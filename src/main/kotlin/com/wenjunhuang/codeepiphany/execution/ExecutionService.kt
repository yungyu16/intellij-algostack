package com.wenjunhuang.codeepiphany.execution

import com.wenjunhuang.codeepiphany.presentation.*

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.wenjunhuang.codeepiphany.PluginBundle
import com.wenjunhuang.codeepiphany.configuration.ChallengeSettings
import com.wenjunhuang.codeepiphany.domain.*
import com.wenjunhuang.codeepiphany.leetcode.api.model.LeetCodeRunResult
import com.wenjunhuang.codeepiphany.leetcode.api.model.LeetCodeSubmitAnswerResult
import com.wenjunhuang.codeepiphany.presentation.LogConsole
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
                    console.print(item.dojo, PluginBundle.message(if (submit) "submit.code.start" else "test.code.start", file.name, item.dojo.show))
                    if (submit) submit(item, request, code, processed, console, repository)
                    else evaluate(item, request, processed, cases ?: item.testCases.takeIf { it.isNotEmpty() }, console)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { console.print(null, failure.message.orEmpty(), true) }
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
                    SubmissionResult.Success -> PluginBundle.message("submission.passed")
                    SubmissionResult.CompilationError -> result.fullCompileError ?: result.compileError ?: result.statusMsg
                    SubmissionResult.RuntimeError -> result.fullRuntimeError ?: result.runtimeError ?: result.statusMsg
                    SubmissionResult.Failure -> result.codeAnswer.zip(result.expectedCodeAnswer).mapIndexed { index, pair ->
                        "${PluginBundle.message("testcases.title", index + 1)}\n=========================\n" +
                            "${PluginBundle.message("leetcode.submissionResult.wrongAnswer.input.text")}:\n${cases?.getOrNull(index)?.input ?: input.lines().getOrNull(index).orEmpty()}\n" +
                            "${PluginBundle.message("leetcode.submissionResult.wrongAnswer.output.text")}:\n${pair.first}\n" +
                            "${PluginBundle.message("leetcode.submissionResult.wrongAnswer.expected.text")}:\n${pair.second}"
                    }.joinToString("\n")
                    else -> result.statusMsg
                }
                console.print(item.dojo, "${state.show}\n$message", state != SubmissionResult.Success)
            }
        }
    }
    private suspend fun submit(item: ChallengeSettings.Association, request: ExecutionRequest, local: String, code: String,
        console: LogConsole, repository: ChallengeRepository) {
        val id = IdGenerator.nextId()
        try {
            repository.transaction { it.createSubmission(item.challengeLanguageId, item.solutionId, local, code, id) }
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
                    console.print(item.dojo, "${state.show}\n$message", state != SubmissionResult.Success)
                    console.link(PluginBundle.message("submissionResult.viewDetails")) { owner ->
                        openWebPreview(owner, item.dojo, "https://${item.dojo.domain}/problems/${request.slug}/submissions/$remoteId/", remoteId)
                    }
                    console.print(null, "")
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
        SubmissionResult.Success -> "Runtime: ${response.statusRuntime} (Beats ${"%.2f".format(response.runtimePercentile ?: 0f)}%)\n" +
            "Memory: ${response.statusMemory} (Beats ${"%.2f".format(response.memoryPercentile ?: 0f)}%)"
        SubmissionResult.CompilationError -> response.fullCompileError ?: response.compileError ?: response.statusMsg
        SubmissionResult.RuntimeError -> response.fullRuntimeError ?: response.runtimeError ?: response.statusMsg
        SubmissionResult.Failure -> "${PluginBundle.message("leetcode.submissionResult.wrongAnswer.input.text")}:\n${response.input.orEmpty()}\n" +
            "${PluginBundle.message("leetcode.submissionResult.wrongAnswer.output.text")}:\n${response.codeOutput.orEmpty()}\n" +
            "${PluginBundle.message("leetcode.submissionResult.wrongAnswer.expected.text")}:\n${response.expectedOutput.orEmpty()}"
        else -> response.statusMsg
    }
    companion object { @JvmStatic fun getInstance(project: Project): ExecutionService = project.getService(ExecutionService::class.java) }
}
