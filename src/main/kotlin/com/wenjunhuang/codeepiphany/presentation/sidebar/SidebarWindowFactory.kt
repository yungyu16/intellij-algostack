package com.wenjunhuang.codeepiphany.presentation.sidebar

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.wenjunhuang.codeepiphany.PluginBundle
import com.wenjunhuang.codeepiphany.configuration.CodeEpiphanySettings
import com.wenjunhuang.codeepiphany.configuration.PlatformSettings
import com.wenjunhuang.codeepiphany.domain.CodeDojo
import com.wenjunhuang.codeepiphany.presentation.LogConsole
import com.wenjunhuang.codeepiphany.presentation.dojo.ChallengesView
import com.wenjunhuang.codeepiphany.presentation.show
import com.intellij.openapi.util.Disposer
import com.wenjunhuang.codeepiphany.runtime.ProjectTasks
import com.wenjunhuang.codeepiphany.runtime.onEdt
import kotlinx.coroutines.launch
import com.wenjunhuang.codeepiphany.toolwindows.dojo.SetupRequiredPromptView

class SidebarWindowFactory : ToolWindowFactory, DumbAware {
    override fun isApplicable(project: Project): Boolean = SidebarPolicy.enabled(project.basePath)

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val manager = toolWindow.contentManager
        val console = LogConsole.getInstance(project).view
        val consolePanel = SimpleToolWindowPanel(false, true).apply {
            setContent(console.component)
            val toolbar = ActionManager.getInstance().createActionToolbar("CodeEpiphany.Console", DefaultActionGroup(*console.createConsoleActions()), true)
            toolbar.targetComponent = console.component; setToolbar(toolbar.component)
        }
        manager.addContent(manager.factory.createContent(consolePanel, "Console", false))
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
        project.messageBus.connect(project).subscribe(CodeEpiphanySettings.TOPIC,
            CodeEpiphanySettings.CodeEpiphanySettingsChangedListener { addChallengeTabs(project, manager) })
    }

    /** 题库按站点各占一个标签页；未配置语言时给出配置提示。 */
    private fun addChallengeTabs(project: Project, manager: com.intellij.ui.content.ContentManager) {
        manager.contents.filter { it.component is ChallengesView || it.displayName == PluginBundle.message("ui.setupView.title") }
            .forEach { manager.removeContent(it, true) }
        var added = false
        for (dojo in CodeDojo.entries) {
            if (PlatformSettings.getInstance(project, dojo).getSelectedLanguages().isEmpty()) continue
            val view = ChallengesView(project, dojo)
            manager.addContent(manager.factory.createContent(view, dojo.show, false).apply { setDisposer(view) })
            added = true
        }
        if (!added) {
            val prompt = SetupRequiredPromptView(project)
            manager.addContent(manager.factory.createContent(prompt.component, PluginBundle.message("ui.setupView.title"), false))
        }
    }
}
fun activateSidebar(project: Project, name: String) {
    val window = ToolWindowManager.getInstance(project).getToolWindow("leetcode-editor") ?: return
    window.activate(null)
    window.contentManager.findContent(name)?.let { window.contentManager.setSelectedContent(it) }
}
