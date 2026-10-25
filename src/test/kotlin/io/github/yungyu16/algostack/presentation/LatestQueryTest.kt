package io.github.yungyu16.algostack.presentation

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class LatestQueryTest {
    @Test fun aSupersededSlowRequestCannotReplaceTheNewResult() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val runner = LatestQuery<Int, String>(this, 0) { value ->
            if (value == 1) { entered.complete(Unit); withContext(NonCancellable) { release.await() }; "old" } else "new"
        }
        try {
            val old = runner.submit(1)!!; entered.await()
            runner.submit(2)
            withTimeout(2_000) { runner.states.first { it is QueryState.Success } }
            release.complete(Unit); old.join()
            assertEquals(QueryState.Success("new"), runner.states.value)
        } finally { release.complete(Unit); runner.close() }
    }
    @Test fun queuedResultsAreRejectedAfterReplacementCancellationOrClose() = runBlocking {
        withTimeout(2_000) {
            val runner = LatestQuery<Int, String>(this, 0) { if (it < 0) error("failed") else "same" }
            try {
                runner.submit(1)!!.join()
                val queued = runner.states.value
                assertTrue(runner.isCurrent(queued))
                runner.submit(2)!!.join()
                // 相同业务结果也来自另一次请求，不能用结构相等代替身份校验。
                assertEquals(queued, runner.states.value)
                assertFalse(runner.isCurrent(queued))
                val replaced = runner.states.value
                runner.cancel()
                assertFalse(runner.isCurrent(replaced))
                runner.submit(-1)!!.join()
                val failure = runner.states.value
                assertTrue(failure is QueryState.Failure)
                assertTrue(runner.isCurrent(failure))
                runner.submit(3)!!.join()
                assertFalse(runner.isCurrent(failure))
                val last = runner.states.value
                runner.close()
                assertFalse(runner.isCurrent(last))
            } finally { runner.close() }
        }
    }

    @Test fun closingTheViewStopsPendingQueriesWithoutCancellingTheProject() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val runner = LatestQuery<Int, Int>(this, 0) { entered.complete(Unit); awaitCancellation() }
        val job = runner.submit(1)!!; entered.await()
        runner.close(); job.join()
        assertTrue(currentCoroutineContext().isActive)
        assertEquals(QueryState.Idle, runner.states.value)
        assertNull(runner.submit(2))
    }
}
