package com.wenjunhuang.codeepiphany.execution

import com.wenjunhuang.codeepiphany.configuration.ChallengeSettings
import com.wenjunhuang.codeepiphany.domain.*
import com.wenjunhuang.codeepiphany.leetcode.api.model.*
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

    @Test fun templateFileAndContentIdentityWorkForBothLanguagesWithoutOverwriting() = fixture { root ->
        run {
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
                        settings.resolve(target.toString(), Files.readString(target))?.identity == prepared.identity
                    }, hasMapping = { settings.hasMapping(target.toString()) },
                    persistAndAssociate = {
                        writes++
                        settings.addChallenge(target.toString(), ChallengeSettings.Association(prepared.identity, listOf(ChallengeSettings.TestCase("custom", "keep")), emptyList()))
                    }, open = {})
                val result = initialize()
                assertEquals("created", result.status); assertTrue(result.associated)
                assertEquals(false, result.implemented)
                assertTrue(Files.readString(target).contains("-100 <= n"))
                assertTrue(Files.readString(target).contains("Submit Code Region Begin"))
                val before = settings.findAssociation(target.toString())
                val implementation = prepared.code + "\n// user implementation"
                Files.writeString(target, implementation)
                val repeated = initialize()
                assertEquals("already_exists", repeated.status); assertNull(repeated.implemented)
                assertEquals(implementation, Files.readString(target)); assertEquals(1, writes)
                assertEquals(before, settings.findAssociation(target.toString()))
                assertEquals("9001", prepared.template.questionId)
                assertEquals("26", prepared.template.frontendQuestionId)
                assertEquals(prepared.identity, ChallengeRecognition.recognize(prepared.code, language.fileExt))
            }
        }
    }

    @Test fun existingContentWithoutMappingIsAcceptedButDifferentVersionConflicts() = fixture { root ->
        val identity = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", Language.Java, LanguageVersion.SpecificVersion("17"))
        val file = root.resolve("old.java")
        val code = ChallengeRecognition.header(identity) + Language.Java.encloseCodeInRegion("class Solution {}")
        Files.writeString(file, code)
        val settings = ChallengeSettings()
        suspend fun initialize(expected: ChallengeIdentity) = initializeChallengeFile(root, file, "new",
            { settings.resolve(file.toString(), Files.readString(file))?.identity == expected },
            { settings.hasMapping(file.toString()) }, { error("must not write association") }, { error("must not overwrite or open existing file") })
        assertEquals("already_exists", initialize(identity).status)
        assertEquals("conflict", initialize(identity.copy(version = LanguageVersion.AnyVersion)).status)
        assertEquals(code, Files.readString(file))
        assertFalse(settings.hasMapping(file.toString()))
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
        val result = initializeChallengeFile(root, file, "new", { false }, { false }, { error("association failure") }, {})
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
