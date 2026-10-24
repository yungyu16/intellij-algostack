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
import com.wenjunhuang.codeepiphany.toolwindows.sidebar.notes.NotesListPresenter
import com.wenjunhuang.codeepiphany.toolwindows.dojo.SetupRequiredPromptView

class SidebarWindowFactory : ToolWindowFactory, DumbAware {
    /** 项目 root 有 .leetcode 目录才注册整个工具窗口；普通项目零感知。 */
    override fun isApplicable(project: Project): Boolean =
        project.basePath?.let { java.nio.file.Path.of(it, ".leetcode").toFile().isDirectory } == true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 项目 root 有 .leetcode 标记才激活题目面板；非刷题项目只保留 Console。
        PlatformSettings.migrateLegacyStorage(project)
        val manager = toolWindow.contentManager
        val console = LogConsole.getInstance(project).view
        val consolePanel = SimpleToolWindowPanel(false, true).apply {
            setContent(console.component)
            val toolbar = ActionManager.getInstance().createActionToolbar("CodeEpiphany.Console", DefaultActionGroup(*console.createConsoleActions()), true)
            toolbar.targetComponent = console.component; setToolbar(toolbar.component)
        }
        manager.addContent(manager.factory.createContent(consolePanel, "Console", false))
        for ((name, panel) in listOf("Description" to DescriptionPanel(project), "Submissions" to SubmissionsPanel(project), "Solutions" to ArticlesPanel(project))) {
            manager.addContent(manager.factory.createContent(panel, name, false).apply { setDisposer(panel) })
        }
        val notes = NotesListPresenter(project)
        manager.addContent(manager.factory.createContent(notes.getView(), "Notes", false).apply { setDisposer(notes) })
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
