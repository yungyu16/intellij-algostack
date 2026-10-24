package com.wenjunhuang.codeepiphany.notifications

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.wenjunhuang.codeepiphany.PluginBundle
import com.wenjunhuang.codeepiphany.utils.IdeUtils
import icons.CodeEpiphanyIcons

object CodeEpiphanyNotification {
    const val GITHUB_URL = "https://github.com/yungyu16/CodeEpiphany"
    const val CHANGELOG_URL = "$GITHUB_URL/blob/main/CHANGELOG.md"
    fun notifyFirstInstall(project: Project) = show(project, PluginBundle.message("notification.firstlyDownloaded.title"), PluginBundle.message("notification.firstlyDownloaded.description"))
    fun notifyRelease(project: Project) {
        val notes = javaClass.getResourceAsStream("/META-INF/release-notes.html")?.bufferedReader()?.use { it.readText() }
            ?: PluginBundle.message("whatsNew")
        show(project, PluginBundle.message("notification.releaseNote.title", IdeUtils.pluginVersion()), notes)
    }
    private fun show(project: Project, title: String, body: String) {
        NotificationGroupManager.getInstance().getNotificationGroup("Code Epiphany")
            .createNotification(title, body, NotificationType.INFORMATION).apply {
                icon = CodeEpiphanyIcons.PLUGIN
                addAction(NotificationAction.createSimple("GitHub") { BrowserUtil.browse(GITHUB_URL) })
                addAction(NotificationAction.createSimple(PluginBundle.message("notification.changeLog")) { BrowserUtil.browse(CHANGELOG_URL) })
                notify(project)
            }
    }
}
