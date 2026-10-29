package io.github.yungyu16.algostack.execution

import io.github.yungyu16.algostack.leetcode.LeetCodeClient
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException

class InitializationDiagnosticsTest {
    @Test
    fun controlledMessagesExplainHttpTimeoutCancellationAndTemplateFailures() {
        val cases = listOf(
            LeetCodeClient.HttpException(403, "private-domain") to "HTTP 403",
            LeetCodeClient.InvalidResponse("response=secret") to "GraphQL 契约异常",
            SocketTimeoutException("Cookie: secret") to "网络请求超时",
            CancellationException("Authorization: secret") to "初始化任务已取消",
            IllegalArgumentException("Template must provide a matching problem URL and one submission region") to "唯一提交区域"
        )
        for ((failure, explanation) in cases) {
            val details = initializationFailureDetails(failure)
            assertTrue(details.contains(explanation))
            assertFalse(details.contains("secret"))
            assertFalse(details.contains("private-domain"))
        }
    }

    @Test
    fun causeCyclesAndStackDepthAreBoundedAndSuppressedMessagesAreNotLogged() {
        val first = IllegalStateException("private first")
        val second = IllegalArgumentException("private second")
        first.initCause(second)
        second.initCause(first)
        first.addSuppressed(IllegalStateException("Cookie: secret"))
        first.stackTrace = Array(40) { StackTraceElement("Example", "invoke", "Example.kt", it) }
        val details = initializationFailureDetails(first)
        assertEquals(16, details.lineSequence().count { it.contains("at Example.invoke") })
        assertEquals(1, Regex("exception=java.lang.IllegalStateException").findAll(details).count())
        assertTrue(details.contains("remaining_causes=omitted"))
        assertFalse(details.contains("private"))
        assertFalse(details.contains("secret"))
    }
}
