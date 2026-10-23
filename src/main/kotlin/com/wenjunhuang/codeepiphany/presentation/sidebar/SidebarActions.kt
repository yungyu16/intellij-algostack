package com.wenjunhuang.codeepiphany.presentation.sidebar

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.wenjunhuang.codeepiphany.presentation.browser.WebDocumentView
import com.wenjunhuang.codeepiphany.presentation.files.CodeType
import com.wenjunhuang.codeepiphany.presentation.files.openCode
import javax.swing.JComponent

abstract class ActivateSidebarAction(private val name: String) : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) { event.project?.let { activateSidebar(it, name) } }
    override fun update(event: AnActionEvent) { event.presentation.isEnabled = event.project != null }
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
class ActivateDescriptionAction : ActivateSidebarAction("Description")
class ActivateSolutionsAction : ActivateSidebarAction("Solutions")
class ActivateNotesAction : ActivateSidebarAction("Notes")
class ActivateSubmissionsAction : ActivateSidebarAction("Submissions")
abstract class ZoomAction(private val factor: Double?) : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    override fun actionPerformed(event: AnActionEvent) { event.getData(WebDocumentView.KEY)?.let { if (factor == null) it.actualZoom() else it.zoom(factor) } }
    override fun update(event: AnActionEvent) { event.presentation.isEnabled = event.getData(WebDocumentView.KEY) != null }
}
class ZoomInAction : ZoomAction(1.2)
class ZoomOutAction : ZoomAction(1 / 1.2)
class ActualZoomAction : ZoomAction(null)
class ArticleOrderAction : ComboBoxAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    override fun update(event: AnActionEvent) {
        val panel = event.getData(ArticlesPanel.KEY)
        event.presentation.isEnabledAndVisible = panel != null
        event.presentation.text = panel?.order?.show.orEmpty()
    }
    override fun createPopupActionGroup(button: JComponent, context: DataContext): DefaultActionGroup {
        val panel = ArticlesPanel.KEY.getData(context) ?: return DefaultActionGroup()
        return DefaultActionGroup(panel.orders().map { order -> object : CheckboxAction(order.show), DumbAware {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun isSelected(event: AnActionEvent) = panel.order == order
            override fun setSelected(event: AnActionEvent, selected: Boolean) { if (selected) panel.setOrder(order) }
        } })
    }
}
class OpenSubmissionCodeAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    override fun update(event: AnActionEvent) { event.presentation.isEnabled = event.getData(SubmissionsPanel.KEY)?.selected()?.isNotEmpty() == true }
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        event.getData(SubmissionsPanel.KEY)?.selected()?.firstOrNull()?.let { openCode(project, it, CodeType.Submission) }
    }
}
