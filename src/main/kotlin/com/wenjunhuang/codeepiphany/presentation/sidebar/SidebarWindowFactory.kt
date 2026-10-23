package com.wenjunhuang.codeepiphany.presentation.sidebar

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.wenjunhuang.codeepiphany.presentation.LogConsole
import com.wenjunhuang.codeepiphany.toolwindows.sidebar.notes.NotesListPresenter

class SidebarWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
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
    }
}
fun activateSidebar(project: Project, name: String) {
    val window = ToolWindowManager.getInstance(project).getToolWindow("LeetCode Epiphany Sidebar") ?: return
    window.activate(null)
    window.contentManager.findContent(name)?.let { window.contentManager.setSelectedContent(it) }
}
