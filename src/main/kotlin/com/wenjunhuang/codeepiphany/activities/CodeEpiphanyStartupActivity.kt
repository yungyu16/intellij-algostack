package com.wenjunhuang.codeepiphany.activities

import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.Logger
import com.wenjunhuang.codeepiphany.automation.ChallengeAutomation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.intellij.openapi.startup.ProjectActivity
import com.wenjunhuang.codeepiphany.configuration.CodeEpiphanyAppSettings
import com.wenjunhuang.codeepiphany.notifications.CodeEpiphanyNotification
import com.wenjunhuang.codeepiphany.runtime.AuthService
import com.wenjunhuang.codeepiphany.runtime.onEdt
import com.wenjunhuang.codeepiphany.utils.IdeUtils

class CodeEpiphanyStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (project.isDefault || project.isDisposed) return
        activateMarkedProject(project.basePath, start = {
            onEdt {
                val state = CodeEpiphanyAppSettings.getInstance().state
                val previous = state.version
                val version = IdeUtils.pluginVersion()
                if (previous != version) {
                    state.version = version
                    if (previous == null) CodeEpiphanyNotification.notifyFirstInstall(project) else CodeEpiphanyNotification.notifyRelease(project)
                }
            }
            try { withContext(Dispatchers.IO) { ChallengeAutomation.getInstance().start() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { Logger.getInstance(CodeEpiphanyStartupActivity::class.java).warn("Challenge bridge failed to start", failure) }
        }, restore = { dojo -> AuthService.getInstance(project).ensureAuthenticated(dojo) })
    }
}
