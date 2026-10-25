package io.github.yungyu16.algostack.presentation.sidebar

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import io.github.yungyu16.algostack.presentation.browser.WebDocumentView
import javax.swing.JComponent

abstract class ActivateSidebarAction(private val name: String) : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        event.project?.let { activateSidebar(it, name) }
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

class ActivateDescriptionAction : ActivateSidebarAction("Description")
class ActivateSolutionsAction : ActivateSidebarAction("Solutions")
abstract class ZoomAction(private val factor: Double?) : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    override fun actionPerformed(event: AnActionEvent) {
        event.getData(WebDocumentView.KEY)?.let { if (factor == null) it.actualZoom() else it.zoom(factor) }
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.getData(WebDocumentView.KEY) != null
    }
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
        return DefaultActionGroup(panel.orders().map { order ->
            object : CheckboxAction(order.show), DumbAware {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun isSelected(event: AnActionEvent) = panel.order == order
                override fun setSelected(event: AnActionEvent, selected: Boolean) {
                    if (selected) panel.setOrder(order)
                }
            }
        })
    }
}
