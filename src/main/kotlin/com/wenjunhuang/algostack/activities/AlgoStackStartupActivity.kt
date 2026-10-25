package com.wenjunhuang.algostack.activities

import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.Logger
import com.wenjunhuang.algostack.automation.ChallengeAutomation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.intellij.openapi.startup.ProjectActivity
import com.wenjunhuang.algostack.configuration.AlgoStackAppSettings
import com.wenjunhuang.algostack.notifications.AlgoStackNotification
import com.wenjunhuang.algostack.runtime.AuthService
import com.wenjunhuang.algostack.runtime.onEdt
import com.wenjunhuang.algostack.utils.IdeUtils

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
                    if (previous == null) AlgoStackNotification.notifyFirstInstall(project) else AlgoStackNotification.notifyRelease(project)
                }
            }
            try { withContext(Dispatchers.IO) { ChallengeAutomation.getInstance().start() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { Logger.getInstance(AlgoStackStartupActivity::class.java).warn("Challenge bridge failed to start", failure) }
        }, restore = { dojo -> AuthService.getInstance(project).ensureAuthenticated(dojo) })
    }
}
