package io.github.yungyu16.algostack.presentation

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
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
            assertEquals("new", (runner.states.value as QueryState.Success).value)
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
                assertEquals((queued as QueryState.Success).value, (runner.states.value as QueryState.Success).value)
                assertNotEquals(queued, runner.states.value)
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

    @Test fun equalSuccessIsDeliveredAfterTheQueuedPreviousResultIsRejected() = runBlocking {
        assertLatestEqualResultDelivered(failure = false)
    }

    @Test fun repeatedFailureIsDeliveredAfterTheQueuedPreviousFailureIsRejected() = runBlocking {
        assertLatestEqualResultDelivered(failure = true)
    }

    private suspend fun assertLatestEqualResultDelivered(failure: Boolean) = withTimeout(2_000) {
        val error = IllegalStateException("same failure")
        val runner = LatestQuery<Int, String>(this, 0) { if (failure) throw error else "same result" }
        val queued = CompletableDeferred<QueryState<String>>()
        val release = CompletableDeferred<Unit>()
        val applied = CompletableDeferred<QueryState<String>>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            runner.states.collect { state ->
                if (state is QueryState.Success || state is QueryState.Failure) {
                    if (!queued.isCompleted) {
                        queued.complete(state)
                        // 模拟消费者已取得 A、等待 UI 调度；期间不能消费中间的 Loading。
                        release.await()
                    }
                    if (runner.isCurrent(state)) applied.complete(state)
                }
            }
        }
        try {
            runner.submit(1)!!.join()
            val previous = queued.await()
            runner.submit(2)!!.join()
            val latest = runner.states.value
            assertFalse(runner.isCurrent(previous))
            release.complete(Unit)
            assertSame(latest, applied.await())
        } finally {
            release.complete(Unit)
            collector.cancel()
            runner.close()
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
