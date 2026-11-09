package io.github.yungyu16.algostack.presentation.sidebar

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import kotlinx.coroutines.CancellationException
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

internal fun <Panel> createSidebarPanel(
    name: String,
    report: (Exception) -> Unit = { Logger.getInstance(SidebarWindowFactory::class.java).warn("Cannot initialize $name sidebar", it) },
    create: () -> Panel
): JPanel where Panel : JPanel, Panel : Disposable = try {
    create()
} catch (cancelled: ProcessCanceledException) {
    throw cancelled
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    report(failure)
    object : JPanel(BorderLayout()), Disposable {
        init {
            add(JLabel("$name 初始化失败，请完整重启 IDEA；若仍失败，请检查 idea.log。", SwingConstants.CENTER), BorderLayout.CENTER)
        }

        override fun dispose() = Unit
    }
}
