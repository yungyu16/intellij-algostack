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
