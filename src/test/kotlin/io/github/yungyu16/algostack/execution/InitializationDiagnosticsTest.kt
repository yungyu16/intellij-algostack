package io.github.yungyu16.algostack.execution

import io.github.yungyu16.algostack.domain.ChallengeSourceException
import io.github.yungyu16.algostack.domain.RecognitionFailure
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
            ChallengeSourceException(RecognitionFailure.INVALID_DECLARATION) to "@leetcode 声明格式",
            ChallengeSourceException(RecognitionFailure.INVALID_REGION) to "提交区域缺失",
            ChallengeSourceException(RecognitionFailure.IDENTITY_MISMATCH) to "本次预期题目不一致",
            LeetCodeClient.InvalidResponse("response=secret") to "GraphQL 契约异常",
            SocketTimeoutException("Cookie: secret") to "网络请求超时",
            CancellationException("Authorization: secret") to "初始化任务已取消"
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
