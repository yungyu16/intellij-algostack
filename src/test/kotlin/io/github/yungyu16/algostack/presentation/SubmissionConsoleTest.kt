package io.github.yungyu16.algostack.presentation

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestApplicationManager
import io.github.yungyu16.algostack.domain.CodeDojo
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

/** 在测试应用中阻塞真实 EDT，核验 Console 交付；不访问平台或打开链接。 */
class SubmissionConsoleTest {
    companion object {
        @BeforeClass @JvmStatic fun initializeApplication() { TestApplicationManager.getInstance() }
    }

    @Test fun cancellationWhileWaitingForEdtStillPrintsAcceptedIdAndLink() = runBlocking {
        checkDelivery(disposeBeforeOutput = false)
    }

    @Test fun disposalWhileWaitingForEdtSkipsAcceptedOutput() = runBlocking {
        checkDelivery(disposeBeforeOutput = true)
    }

    private suspend fun checkDelivery(disposeBeforeOutput: Boolean) = withTimeout(10_000) {
        lateinit var console: LogConsole
        lateinit var view: ConsoleViewImpl
        SwingUtilities.invokeAndWait {
            console = LogConsole(ProjectManager.getInstance().defaultProject)
            view = console.view as ConsoleViewImpl
            view.component
            view.flushDeferredText()
        }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var continued = false
        var job: Job? = null
        try {
            SwingUtilities.invokeLater {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS)) { "EDT release timed out" }
                // 模拟 Console 随项目关闭／插件卸载而先于排队输出释放。
                if (disposeBeforeOutput) console.dispose()
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            job = launch(start = CoroutineStart.UNDISPATCHED) {
                // UNDISPATCHED 确保调用已进入生产输出方法、挂起于 EDT，而非尚未启动。
                console.submissionAccepted(CodeDojo.LeetCodeCN, "two-sum", "42")
                continued = true
            }
            assertFalse(job.isCompleted)
            job.cancel()
            release.countDown()
            job.join()
            assertTrue(job.isCancelled)
            assertFalse("取消后不能继续轮询", continued)
            SwingUtilities.invokeAndWait {
                view.flushDeferredText()
                val accepted = "平台已受理提交（42）"
                assertEquals(if (disposeBeforeOutput) 0 else 1, Regex(Regex.escape(accepted)).findAll(view.text).count())
                assertEquals(if (disposeBeforeOutput) 0 else 1, requireNotNull(view.getHyperlinks()).getAllHyperlinks(0, view.text.length).size)
            }
        } finally {
            release.countDown()
            job?.cancel()
            SwingUtilities.invokeAndWait { Disposer.dispose(console) }
        }
    }
}
