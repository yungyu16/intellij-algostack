package com.wenjunhuang.algostack.execution

import com.wenjunhuang.algostack.domain.*
import com.wenjunhuang.algostack.leetcode.api.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

class LanguageProjectInitializationTest {
    private fun fixture(block: suspend (Path) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("language-project-").toRealPath()
        try { block(root) } finally { root.toFile().deleteRecursively() }
    }
    private fun setting(root: Path, language: Language): GenerationSettings =
        ChallengeConventions.generation(root, CodeDojo.LeetCodeCN, language)
    private fun prepared(language: Language, setting: GenerationSettings): PreparedChallenge {
        val snippet = if (language == Language.Java) "class Solution {}" else "func solve() {}"
        val data = LeetCodeChallengeData("9001", "1", "Two Sum", "two-sum", "<p>Fixture</p>",
            isPaidOnly = false, difficulty = "Easy", likes = 0, dislikes = 0, similarQuestions = "[]",
            exampleTestcaseList = emptyList(), topicTags = emptyList(), hints = emptyList(), testCase = "", metaData = "{}",
            codeSnippets = listOf(LeetCodeQuestionCodeSnippet("fixture", if (language == Language.Java) "java" else "golang", snippet)))
        return renderChallenge(data, CodeDojo.LeetCodeCN, language, LanguageVersion.AnyVersion, setting)
    }
    private suspend fun initialize(root: Path, language: Language, setting: GenerationSettings = setting(root, language),
        goVersion: () -> String = { "go version go1.20.14 linux/amd64" }): InitializationResult {
        val prepared = prepared(language, setting)
        val target = prepared.file.toPath()
        return initializeChallengeFile(root, target, prepared.code,
            { ChallengeRecognition.recognize(Files.readString(target), language.fileExt) == prepared.identity },
            { false }, {}, {},
            initializeProject = { ensureLanguageProject(root, CodeDojo.LeetCodeCN, language, setting, goVersion) })
    }

