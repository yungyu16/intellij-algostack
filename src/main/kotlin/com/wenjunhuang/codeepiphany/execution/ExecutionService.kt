package com.wenjunhuang.codeepiphany.execution

import com.wenjunhuang.codeepiphany.presentation.*

import com.intellij.openapi.components.Service
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.wenjunhuang.codeepiphany.PluginBundle
import com.wenjunhuang.codeepiphany.configuration.ChallengeSettings
import com.wenjunhuang.codeepiphany.domain.*
import com.wenjunhuang.codeepiphany.leetcode.api.model.LeetCodeRunResult
import com.wenjunhuang.codeepiphany.leetcode.api.model.LeetCodeSubmitAnswerResult
import com.wenjunhuang.codeepiphany.presentation.LogConsole
import com.wenjunhuang.codeepiphany.presentation.ConsoleSegment
import com.wenjunhuang.codeepiphany.presentation.browser.openWebPreview
import com.wenjunhuang.codeepiphany.runtime.HttpService
import com.wenjunhuang.codeepiphany.runtime.onEdt
import com.wenjunhuang.codeepiphany.storage.ChallengeRepository
import com.wenjunhuang.codeepiphany.storage.ExecutionRequest
import com.wenjunhuang.codeepiphany.storage.SubmissionMetrics
import com.wenjunhuang.codeepiphany.storage.IdGenerator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ExecutionService(private val project: Project, private val scope: CoroutineScope) {
    private val running = ConcurrentHashMap<VirtualFile, Job>()
    fun isRunning(file: VirtualFile): Boolean = running.containsKey(file)
    fun start(file: VirtualFile, submit: Boolean, cases: List<ChallengeSettings.TestCase>? = null) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val console = onEdt { LogConsole.getInstance(project).also { it.show(project) } }
            try {
                withBackgroundProgress(project, PluginBundle.message(if (submit) "submit.code.start" else "test.code.start", file.name), true) {
                    val item = ChallengeSettings.getInstance(project).findChallengeId(file)
                        ?: throw IllegalStateException(PluginBundle.message("error.no.challenge", file.name))
                    if (!ensureLoggedIn(project, item.dojo)) return@withBackgroundProgress
                    val code = saveAndRead(file)
                    val processed = item.language.extractCodeFromRegion(code)
                    val repository = ChallengeRepository.getInstance(project)
                    val request = repository.query { it.executionRequest(item.challengeId, item.challengeLanguageId) }
                        ?: error("LeetCode challenge data not found")
                    console.info(item.dojo, PluginBundle.message(if (submit) "submit.code.start" else "test.code.start", file.name, item.dojo.show))
                    if (submit) submit(item, request, code, processed, console, repository)
                    else evaluate(item, request, processed, cases ?: item.testCases.takeIf { it.isNotEmpty() }, console)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                console.error(null, PluginBundle.message(if (submit) "error.submit.code" else "test.code.error", failure.message.orEmpty()))
            }
        }
        if (running.putIfAbsent(file, job) == null) {
            job.invokeOnCompletion { running.remove(file, job) }; job.start()
        } else job.cancel()
    }
    private suspend fun evaluate(item: ChallengeSettings.Association, request: ExecutionRequest, code: String,
        cases: List<ChallengeSettings.TestCase>?, console: LogConsole) {
        val input = cases?.joinToString("\n") { it.input } ?: request.defaultCases
        HttpService.getInstance().api(item.dojo).runAnswer(request.questionId, request.slug, input,
            request.language.toLeetCode(request.version), code).collect { result ->
            if (result is LeetCodeRunResult.Success) {
                val state = SubmissionResult.fromApi(result.statusMsg, result.correctAnswer)
                val message = when (state) {
                    // 只有明确返回 correct_answer=true 才算通过，与上游判定口径一致。
                    SubmissionResult.Success -> if (result.correctAnswer == true) PluginBundle.message("submission.passed")
                        else result.statusMsg
                    SubmissionResult.CompilationError -> result.fullCompileError ?: result.compileError ?: result.statusMsg
                    SubmissionResult.RuntimeError -> result.fullRuntimeError ?: result.runtimeError ?: result.statusMsg
                    SubmissionResult.Failure -> result.codeAnswer.zip(result.expectedCodeAnswer).mapIndexed { index, pair ->
                        "${PluginBundle.message("testcases.title", index + 1)}\n=========================\n" +
                            "${PluginBundle.message("leetcode.submissionResult.wrongAnswer.input.text")}:\n${cases?.getOrNull(index)?.input ?: input.lines().getOrNull(index).orEmpty()}\n" +
                            "-------------------------\n" +
                            "${PluginBundle.message("leetcode.submissionResult.wrongAnswer.output.text")}:\n${pair.first}\n" +
                            "-------------------------\n" +
                            "${PluginBundle.message("leetcode.submissionResult.wrongAnswer.expected.text")}:\n${pair.second}"
                    }.joinToString("\n")
                    else -> result.statusMsg
                }
                console.print(item.dojo,
                    if (state == SubmissionResult.Success) ConsoleViewContentType.LOG_INFO_OUTPUT else ConsoleViewContentType.LOG_ERROR_OUTPUT,
                    ConsoleSegment.Text("${state.show}\n$message"))
            }
        }
    }
    private suspend fun submit(item: ChallengeSettings.Association, request: ExecutionRequest, local: String, code: String,
        console: LogConsole, repository: ChallengeRepository) {
        val id = IdGenerator.nextId()
        try {
            repository.transaction { it.createSubmission(item.challengeLanguageId, local, code, id) }
            HttpService.getInstance().api(item.dojo).submitAnswer(request.questionId, request.slug,
                request.language.toLeetCode(request.version), code).collect { response ->
                val state = if (response is LeetCodeSubmitAnswerResult.Success) SubmissionResult.fromApi(response.statusMsg) else SubmissionResult.Processing
                val remoteId = when (response) {
                    is LeetCodeSubmitAnswerResult.Started -> response.leetCodeSubmissionId
                    is LeetCodeSubmitAnswerResult.Pending -> response.leetCodeSubmissionId
                    is LeetCodeSubmitAnswerResult.Success -> response.submissionId
                }
                val message = if (response is LeetCodeSubmitAnswerResult.Success) submissionMessage(state, response) else ""
                val metrics = if (response is LeetCodeSubmitAnswerResult.Success) SubmissionMetrics(
                    memory = response.memory, totalCorrect = response.totalCorrect, totalTestcases = response.totalTestcases,
                    statusMemory = response.statusMemory, runtimePercentile = response.runtimePercentile,
                    memoryPercentile = response.memoryPercentile, lastTestcase = response.lastTestcase,
                    inputFormatted = response.inputFormatted, expectedOutput = response.expectedOutput,
                    codeOutput = response.codeOutput, stdOutput = response.stdOutput, statusRuntime = response.statusRuntime
                ) else null
                repository.transaction { it.updateSubmission(id, state, message, remoteId, metrics) }
                if (response is LeetCodeSubmitAnswerResult.Success) {
                    val details = ConsoleSegment.Link(PluginBundle.message("submissionResult.viewDetails")) { owner ->
                        openWebPreview(owner, item.dojo, "https://${item.dojo.domain}/problems/${request.slug}/submissions/$remoteId/", remoteId)
                    }
                    if (state == SubmissionResult.Success) {
                        console.print(item.dojo, ConsoleViewContentType.LOG_INFO_OUTPUT,
                            ConsoleSegment.Text("${PluginBundle.message("submission.passed")}\n$message"),
                            ConsoleSegment.Text("\n"), details)
                    } else {
                        console.print(item.dojo, ConsoleViewContentType.LOG_ERROR_OUTPUT,
                            details, ConsoleSegment.Text("\n"),
                            ConsoleSegment.Text("${state.show}\n$message"))
                    }
                }
            }
        } catch (failure: Throwable) {
            if (!project.isDisposed) withContext(NonCancellable) {
                runCatching { repository.transaction { it.failSubmission(id, failure.message ?: "Tracking cancelled") } }
            }
            throw failure
        }
    }
    private fun submissionMessage(state: SubmissionResult, response: LeetCodeSubmitAnswerResult.Success): String = when (state) {
        SubmissionResult.Success -> formatSubmissionTable(
            listOf("Metric", "Value"),
            listOf("Runtime", "${response.statusRuntime} (Beats ${"%.2f".format(response.runtimePercentile ?: 0f)}%)"),
            listOf("Memory", "${response.statusMemory} (Beats ${"%.2f".format(response.memoryPercentile ?: 0f)}%)")
        )
        SubmissionResult.CompilationError -> response.fullCompileError ?: response.compileError ?: response.statusMsg
        SubmissionResult.RuntimeError -> response.fullRuntimeError ?: response.runtimeError ?: response.statusMsg
        SubmissionResult.Failure -> "\n-------------------------\n" +
            "${PluginBundle.message("leetcode.submissionResult.wrongAnswer.input.text")}:\n${response.input.orEmpty()}\n" +
            "-------------------------\n" +
            "${PluginBundle.message("leetcode.submissionResult.wrongAnswer.output.text")}:\n${response.codeOutput.orEmpty()}\n" +
            "-------------------------\n" +
            "${PluginBundle.message("leetcode.submissionResult.wrongAnswer.expected.text")}:\n${response.expectedOutput.orEmpty()}\n" +
            "-------------------------\n"
        else -> response.statusMsg
    }
    companion object { @JvmStatic fun getInstance(project: Project): ExecutionService = project.getService(ExecutionService::class.java) }
}

/**
 * 与上游 Tabulator 一致的带边框表格：首行为表头，列宽取各列最大值，单元格右对齐。
 * 表头与数据行之间、末行之后各有一条分隔线。
 */
internal fun formatSubmissionTable(header: List<String>, vararg rows: List<String>): String {
    val all = listOf(header) + rows
    val widths = header.indices.map { column -> all.maxOf { it.getOrElse(column) { "" }.length } }
    fun separator() = widths.joinToString("+", "+", "+") { "-".repeat(it) }
    fun line(cells: List<String>) = cells.mapIndexed { index, cell ->
        cell.padStart(widths[index])
    }.joinToString("|", "|", "|")
    return (listOf(separator(), line(header), separator()) + rows.map { line(it) } + separator()).joinToString("\n")
}
