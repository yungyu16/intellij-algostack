package io.github.yungyu16.algostack.activities

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import io.github.yungyu16.algostack.automation.ChallengeAutomation
import io.github.yungyu16.algostack.configuration.AlgoStackAppSettings
import io.github.yungyu16.algostack.notifications.AlgoStackNotification
import io.github.yungyu16.algostack.runtime.AuthService
import io.github.yungyu16.algostack.runtime.onEdt
import io.github.yungyu16.algostack.utils.IdeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AlgoStackStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (project.isDefault || project.isDisposed) return
        activateMarkedProject(project.basePath, start = {
            onEdt {
                val state = AlgoStackAppSettings.getInstance().state
                val previous = state.version
                val version = IdeUtils.pluginVersion()
                if (previous != version) {
                    state.version = version
                    if (previous == null) AlgoStackNotification.notifyFirstInstall(project) else AlgoStackNotification.notifyRelease(
                        project
                    )
                }
            }
            try {
                withContext(Dispatchers.IO) { ChallengeAutomation.getInstance().start() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Logger.getInstance(AlgoStackStartupActivity::class.java)
                    .warn("Challenge bridge failed to start", failure)
            }
        }, restore = { dojo -> AuthService.getInstance(project).ensureAuthenticated(dojo) })
    }
}
