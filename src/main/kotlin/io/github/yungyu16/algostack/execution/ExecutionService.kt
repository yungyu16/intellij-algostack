package io.github.yungyu16.algostack.execution

import io.github.yungyu16.algostack.presentation.*

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import io.github.yungyu16.algostack.PluginBundle
import io.github.yungyu16.algostack.utils.ChallengeFiles
import io.github.yungyu16.algostack.domain.*
import io.github.yungyu16.algostack.leetcode.api.model.LeetCodeChallengeData
import io.github.yungyu16.algostack.leetcode.api.model.LeetCodeRunResult
import io.github.yungyu16.algostack.leetcode.api.model.LeetCodeSubmitAnswerResult
import io.github.yungyu16.algostack.presentation.LogConsole
import io.github.yungyu16.algostack.presentation.sidebar.SidebarPolicy
import io.github.yungyu16.algostack.presentation.ConsoleSegment
import io.github.yungyu16.algostack.presentation.browser.openWebPreview
import io.github.yungyu16.algostack.runtime.HttpService
import io.github.yungyu16.algostack.runtime.onEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.util.concurrent.ConcurrentHashMap

internal data class ExecutionRequest(val questionId: String, val slug: String, val defaultCases: List<String>,
    val language: Language, val version: LanguageVersion) {
    val testInput: String get() = defaultCases.joinToString("\n")
}

/** 测试输入仅取本次平台响应，不使用本地快照或自定义输入。 */
internal fun executionRequest(data: LeetCodeChallengeData, identity: ChallengeIdentity): ExecutionRequest {
    require(data.titleSlug == identity.slug) { "Question identity mismatch" }
    return ExecutionRequest(data.questionId, identity.slug, data.exampleTestcaseList, identity.language, identity.version)
}

internal fun executionStartMessage(submit: Boolean, fileName: String, dojo: CodeDojo): String =
    PluginBundle.message(if (submit) "submit.code.start" else "test.code.start", fileName, dojo.show)

@Service(Service.Level.PROJECT)
class ExecutionService(private val project: Project, private val scope: CoroutineScope) {
    private val running = ConcurrentHashMap<VirtualFile, Job>()
    fun isRunning(file: VirtualFile): Boolean = running.containsKey(file)
    fun start(file: VirtualFile, submit: Boolean) {
        if (!SidebarPolicy.enabled(project.basePath)) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val console = onEdt { LogConsole.getInstance(project).also { it.show(project) } }
            try {
                val item = readAction { ChallengeFiles.resolve(file) }
                    ?: throw IllegalStateException(PluginBundle.message("error.no.challenge", file.name))
                val startMessage = executionStartMessage(submit, file.name, item.dojo)
                withBackgroundProgress(project, startMessage, true) {
                    if (!ensureLoggedIn(project, item.dojo)) return@withBackgroundProgress
                    val code = saveAndRead(file)
                    check(ChallengeFiles.resolve(file.path, code) == item) {
                        "Question identity changed while preparing execution; retry explicitly"
                    }
                    val processed = item.language.extractCodeFromRegion(code)
                    require(processed.isNotBlank()) { "Submission region is empty" }
                    val data = HttpService.getInstance().api(item.dojo).getQuestionData(item.slug)
                    val request = executionRequest(data, item)
                    console.info(item.dojo, startMessage)
                    if (submit) submit(item, request, processed, console)
                    else evaluate(item, request, processed, console)
                }
            } catch (cancelled: CancellationException) {
                if (!project.isDisposed) withContext(NonCancellable) {
                    console.info(null, if (submit) "提交跟踪已取消；远端可能已受理，请到平台核对，勿盲目重试。" else "测试已取消。")
                }
                throw cancelled
            }
            catch (failure: Exception) {
                console.error(null, PluginBundle.message(if (submit) "error.submit.code" else "test.code.error", failure.message.orEmpty()))
            }
        }
        if (running.putIfAbsent(file, job) == null) {
            job.invokeOnCompletion { running.remove(file, job) }; job.start()
        } else job.cancel()
    }
    private suspend fun evaluate(item: ChallengeIdentity, request: ExecutionRequest, code: String, console: LogConsole) {
        val input = request.testInput
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
                            "${PluginBundle.message("leetcode.submissionResult.wrongAnswer.input.text")}:\n${request.defaultCases.getOrNull(index).orEmpty()}\n" +
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
    private suspend fun submit(item: ChallengeIdentity, request: ExecutionRequest, code: String,
        console: LogConsole) {
        HttpService.getInstance().api(item.dojo).submitAnswer(request.questionId, request.slug,
            request.language.toLeetCode(request.version), code).collect { response ->
            val state = if (response is LeetCodeSubmitAnswerResult.Success) SubmissionResult.fromApi(response.statusMsg) else SubmissionResult.Processing
            val remoteId = when (response) {
                is LeetCodeSubmitAnswerResult.Accepted -> response.submissionId
                is LeetCodeSubmitAnswerResult.Started -> response.leetCodeSubmissionId
                is LeetCodeSubmitAnswerResult.Pending -> response.leetCodeSubmissionId
                is LeetCodeSubmitAnswerResult.Success -> response.submissionId
            }
            if (response is LeetCodeSubmitAnswerResult.Accepted) {
                console.submissionAccepted(item.dojo, request.slug, remoteId)
            }
            val message = if (response is LeetCodeSubmitAnswerResult.Success) submissionMessage(state, response) else ""
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
