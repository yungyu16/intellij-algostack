package io.github.yungyu16.algostack.automation

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import io.github.yungyu16.algostack.domain.CodeDojo
import io.github.yungyu16.algostack.domain.Language
import io.github.yungyu16.algostack.execution.InitializationResult
import io.github.yungyu16.algostack.execution.initializeChallenge
import io.github.yungyu16.algostack.presentation.sidebar.SidebarPolicy
import io.github.yungyu16.algostack.runtime.ProjectTasks
import io.github.yungyu16.algostack.runtime.onEdt
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import java.nio.file.Path

/** 一个 IDE 实例一个端口，项目列表按请求读取；真正初始化任务归属于目标项目。 */
@Service
class ChallengeAutomation : Disposable {
    private var server: ChallengeBridgeServer? = null
    @Synchronized
    fun start() {
        if (server != null) return
        val candidate = ChallengeBridgeServer(object : ChallengeBridgeBackend {
            override fun projects(): List<String> = ProjectManager.getInstance().openProjects
                .filter { !it.isDisposed && !it.isDefault && SidebarPolicy.enabled(it.basePath) }.mapNotNull { p ->
                    p.basePath?.let { runCatching { Path.of(it).toRealPath().toString() }.getOrNull() }
                }

            override suspend fun initialize(request: InitializationRequest, onStage: (String) -> Unit): InitializationResult {
                onStage("project_resolution")
                val project = onEdt {
                    ProjectManager.getInstance().openProjects.filter {
                        !it.isDisposed && !it.isDefault && SidebarPolicy.enabled(
                            it.basePath
                        )
                    }
                        .filter { p -> p.basePath?.let { Path.of(it).toRealPath().toString() } == request.project }
                        .singleOrNull()
                } ?: return InitializationResult("conflict", reason = "project_missing_or_ambiguous")
                val deferred = ProjectTasks.getInstance(project).scope.async {
                    initializeChallenge(
                        project, requireNotNull(CodeDojo.fromString(request.dojo)), request.slug,
                        requireNotNull(Language.fromString(request.language)), onStage
                    )
                }
                try {
                    return deferred.await()
                } finally {
                    // 等项目任务结束再释放 HTTP 层串行锁，避免取消后的阻塞写入与下一次请求交叠。
                    if (!deferred.isCompleted) withContext(NonCancellable) { deferred.cancelAndJoin() }
                }
            }
        })
        try {
            candidate.publish(Path.of(System.getProperty("user.home"), ".local/state/algostack/bridge"))
            server = candidate
            Logger.getInstance(ChallengeAutomation::class.java)
                .info("Challenge initialization bridge ready on loopback port ${candidate.port}")
        } catch (failure: Exception) {
            candidate.close(); throw failure
        }
    }

    @Synchronized
    override fun dispose() {
        server?.close(); server = null
    }

    companion object {
        fun getInstance(): ChallengeAutomation =
            ApplicationManager.getApplication().getService(ChallengeAutomation::class.java)
    }
}
