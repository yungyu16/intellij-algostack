package com.wenjunhuang.codeepiphany.automation

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.wenjunhuang.codeepiphany.domain.CodeDojo
import com.wenjunhuang.codeepiphany.domain.Language
import com.wenjunhuang.codeepiphany.execution.InitializationResult
import com.wenjunhuang.codeepiphany.execution.initializeChallenge
import com.wenjunhuang.codeepiphany.runtime.ProjectTasks
import com.wenjunhuang.codeepiphany.runtime.onEdt
import kotlinx.coroutines.async
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import java.nio.file.Path

/** 一个 IDE 实例一个端口，项目列表按请求读取；真正初始化任务归属于目标项目。 */
@Service
class ChallengeAutomation : Disposable {
    private var server: ChallengeBridgeServer? = null
    @Synchronized fun start() {
        if (server != null) return
        val candidate = ChallengeBridgeServer(object : ChallengeBridgeBackend {
            override fun projects(): List<String> = ProjectManager.getInstance().openProjects
                .filter { !it.isDisposed && !it.isDefault }.mapNotNull { p ->
                    p.basePath?.let { runCatching { Path.of(it).toRealPath().toString() }.getOrNull() }
                }
            override suspend fun initialize(request: InitializationRequest): InitializationResult {
                val project = onEdt {
                    ProjectManager.getInstance().openProjects.filter { !it.isDisposed && !it.isDefault }
                        .filter { p -> p.basePath?.let { Path.of(it).toRealPath().toString() } == request.project }.singleOrNull()
                } ?: return InitializationResult("conflict", reason = "project_missing_or_ambiguous")
                val deferred = ProjectTasks.getInstance(project).scope.async {
                    initializeChallenge(project, requireNotNull(CodeDojo.fromString(request.dojo)), request.slug,
                        requireNotNull(Language.fromString(request.language)))
                }
                try { return deferred.await() } finally {
                    // 等项目任务结束再释放 HTTP 层串行锁，避免取消后的阻塞写入与下一次请求交叠。
                    if (!deferred.isCompleted) withContext(NonCancellable) { deferred.cancelAndJoin() }
                }
            }
        })
        try {
            candidate.publish(Path.of(System.getProperty("user.home"), ".local/state/yy-leetcode/bridge"))
            server = candidate
            Logger.getInstance(ChallengeAutomation::class.java).info("Challenge initialization bridge ready on loopback port ${candidate.port}")
        } catch (failure: Exception) { candidate.close(); throw failure }
    }
    @Synchronized override fun dispose() { server?.close(); server = null }
    companion object {
        fun getInstance(): ChallengeAutomation = ApplicationManager.getApplication().getService(ChallengeAutomation::class.java)
    }
}
