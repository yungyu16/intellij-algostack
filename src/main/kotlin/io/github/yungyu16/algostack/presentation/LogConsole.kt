package io.github.yungyu16.algostack.presentation

import io.github.yungyu16.algostack.presentation.*

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import io.github.yungyu16.algostack.PluginBundle
import io.github.yungyu16.algostack.domain.CodeDojo
import io.github.yungyu16.algostack.runtime.onEdt
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 控制台输出的一段内容：普通文本或超链接。 */
sealed interface ConsoleSegment {
    data class Text(val text: String) : ConsoleSegment
    data class Link(val text: String, val clicked: (Project) -> Unit) : ConsoleSegment
}

@Service(Service.Level.PROJECT)
class LogConsole(project: Project) : Disposable {
    private val consoleDelegate = lazy {
        TextConsoleBuilderFactory.getInstance().createBuilder(project).console.also {
            Disposer.register(this, it)
            it.print(PluginBundle.message("console.welcome") + "\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        }
    }
    val view: ConsoleView get() = consoleDelegate.value
    fun show(project: Project) {
        val window = ToolWindowManager.getInstance(project).getToolWindow("题栈") ?: return
        window.show()
        window.contentManager.findContent("Console")?.let { window.contentManager.setSelectedContent(it) }
    }
    /**
     * 输出控制台消息：前缀独占一行并随内容类型着色，消息段落紧随其后，末尾补一个换行。
     * 前缀包含状态图标、站点与完整时间戳，便于跨天定位。
     */
    suspend fun print(dojo: CodeDojo?, type: ConsoleViewContentType, vararg segments: ConsoleSegment) = onEdt {
        view.requestScrollingToEnd()
        view.print(prefix(dojo, type), type)
        segments.forEach { segment ->
            when (segment) {
                is ConsoleSegment.Text -> view.print(segment.text, type)
                is ConsoleSegment.Link -> view.printHyperlink(segment.text, HyperlinkInfo(segment.clicked))
            }
        }
        view.print("\n", type)
    }
    suspend fun info(dojo: CodeDojo?, text: String) =
        print(dojo, ConsoleViewContentType.LOG_INFO_OUTPUT, ConsoleSegment.Text(text))
    suspend fun error(dojo: CodeDojo?, text: String) =
        print(dojo, ConsoleViewContentType.LOG_ERROR_OUTPUT, ConsoleSegment.Text(text))
    private fun prefix(dojo: CodeDojo?, type: ConsoleViewContentType): String {
        val icon = when (type) {
            ConsoleViewContentType.LOG_ERROR_OUTPUT -> "❌"
            ConsoleViewContentType.LOG_WARNING_OUTPUT -> "⚠️"
            else -> "ℹ️"
        }
        val site = dojo?.let { "[${it.show}] " }.orEmpty()
        return "$icon $site${currentDateTime()}\n"
    }
    private fun currentDateTime(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    override fun dispose() = Unit
    companion object { @JvmStatic fun getInstance(project: Project): LogConsole = project.getService(LogConsole::class.java) }
}
