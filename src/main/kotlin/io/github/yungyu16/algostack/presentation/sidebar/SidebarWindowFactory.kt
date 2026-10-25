package io.github.yungyu16.algostack.presentation.sidebar

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import io.github.yungyu16.algostack.domain.CodeDojo
import io.github.yungyu16.algostack.presentation.LogConsole
import io.github.yungyu16.algostack.presentation.dojo.ChallengesView
import com.intellij.openapi.util.Disposer
import io.github.yungyu16.algostack.runtime.ProjectTasks
import io.github.yungyu16.algostack.runtime.onEdt
import kotlinx.coroutines.launch

class SidebarWindowFactory : ToolWindowFactory, DumbAware {
    override fun isApplicable(project: Project): Boolean = SidebarPolicy.enabled(project.basePath)

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val manager = toolWindow.contentManager
        val console = LogConsole.getInstance(project).view
        val consolePanel = SimpleToolWindowPanel(false, true).apply {
            setContent(console.component)
            val toolbar = ActionManager.getInstance().createActionToolbar("AlgoStack.Console", DefaultActionGroup(*console.createConsoleActions()), true)
            toolbar.targetComponent = console.component; setToolbar(toolbar.component)
        }
        manager.addContent(manager.factory.createContent(consolePanel, SidebarPolicy.CONSOLE, false))
        val contextual = listOf("Description" to DescriptionPanel(project), "Solutions" to ArticlesPanel(project)).map { (name, panel) ->
            manager.factory.createContent(panel, name, false).apply {
                setDisposer(panel)
                Disposer.register(toolWindow.disposable, this)
            }
        }
        val tabs = ProjectTasks.getInstance(project).scope.launch {
            SidebarContext.getInstance(project).challenge.collect { challenge -> onEdt {
                for (content in contextual) {
                    val visible = SidebarPolicy.visibleTab(content.displayName, challenge)
                    if (visible && content !in manager.contents) manager.addContent(content)
                    else if (!visible && content in manager.contents) manager.removeContent(content, false)
                }
            } }
        }
        Disposer.register(toolWindow.disposable, com.intellij.openapi.Disposable { tabs.cancel() })
        addChallengeTabs(project, manager)
    }

    /** Problems 始终存在，不依赖项目语言配置。 */
    private fun addChallengeTabs(project: Project, manager: com.intellij.ui.content.ContentManager) {
        manager.contents.filter { it.displayName == SidebarPolicy.PROBLEMS }.forEach { manager.removeContent(it, true) }
        val dojo = CodeDojo.LeetCodeCN
        val view = ChallengesView(project, dojo)
        manager.addContent(manager.factory.createContent(view, SidebarPolicy.PROBLEMS, false).apply { setDisposer(view) })
    }
}
fun activateSidebar(project: Project, name: String) {
    val window = ToolWindowManager.getInstance(project).getToolWindow("题栈") ?: return
    window.activate(null)
    window.contentManager.findContent(name)?.let { window.contentManager.setSelectedContent(it) }
}
