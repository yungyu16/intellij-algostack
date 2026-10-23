package com.wenjunhuang.codeepiphany.presentation

import com.wenjunhuang.codeepiphany.presentation.*

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.wenjunhuang.codeepiphany.domain.CodeDojo
import com.wenjunhuang.codeepiphany.runtime.onEdt
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Service(Service.Level.PROJECT)
class LogConsole(project: Project) : Disposable {
    private val consoleDelegate = lazy {
        TextConsoleBuilderFactory.getInstance().createBuilder(project).console.also { Disposer.register(this, it) }
    }
    val view: ConsoleView get() = consoleDelegate.value
    fun show(project: Project) {
        val window = ToolWindowManager.getInstance(project).getToolWindow("LeetCode Epiphany Sidebar") ?: return
        window.show()
        window.contentManager.findContent("Console")?.let { window.contentManager.setSelectedContent(it) }
    }
    suspend fun print(dojo: CodeDojo?, text: String, error: Boolean = false) = onEdt {
        val prefix = "[${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}]${dojo?.let { "[${it.show}]" }.orEmpty()} "
        view.print(prefix, ConsoleViewContentType.SYSTEM_OUTPUT)
        view.print(text + "\n", if (error) ConsoleViewContentType.ERROR_OUTPUT else ConsoleViewContentType.NORMAL_OUTPUT)
    }
    suspend fun link(text: String, clicked: (Project) -> Unit) = onEdt { view.printHyperlink(text, HyperlinkInfo(clicked)) }
    override fun dispose() = Unit
    companion object { @JvmStatic fun getInstance(project: Project): LogConsole = project.getService(LogConsole::class.java) }
}
