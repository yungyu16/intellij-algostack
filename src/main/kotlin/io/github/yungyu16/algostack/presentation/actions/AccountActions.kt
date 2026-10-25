package io.github.yungyu16.algostack.presentation.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import io.github.yungyu16.algostack.PluginBundle
import icons.AlgoStackIcons

class LoginAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    override fun actionPerformed(event: AnActionEvent) { site(event)?.login() }
    override fun update(event: AnActionEvent) {
        val owner = site(event)
        event.presentation.isVisible = owner != null && !owner.loggedIn
        event.presentation.isEnabled = owner != null && !owner.loggingIn
        event.presentation.icon = if (owner?.loggingIn == true) com.intellij.ui.AnimatedIcon.Default.INSTANCE else AlgoStackIcons.LOGIN
    }
}
class LogoutAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    override fun actionPerformed(event: AnActionEvent) { site(event)?.logout() }
    override fun update(event: AnActionEvent) { event.presentation.isEnabledAndVisible = site(event)?.loggedIn == true }
}
class UserInfoAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    override fun actionPerformed(event: AnActionEvent) { site(event)?.showProfile() }
    override fun update(event: AnActionEvent) {
        val owner = site(event)
        event.presentation.isEnabledAndVisible = owner?.loggedIn == true
        event.presentation.text = owner?.userInfo?.username ?: PluginBundle.message("user.unknown")
    }
}
class AccountGroup : DefaultActionGroup(), DumbAware {
    init { isPopup = true }
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    override fun update(event: AnActionEvent) {
        val owner = site(event); event.presentation.isEnabledAndVisible = owner?.loggedIn == true
        event.presentation.text = owner?.userInfo?.username ?: PluginBundle.message("user.unknown")
        event.presentation.icon = owner?.avatar
    }
}
