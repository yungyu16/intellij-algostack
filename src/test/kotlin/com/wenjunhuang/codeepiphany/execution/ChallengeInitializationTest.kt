package com.wenjunhuang.codeepiphany.execution

import com.wenjunhuang.codeepiphany.configuration.ChallengeSettings
import com.wenjunhuang.codeepiphany.domain.*
import com.wenjunhuang.codeepiphany.leetcode.api.model.*
import com.wenjunhuang.codeepiphany.storage.SqliteDatabase
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class ChallengeInitializationTest {
    private fun fixture(block: suspend (Path) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("initialize-challenge-").toRealPath()
        try { block(root) }
        finally { Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }

    @Test fun templateFileDatabaseAndAssociationWorkForBothLanguagesWithoutOverwriting() = fixture { root ->
        SqliteDatabase { root.resolve("challenges.db").toFile() }.use { db ->
            val settings = ChallengeSettings()
            for ((language, snippetSlug, snippet) in listOf(Triple(Language.Java, "java", "class Solution {}"), Triple(Language.GO, "golang", "func solve() {}"))) {
                val data = LeetCodeChallengeData("9001", "26", "Fixture", "fixture", "<p>-10<font size='1'>0</font> &lt;= n</p>",
                    isPaidOnly = false, difficulty = "Easy", likes = 0, dislikes = 0, similarQuestions = "[]",
                    exampleTestcaseList = listOf("[1,1,2]"), topicTags = emptyList(),
                    codeSnippets = listOf(LeetCodeQuestionCodeSnippet(snippetSlug, snippetSlug, snippet)), hints = emptyList(), testCase = "", metaData = "{}")
                val prepared = renderChallenge(data, CodeDojo.LeetCodeCN, language, LanguageVersion.AnyVersion,
                    GenerationSettings(root.toString(), "case_\${challenge.frontendQuestionId}_\${challenge.slug}", "/*\${challenge.descriptionText}*/\n\${challenge.code}"))
                val target = prepared.file.toPath()
                var writes = 0
                suspend fun initialize() = initializeChallengeFile(root, target, prepared.code,
                    associationMatches = {
                        val item = settings.findChallengeId(target.toString())
                        item != null && db.read { it.executionRequest(item.challengeId, item.challengeLanguageId)?.language } == language
                    }, hasMapping = { settings.findChallengeId(target.toString()) != null },
                    persistAndAssociate = {
                        val saved = db.transaction { it.saveChallenge(prepared.draft) }; writes++
                        settings.addChallenge(target.toString(), ChallengeSettings.Association(saved.challengeId, saved.languageId,
                            CodeDojo.LeetCodeCN, language, listOf(ChallengeSettings.TestCase("custom", "keep")), emptyList()))
                    }, open = {})
                val result = initialize()
                assertEquals("created", result.status); assertTrue(result.associated)
                assertEquals(false, result.implemented)
                assertTrue(Files.readString(target).contains("-100 <= n"))
                assertTrue(Files.readString(target).contains("Submit Code Region Begin"))
                val before = settings.findChallengeId(target.toString())
                Files.writeString(target, "user implementation")
                val repeated = initialize()
                assertEquals("already_exists", repeated.status); assertNull(repeated.implemented)
                assertEquals("user implementation", Files.readString(target)); assertEquals(1, writes)
                assertEquals(before, settings.findChallengeId(target.toString()))
                assertEquals("9001", db.read { it.challenge(before!!.challengeId)!!.dojoId })
            }
        }
    }

    @Test fun existingUnassociatedFileAndStaleMappingAreConflicts() = fixture { root ->
        val file = root.resolve("main.go"); Files.writeString(file, "keep")
        val result = initializeChallengeFile(root, file, "new", { false }, { false }, { error("must not persist") }, {})
        assertEquals("conflict", result.status); assertEquals("keep", Files.readString(file))
        Files.delete(file)
        assertEquals("conflict", initializeChallengeFile(root, file, "new", { false }, { true }, { error("must not persist") }, {}).status)
        assertFalse(Files.exists(file))
    }

    @Test fun failureAfterCreatingFilePreservesItAndReportsPartial() = fixture { root ->
        val file = root.resolve("main.go")
        val result = initializeChallengeFile(root, file, "new", { false }, { false }, { error("database failure") }, {})
        assertEquals("partial", result.status); assertFalse(result.associated); assertEquals("new", Files.readString(file))
    }

    @Test fun editorFailureDoesNotHideVerifiedAssociation() = fixture { root ->
        val result = initializeChallengeFile(root, root.resolve("main.go"), "new", { true }, { false }, {}, { error("editor failure") })
        assertEquals("partial", result.status); assertTrue(result.associated)
    }

    @Test fun cancellationPropagatesWithoutPretendingNoFileWasCreated() = fixture { root ->
        val entered = CompletableDeferred<Unit>(); val file = root.resolve("main.go")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val task = scope.async { initializeChallengeFile(root, file, "new", { false }, { false }, {
                entered.complete(Unit); awaitCancellation()
            }, {}) }
            entered.await(); task.cancelAndJoin()
            assertTrue(task.isCancelled); assertEquals("new", Files.readString(file))
        } finally { scope.cancel() }
    }

    @Test fun unsafePathsCannotCreateFilesOutsideRootOrThroughSymlinks() = fixture { root ->
        val outside = root.resolve("../outside.go")
        assertThrows(IllegalArgumentException::class.java) { safeInitializationPath(root, outside) }
        val real = Files.createDirectory(root.resolve("real"))
        Files.createSymbolicLink(root.resolve("link"), real)
        assertThrows(IllegalArgumentException::class.java) { safeInitializationPath(root, root.resolve("link/test.go")) }
        assertFalse(Files.exists(real.resolve("test.go")))
    }
}
