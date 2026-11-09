package io.github.yungyu16.algostack.presentation.sidebar

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProcessCanceledException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import javax.swing.JLabel
import javax.swing.JPanel

class SidebarPanelFactoryTest {
    private class Panel : JPanel(), Disposable {
        override fun dispose() = Unit
    }

    @Test
    fun failedPanelDoesNotPreventFollowingPanels() {
        val failures = mutableListOf<Exception>()
        val failure = IllegalStateException("CefApp: TERMINATED")
        val fallback = createSidebarPanel<Panel>("Description", failures::add) { throw failure }
        val healthy = Panel()
        val following = createSidebarPanel("Solutions", failures::add) { healthy }
        assertSame(healthy, following)
        assertEquals(listOf(failure), failures)
        assertTrue(fallback is Disposable)
        assertTrue((fallback.getComponent(0) as JLabel).text.contains("完整重启 IDEA"))
    }

    @Test
    fun cancellationAndFatalErrorsAreNotHidden() {
        for (failure in listOf(ProcessCanceledException(), CancellationException(), OutOfMemoryError())) {
            try {
                createSidebarPanel<Panel>("Description", { fail("Must not report cancellation or fatal errors") }) { throw failure }
                fail("Expected original failure")
            } catch (actual: Throwable) {
                assertSame(failure, actual)
            }
        }
    }
}
