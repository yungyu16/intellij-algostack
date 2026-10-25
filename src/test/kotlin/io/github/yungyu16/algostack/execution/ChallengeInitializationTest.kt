package io.github.yungyu16.algostack.execution

import io.github.yungyu16.algostack.domain.*
import io.github.yungyu16.algostack.leetcode.api.model.*
import io.github.yungyu16.algostack.utils.ChallengeFiles
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class ChallengeInitializationTest {
    companion object {
        @org.junit.BeforeClass @JvmStatic fun initializeApplication() { com.intellij.testFramework.TestApplicationManager.getInstance() }
    }

    private fun fixture(block: suspend (Path) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("initialize-challenge-").toRealPath()
        try { block(root) }
        finally { Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }

    @Test fun templateAndInitializationUseOnlyContentWithoutCreatingProjectState() = fixture { root ->
        for ((language, snippetSlug, snippet) in listOf(Triple(Language.Java, "java", "class Solution {}"), Triple(Language.GO, "golang", "func solve() {}"))) {
            val data = LeetCodeChallengeData("9001", "26", "Fixture", "fixture", "<p>-10<font size='1'>0</font> &lt;= n</p>",
                isPaidOnly = false, difficulty = "Easy", likes = 0, dislikes = 0, similarQuestions = "[]",
                exampleTestcaseList = listOf("[1,1,2]"), topicTags = emptyList(),
                codeSnippets = listOf(LeetCodeQuestionCodeSnippet(snippetSlug, snippetSlug, snippet)), hints = emptyList(), testCase = "", metaData = "{}")
            val prepared = renderChallenge(data, CodeDojo.LeetCodeCN, language, LanguageVersion.AnyVersion,
                GenerationSettings(root.toString(), "case_\${challenge.frontendQuestionId}_\${challenge.slug}", "// https://leetcode.cn/problems/\${challenge.slug}/\n/*\${challenge.descriptionText}*/\n\${challenge.code}"))
            val target = prepared.file.toPath()
            var refreshes = 0
            suspend fun initialize() = initializeChallengeFile(root, target, prepared.code,
                identityMatches = { ChallengeFiles.resolve(target.toString(), Files.readString(target)) == prepared.identity },
                refresh = { refreshes++ }, open = {})
            val result = initialize()
            assertEquals("created", result.status); assertTrue(result.associated)
            assertEquals(false, result.implemented)
            assertTrue(Files.readString(target).contains("-100 <= n"))
            assertTrue(Files.readString(target).contains("Submit Code Region Begin"))
            val implementation = prepared.code + "\n// user implementation"
            Files.writeString(target, implementation)
            val repeated = initialize()
            assertEquals("already_exists", repeated.status); assertNull(repeated.implemented)
            assertEquals(implementation, Files.readString(target)); assertEquals(1, refreshes)
            assertEquals("9001", prepared.template.questionId)
            assertEquals("26", prepared.template.frontendQuestionId)
            assertFalse(Files.exists(root.resolve(".idea")))
        }
    }

    @Test fun legacyXmlIsUntouchedAndCannotProveIdentityOrBlockNewFiles() = fixture { root ->
        val xml = root.resolve(".idea/CodeEpiphany/challenge.xml")
        Files.createDirectories(xml.parent)
        // 故意使用非法 XML：初始化不解析旧状态，也不清理它。
        val old = "<retired custom-cases and stale path mapping"
        Files.writeString(xml, old)
        for (language in listOf(Language.Java, Language.GO)) {
            val identity = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", language)
            val file = root.resolve("old.${language.fileExt}")
            val region = language.encloseCodeInRegion("// existing implementation")
            val code = "// https://leetcode.cn/problems/two-sum/\n$region"
            suspend fun initialize(expected: ChallengeIdentity = identity) = initializeChallengeFile(root, file, code,
                { ChallengeFiles.resolve(file.toString(), Files.readString(file)) == expected }, {}, {})
            Files.writeString(file, region)
            assertEquals("conflict", initialize().status)
            assertEquals(region, Files.readString(file))
            Files.writeString(file, code)
            assertEquals("already_exists", initialize().status)
            assertEquals("conflict", initialize(identity.copy(slug = "three-sum")).status)
            assertEquals("conflict", initialize(identity.copy(version = LanguageVersion.SpecificVersion("17"))).status)
            Files.delete(file)
            assertEquals("created", initialize().status)
        }
        assertEquals(old, Files.readString(xml))
    }

    @Test fun failureAfterCreatingFilePreservesItAndReportsPartial() = fixture { root ->
        val file = root.resolve("main.go")
        val result = initializeChallengeFile(root, file, "new", { false }, { error("refresh failure") }, {})
        assertEquals("partial", result.status); assertFalse(result.associated); assertEquals("new", Files.readString(file))
    }

    @Test fun identityVerificationFailureDoesNotOpenEditor() = fixture { root ->
        val result = initializeChallengeFile(root, root.resolve("main.go"), "new", { false }, {}, { error("must not open") })
        assertEquals("partial", result.status); assertFalse(result.associated)
        assertTrue(result.reason!!.contains("Source identity verification failed"))
    }

    @Test fun editorFailureDoesNotHideVerifiedIdentity() = fixture { root ->
        val result = initializeChallengeFile(root, root.resolve("main.go"), "new", { true }, {}, { error("editor failure") })
        assertEquals("partial", result.status); assertTrue(result.associated)
    }

    @Test fun cancellationPropagatesWithoutPretendingNoFileWasCreated() = fixture { root ->
        val entered = CompletableDeferred<Unit>(); val file = root.resolve("main.go")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val task = scope.async { initializeChallengeFile(root, file, "new", { false }, {
                entered.complete(Unit); awaitCancellation()
            }, {}) }
            entered.await(); task.cancelAndJoin()
            assertTrue(task.isCancelled); assertEquals("new", Files.readString(file))
        } finally { scope.cancel() }
    }

    @Test fun unsafePathsCannotCreateFilesOutsideRootOrThroughSymlinks() = fixture { root ->
        assertThrows(IllegalArgumentException::class.java) { safeInitializationPath(root, root.resolve("../outside.go")) }
        val real = Files.createDirectory(root.resolve("real"))
        Files.createSymbolicLink(root.resolve("link"), real)
        assertThrows(IllegalArgumentException::class.java) { safeInitializationPath(root, root.resolve("link/test.go")) }
        assertFalse(Files.exists(real.resolve("test.go")))
    }
}
