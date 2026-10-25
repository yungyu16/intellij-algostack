package io.github.yungyu16.algostack.runtime

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AuthenticationSessionTest {
    private class Fixture {
        var saved: String? = "saved"
        var current: String? = null
        var read: suspend () -> String? = { saved }
        var validate: suspend (String) -> String? = { it }
        var persist: suspend (String?) -> Unit = { saved = it }
        val failures = mutableListOf<Exception>()
        val session = AuthenticationSession({ read() }, { validate(it) }, { persist(it) },
            { current = it }, { current != null }, { failures += it })
    }

    @Test fun restoreFailureAllowsInteractiveLoginButCancellationDoesNot() = runBlocking {
        val f = Fixture()
        f.read = { error("storage unavailable") }
        assertTrue(f.session.ensureAuthenticated { f.session.accept("manual") })
        assertEquals("manual", f.current)
        assertEquals(1, f.failures.size)
        val cancelled = Fixture()
        cancelled.read = { throw CancellationException("cancelled") }
        var prompted = false
        try {
            cancelled.session.ensureAuthenticated { prompted = true; true }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertFalse(prompted)
    }

    @Test fun lateRestoreCannotOverwriteManualSession() = runBlocking {
        val f = Fixture()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        f.validate = { if (it == "saved") { entered.complete(Unit); release.await() }; it }
        val old = async { f.session.restore() }
        entered.await()
        assertNull(f.current)
        assertTrue(f.session.accept("new"))
        release.complete(Unit)
        assertFalse(old.await())
        assertEquals("new", f.saved); assertEquals("new", f.current)
    }

    @Test fun restoreDoesNotPreemptPendingManualValidation() = runBlocking {
        val f = Fixture()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        f.validate = { entered.complete(Unit); release.await(); it }
        val manual = async { f.session.accept("manual") }
        entered.await()
        assertFalse(f.session.restore())
        release.complete(Unit)
        assertTrue(manual.await()); assertEquals("manual", f.current)
    }

    @Test fun logoutInvalidatesPendingValidation() = runBlocking {
        val f = Fixture()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        f.validate = { entered.complete(Unit); release.await(); it }
        val old = async { f.session.accept("old") }
        entered.await(); f.session.logout(); release.complete(Unit)
        assertFalse(old.await())
        assertNull(f.saved); assertNull(f.current)
    }

    @Test fun newerValidationWinsAndSitesAreIndependent() = runBlocking {
        val f = Fixture(); val other = Fixture()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        f.validate = { if (it == "old") { entered.complete(Unit); release.await() }; it }
        val old = async { f.session.accept("old") }
        entered.await()
        assertTrue(other.session.accept("other"))
        assertTrue(f.session.accept("new"))
        release.complete(Unit); assertFalse(old.await())
        assertEquals("other", other.current); assertEquals("new", f.current)
    }

    @Test fun cancellingValidationNeverWritesCredentials() = runBlocking {
        val f = Fixture(); val entered = CompletableDeferred<Unit>()
        f.validate = { entered.complete(Unit); awaitCancellation() }
        val pending = launch { f.session.accept("new") }
        entered.await(); pending.cancelAndJoin()
        assertEquals("saved", f.saved); assertNull(f.current)
    }

    @Test fun cancellationDuringCommitCompletesPublicationAndStillPropagates() = runBlocking {
        val f = Fixture()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        f.persist = { f.saved = it; entered.complete(Unit); release.await() }
        var returned = false
        val pending = launch { f.session.accept("new"); returned = true }
        entered.await(); pending.cancel(); release.complete(Unit); pending.join()
        assertFalse(returned)
        assertEquals("new", f.saved); assertEquals("new", f.current)
    }

    @Test fun persistenceFailureDoesNotPublishAndInvalidCandidatePreservesSession() = runBlocking {
        val f = Fixture()
        f.session.accept("existing")
        f.validate = { null }
        assertFalse(f.session.accept("invalid"))
        assertEquals("existing", f.current)
        f.validate = { it }; f.persist = { error("write failed") }
        try { f.session.accept("new"); fail("Expected storage failure") } catch (_: IllegalStateException) { }
        assertEquals("existing", f.current)
    }

    @Test fun concurrentEnsureOnlyPromptsOnce() = runBlocking {
        val f = Fixture(); f.saved = null
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var prompts = 0
        val first = async { f.session.ensureAuthenticated { prompts++; entered.complete(Unit); release.await(); f.session.accept("new") } }
        entered.await()
        val second = async { f.session.ensureAuthenticated { prompts++; false } }
        yield(); release.complete(Unit)
        assertTrue(first.await()); assertTrue(second.await()); assertEquals(1, prompts)
    }

    @Test fun concurrentSilentEnsureRestoresOnceAndNeverPromptsForMissingOrInvalidCredentials() = runBlocking {
        for (saved in listOf(null, "invalid", "valid")) {
            val f = Fixture(); f.saved = saved
            var reads = 0; var validations = 0
            f.read = { reads++; f.saved }
            f.validate = { validations++; if (it == "valid") it else null }
            val results = (1..12).map { async { f.session.ensureAuthenticated(null) } }.awaitAll()
            assertEquals(List(12) { saved == "valid" }, results)
            assertEquals(1, reads); assertEquals(if (saved == null) 0 else 1, validations)
            assertEquals(if (saved == "valid") "valid" else null, f.current)
        }
    }

    @Test fun silentStorageFailureDoesNotLoopButExplicitLoginCanRetry() = runBlocking {
        val f = Fixture(); var reads = 0
        f.read = { reads++; error("storage unavailable") }
        repeat(3) { assertFalse(f.session.ensureAuthenticated(null)) }
        assertEquals(1, reads); assertEquals(1, f.failures.size)
        f.read = { reads++; "recovered" }
        var prompted = false
        assertTrue(f.session.ensureAuthenticated { prompted = true; false })
        assertFalse(prompted); assertEquals(2, reads); assertEquals("recovered", f.current)
    }

    @Test fun browserErrorsHaveBoundedBackoffAndInvalidCookiesDoNotLoop() {
        var now = 0L
        val retry = AuthenticationRetry { now }
        assertTrue(retry.begin("a")); retry.failed()
        now = 1_999; assertFalse(retry.begin("a"))
        now = 2_000; assertTrue(retry.begin("a")); retry.failed()
        now = 6_999; assertFalse(retry.begin("a"))
        now = 7_000; assertTrue(retry.begin("a")); retry.failed()
        now = 100_000; assertFalse(retry.begin("a"))
        assertTrue(retry.begin("b")); assertFalse(retry.begin("b"))
    }
}