    @Test fun javaFirstGenerationCreatesJava8MavenOnlyAndRepeatPreservesCodeAndPom() = fixture { root ->
        assertEquals("created", initialize(root, Language.Java).status)
        val pom = root.resolve("code/java/pom.xml")
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom.toFile())
        assertEquals("4.0.0", xml.getElementsByTagName("modelVersion").item(0).textContent)
        assertEquals("1.8", xml.getElementsByTagName("maven.compiler.source").item(0).textContent)
        assertEquals("1.8", xml.getElementsByTagName("maven.compiler.target").item(0).textContent)
        assertFalse(Files.exists(root.resolve("code/go")))
        val source = root.resolve("code/java/src/main/java/leetcodecn/P0001_TwoSum.java")
        val code = Files.readString(source) + "\n// user code"
        Files.writeString(source, code)
        val customPom = "custom pom, must not parse or rewrite"
        Files.writeString(pom, customPom)
        assertEquals("already_exists", initialize(root, Language.Java).status)
        assertEquals(customPom, Files.readString(pom)); assertEquals(code, Files.readString(source))
    }

    @Test fun goFirstGenerationUsesAvailableVersionAndIndependentPackagesAndIsIdempotent() = fixture { root ->
        assertEquals("created", initialize(root, Language.GO).status)
        val module = root.resolve("code/go/go.mod")
        assertEquals("module leetcode-practice\n\ngo 1.20\n", Files.readString(module))
        assertTrue(Files.readString(root.resolve("code/go/leetcodecn/P0001_TwoSum/main.go")).contains("package main"))
        assertFalse(Files.exists(root.resolve("code/java")))
        Files.writeString(module, "module custom.example/practice\n\ngo 1.19\n")
        assertEquals("already_exists", initialize(root, Language.GO, goVersion = { error("must not query Go for existing module") }).status)
        assertEquals("module custom.example/practice\n\ngo 1.19\n", Files.readString(module))
    }

    @Test fun existingSkeletonStillInitializesMissingProject() = fixture { root ->
        for (language in listOf(Language.Java, Language.GO)) {
            val prepared = prepared(language, setting(root, language))
            Files.createDirectories(prepared.file.toPath().parent)
            Files.writeString(prepared.file.toPath(), prepared.code)
            assertEquals("already_exists", initialize(root, language).status)
            assertTrue(Files.exists(root.resolve(if (language == Language.Java) "code/java/pom.xml" else "code/go/go.mod")))
            assertEquals(prepared.code, Files.readString(prepared.file.toPath()))
        }
    }

    @Test fun customPathsReuseNearestExistingProjectButNeverGuessAModuleRoot() = fixture { root ->
        for ((language, descriptor) in listOf(Language.Java to "pom.xml", Language.GO to "go.mod")) {
            val custom = root.resolve("custom/${language.value}")
            val setting = setting(root, language).copy(folder = custom.resolve("sources").toString())
            Files.createDirectories(custom)
            Files.writeString(custom.resolve(descriptor), "user configuration")
            assertEquals("created", initialize(root, language, setting).status)
            assertEquals("user configuration", Files.readString(custom.resolve(descriptor)))
            assertFalse(Files.exists(root.resolve("code")))
            val missing = setting.copy(folder = root.resolve("unknown/${language.value}").toString())
            val result = initialize(root, language, missing)
            assertEquals("partial", result.status)
            assertTrue(result.reason!!.contains("Custom source folder"))
            assertTrue(prepared(language, missing).file.exists())
            assertFalse(Files.exists(Path.of(missing.folder).resolve(descriptor)))
        }
    }

    @Test fun existingAncestorModuleIsPreservedWithoutCreatingNestedModule() = fixture { root ->
        Files.writeString(root.resolve("go.mod"), "module parent\n\ngo 1.20\n")
        assertEquals("created", initialize(root, Language.GO, goVersion = { error("no query") }).status)
        assertFalse(Files.exists(root.resolve("code/go/go.mod")))
        assertEquals("module parent\n\ngo 1.20\n", Files.readString(root.resolve("go.mod")))
    }

    @Test fun parallelGenerationsAreIdempotentAndProjectFailureRetainsSkeleton() = fixture { root ->
        coroutineScope {
            val settings = setting(root, Language.Java)
            (1..12).map { async(Dispatchers.Default) { ensureLanguageProject(root, CodeDojo.LeetCodeCN, Language.Java, settings) } }.awaitAll()
        }
        assertTrue(Files.readString(root.resolve("code/java/pom.xml")).endsWith("</project>\n"))
        val failed = initialize(root, Language.GO, goVersion = { error("local Go unavailable") })
        assertEquals("partial", failed.status); assertFalse(failed.associated)
        assertTrue(Files.exists(root.resolve("code/go/leetcodecn/P0001_TwoSum/main.go")))
        assertFalse(Files.exists(root.resolve("code/go/go.mod")))
        assertEquals("already_exists", initialize(root, Language.GO).status)
    }

    @Test fun symlinksNonRegularDescriptorsAndOtherBuildsAreNotTouched() = fixture { root ->
        val javaRoot = Files.createDirectories(root.resolve("code/java"))
        val original = Files.writeString(root.resolve("original.xml"), "keep")
        Files.createSymbolicLink(javaRoot.resolve("pom.xml"), original)
        assertEquals("partial", initialize(root, Language.Java).status)
        assertEquals("keep", Files.readString(original))
        Files.delete(javaRoot.resolve("pom.xml")); Files.createDirectory(javaRoot.resolve("pom.xml"))
        assertEquals("partial", initialize(root, Language.Java).status)
        Files.delete(javaRoot.resolve("pom.xml")); Files.writeString(javaRoot.resolve("build.gradle.kts"), "keep")
        assertEquals("partial", initialize(root, Language.Java).status)
        assertFalse(Files.exists(javaRoot.resolve("pom.xml")))
        val linked = Files.createSymbolicLink(root.resolve("linked"), javaRoot)
        try {
            ensureLanguageProject(root, CodeDojo.LeetCodeCN, Language.Java, setting(root, Language.Java).copy(folder = linked.resolve("source").toString()))
            fail("Symlink parent must be rejected")
        } catch (_: IllegalArgumentException) { }
        try {
            ensureLanguageProject(root, CodeDojo.LeetCodeCN, Language.Java, setting(root, Language.Java).copy(folder = root.parent.toString()))
            fail("Outside project must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun conflictingExistingCodeDoesNotInitializeAProject() = fixture { root ->
        val target = prepared(Language.GO, setting(root, Language.GO)).file.toPath()
        Files.createDirectories(target.parent); Files.writeString(target, "keep")
        assertEquals("conflict", initialize(root, Language.GO).status)
        assertEquals("keep", Files.readString(target)); assertFalse(Files.exists(root.resolve("code/go/go.mod")))
    }

    @Test fun goDirectiveAcceptsStableAndPrereleaseButRejectsUnknownOutput() {
        assertEquals("1.26", goDirective("go version go1.26.4 darwin/arm64\n"))
        assertEquals("1.27", goDirective("go version go1.27rc1 linux/amd64"))
        assertThrows(IllegalStateException::class.java) { goDirective("unknown") }
    }

    @Test fun optionalLocalGoValidationDoesNotDownloadDependencies() = fixture { root ->
        // CI 默认只做离线纯逻辑验证；显式启用时使用本机 Go 验证真实生成文件。
        if (System.getenv("ALGOSTACK_VERIFY_GO") != "true") return@fixture
        val settings = setting(root, Language.GO)
        ensureLanguageProject(root, CodeDojo.LeetCodeCN, Language.GO, settings)
        val source = prepared(Language.GO, settings)
        Files.createDirectories(source.file.toPath().parent); Files.writeString(source.file.toPath(), source.code)
        Files.createDirectories(root.resolve("code/go/leetcodecn/P0002_Another"))
        Files.writeString(root.resolve("code/go/leetcodecn/P0002_Another/main.go"), "package main\nfunc solve() {}\n")
        for (arguments in listOf(listOf("mod", "edit", "-json"), listOf("test", "./..."))) {
            val process = ProcessBuilder(listOf("go") + arguments).directory(root.resolve("code/go").toFile())
                .redirectErrorStream(true).apply {
                    environment().putAll(mapOf("GOPROXY" to "off", "GOSUMDB" to "off", "GOTOOLCHAIN" to "local", "GOWORK" to "off"))
                }.start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            assertEquals(output, 0, process.waitFor())
        }
    }
}
