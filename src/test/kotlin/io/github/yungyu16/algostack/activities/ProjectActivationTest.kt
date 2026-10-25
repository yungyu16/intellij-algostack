package io.github.yungyu16.algostack.activities

import io.github.yungyu16.algostack.domain.CodeDojo
import io.github.yungyu16.algostack.runtime.AuthenticationSession
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class ProjectActivationTest {
    private fun fixture(block: suspend (Path) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("project-activation-")
        try { block(root) } finally { root.toFile().deleteRecursively() }
    }

    @Test fun unmarkedProjectsDoNotStartBusinessOrReadCredentials() = fixture { root ->
        var starts = 0; var reads = 0
        for (path in listOf(null, root.toString())) activateMarkedProject(path, { starts++ }, { reads++; false })
        assertEquals(0, starts); assertEquals(0, reads)
        assertEquals(0, root.toFile().listFiles()!!.size)
    }

    @Test fun fileAndDirectoryMarkersRestoreBothExistingSiteContractsWithoutAView() = fixture { root ->
        val marker = root.resolve(".leetcode")
        for (directory in listOf(false, true)) {
            if (directory) Files.createDirectory(marker) else Files.createFile(marker)
            var started = false
            val restored = mutableSetOf<CodeDojo>()
            activateMarkedProject(root.toString(), { started = true }, {
                assertTrue(started); restored += it; false
            })
            assertEquals(CodeDojo.entries.toSet(), restored)
            assertEquals(1, root.toFile().listFiles()!!.size)
            Files.delete(marker)
        }
    }

    @Test fun concurrentStartupAndViewsShareAppSessionAndDoNotRepeatInvalidRestore() = fixture { root ->
        Files.createFile(root.resolve(".leetcode"))
        var reads = 0; var validations = 0; var current: String? = null
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val session = AuthenticationSession<String>(
            { reads++; "saved" }, { validations++; entered.complete(Unit); release.await(); null }, {},
            { current = it }, { current != null }, { throw it })
        val restore: suspend (CodeDojo) -> Boolean = { if (it == CodeDojo.LeetCodeCN) session.ensureAuthenticated(null) else false }
        coroutineScope {
            val firstStartup = async { activateMarkedProject(root.toString(), {}, restore) }
            entered.await()
            val secondStartup = async { activateMarkedProject(root.toString(), {}, restore) }
            val view = async { session.ensureAuthenticated(null) }
            yield(); release.complete(Unit)
            firstStartup.await(); secondStartup.await(); assertFalse(view.await())
        }
        assertEquals(1, reads); assertEquals(1, validations); assertNull(current)
        var prompts = 0
        assertTrue(session.ensureAuthenticated { prompts++; session.accept { "manual" } })
        assertEquals(1, prompts)
    }

    @Test fun projectCloseCancelsRestoreAndNextProjectCanRetry() = fixture { root ->
        Files.createFile(root.resolve(".leetcode"))
        var reads = 0; var current: String? = null
        val entered = CompletableDeferred<Unit>()
        var cancelValidation = true
        val session = AuthenticationSession({ reads++; "saved" }, {
            if (cancelValidation) { entered.complete(Unit); awaitCancellation() }; it
        }, {}, { current = it }, { current != null }, { throw it })
        val restore: suspend (CodeDojo) -> Boolean = { if (it == CodeDojo.LeetCodeCN) session.ensureAuthenticated(null) else false }
        coroutineScope {
            val task = launch { activateMarkedProject(root.toString(), {}, restore) }
            entered.await(); task.cancelAndJoin()
            assertNull(current)
        }
        cancelValidation = false
        activateMarkedProject(root.toString(), {}, restore)
        assertEquals("saved", current); assertEquals(2, reads)
    }
}
