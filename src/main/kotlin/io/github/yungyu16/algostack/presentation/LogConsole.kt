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
import io.github.yungyu16.algostack.presentation.browser.openWebPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 控制台输出的一段内容：普通文本或超链接。 */
sealed interface ConsoleSegment {
    data class Text(val text: String) : ConsoleSegment
    data class Link(val text: String, val clicked: (Project) -> Unit) : ConsoleSegment
}

@Service(Service.Level.PROJECT)
class LogConsole(private val project: Project) : Disposable {
    @Volatile private var disposed = false
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
        if (disposed || project.isDisposed) return@onEdt
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
    suspend fun submissionAccepted(dojo: CodeDojo, slug: String, submissionId: String) {
        // ID 已由平台返回；仅保护这段本地交付，避免取消打断 EDT 排队。释放检查在 print 的 EDT 内执行。
        withContext(NonCancellable) {
            print(dojo, ConsoleViewContentType.LOG_INFO_OUTPUT,
                ConsoleSegment.Text("平台已受理提交（$submissionId）；取消跟踪不会撤回提交。\n"),
                ConsoleSegment.Link(PluginBundle.message("submissionResult.viewDetails")) { owner ->
                    openWebPreview(owner, dojo, "https://${dojo.domain}/problems/$slug/submissions/$submissionId/", submissionId)
                })
        }
        // 不让短暂的取消保护延伸到后续轮询。
        currentCoroutineContext().ensureActive()
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
    override fun dispose() { disposed = true }
    companion object { @JvmStatic fun getInstance(project: Project): LogConsole = project.getService(LogConsole::class.java) }
}
