package io.github.yungyu16.algostack.notifications

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import icons.AlgoStackIcons
import io.github.yungyu16.algostack.PluginBundle
import io.github.yungyu16.algostack.utils.IdeUtils

object AlgoStackNotification {
    const val GITHUB_URL = "https://github.com/yungyu16/AlgoStack"
    const val CHANGELOG_URL = "$GITHUB_URL/blob/main/CHANGELOG.md"
    fun notifyFirstInstall(project: Project) = show(
        project,
        PluginBundle.message("notification.firstlyDownloaded.title"),
        PluginBundle.message("notification.firstlyDownloaded.description")
    )

    fun notifyRelease(project: Project) {
        val notes =
            javaClass.getResourceAsStream("/META-INF/release-notes.html")?.bufferedReader()?.use { it.readText() }
                ?: PluginBundle.message("whatsNew")
        show(project, PluginBundle.message("notification.releaseNote.title", IdeUtils.pluginVersion()), notes)
    }

    private fun show(project: Project, title: String, body: String) {
        NotificationGroupManager.getInstance().getNotificationGroup("AlgoStack")
            .createNotification(title, body, NotificationType.INFORMATION).apply {
                icon = AlgoStackIcons.PLUGIN
                addAction(NotificationAction.createSimple("GitHub") { BrowserUtil.browse(GITHUB_URL) })
                addAction(NotificationAction.createSimple(PluginBundle.message("notification.changeLog")) {
                    BrowserUtil.browse(
                        CHANGELOG_URL
                    )
                })
                notify(project)
            }
    }
}
