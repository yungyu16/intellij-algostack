package io.github.yungyu16.algostack.execution

import io.github.yungyu16.algostack.domain.ChallengeRecognition
import io.github.yungyu16.algostack.domain.CodeDojo
import io.github.yungyu16.algostack.domain.Language
import io.github.yungyu16.algostack.domain.LanguageVersion
import io.github.yungyu16.algostack.leetcode.model.LeetCodeChallengeData
import io.github.yungyu16.algostack.leetcode.model.LeetCodeQuestionCodeSnippet
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

class LanguageProjectInitializationTest {
    companion object {
        @org.junit.BeforeClass
        @JvmStatic
        fun initializeApplication() {
            com.intellij.testFramework.TestApplicationManager.getInstance()
        }
    }

    private fun fixture(block: suspend (Path) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("language-project-").toRealPath()
        try {
            block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun setting(root: Path, language: Language): GenerationSettings =
        ChallengeConventions.generation(root, CodeDojo.LeetCodeCN, language)

    private fun prepared(language: Language, setting: GenerationSettings): PreparedChallenge {
        val snippet = if (language == Language.Java) "class Solution {}" else "func solve() {}"
        val data = LeetCodeChallengeData(
            "9001", "1", "Two Sum", "two-sum", "<p>Fixture</p>",
            difficulty = "Easy",
            exampleTestcaseList = emptyList(),
            codeSnippets = listOf(
                LeetCodeQuestionCodeSnippet(
                    "fixture",
                    if (language == Language.Java) "java" else "golang",
                    snippet
                )
            )
        )
        return renderChallenge(data, CodeDojo.LeetCodeCN, language, LanguageVersion.AnyVersion, setting)
    }

    private suspend fun initialize(
        root: Path, language: Language,
        goVersion: () -> String = { "go version go1.20.14 linux/amd64" }
    ): InitializationResult {
        val prepared = prepared(language, setting(root, language))
        val target = prepared.file.toPath()
        return initializeChallengeFile(
            root, target, prepared.code,
            { ChallengeRecognition.recognize(Files.readString(target), language.fileExt) == prepared.identity },
            {}, {},
            initializeProject = { ensureLanguageProject(root, CodeDojo.LeetCodeCN, language, goVersion) })
    }

    @Test
    fun javaFirstGenerationCreatesJava8MavenOnlyAndRepeatPreservesCodeAndPom() = fixture { root ->
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

    @Test
    fun goFirstGenerationUsesAvailableVersionAndIndependentPackagesAndIsIdempotent() = fixture { root ->
        assertEquals("created", initialize(root, Language.GO).status)
        val module = root.resolve("code/go/go.mod")
        assertEquals("module leetcode-practice\n\ngo 1.20\n", Files.readString(module))
        assertTrue(Files.readString(root.resolve("code/go/leetcodecn/p0001_two_sum/main.go")).contains("package main"))
        assertFalse(Files.exists(root.resolve("code/java")))
        Files.writeString(module, "module custom.example/practice\n\ngo 1.19\n")
        assertEquals(
            "already_exists",
            initialize(root, Language.GO, goVersion = { error("must not query Go for existing module") }).status
        )
        assertEquals("module custom.example/practice\n\ngo 1.19\n", Files.readString(module))
    }

    @Test
    fun existingSkeletonStillInitializesMissingProject() = fixture { root ->
        for (language in listOf(Language.Java, Language.GO)) {
            val prepared = prepared(language, setting(root, language))
            Files.createDirectories(prepared.file.toPath().parent)
            Files.writeString(prepared.file.toPath(), prepared.code)
            assertEquals("already_exists", initialize(root, language).status)
            assertTrue(Files.exists(root.resolve(if (language == Language.Java) "code/java/pom.xml" else "code/go/go.mod")))
            assertEquals(prepared.code, Files.readString(prepared.file.toPath()))
        }
    }

    @Test
    fun nearestAncestorProjectIsPreservedWithoutCreatingNestedModule() = fixture { root ->
        for ((language, descriptor) in listOf(Language.Java to "pom.xml", Language.GO to "go.mod")) {
            val layout = ChallengeConventions.layout(root, CodeDojo.LeetCodeCN, language)
            val ancestor = Files.createDirectories(root.resolve("code")).resolve(descriptor)
            Files.writeString(ancestor, "user configuration")
            // 更远的非普通描述文件不能盖过最近的有效工程。
            Files.createDirectory(root.resolve(descriptor))
            assertEquals("created", initialize(root, language, goVersion = { error("no query") }).status)
            assertEquals("user configuration", Files.readString(ancestor))
            assertFalse(Files.exists(layout.moduleRoot.resolve(descriptor)))
        }
    }

    @Test
    fun projectRootDescriptorsAreReusedWithoutQueryingGo() = fixture { root ->
        for ((language, descriptor) in listOf(Language.Java to "pom.xml", Language.GO to "go.mod")) {
            Files.writeString(root.resolve(descriptor), "root project")
            assertEquals("created", initialize(root, language, goVersion = { error("no query") }).status)
            assertEquals("root project", Files.readString(root.resolve(descriptor)))
            val layout = ChallengeConventions.layout(root, CodeDojo.LeetCodeCN, language)
            assertFalse(Files.exists(layout.moduleRoot.resolve(descriptor)))
        }
    }

    @Test
    fun parallelGenerationsAreIdempotentAndProjectFailureRetainsSkeleton() = fixture { root ->
        coroutineScope {
            (1..12).map {
                async(Dispatchers.Default) {
                    ensureLanguageProject(
                        root,
                        CodeDojo.LeetCodeCN,
                        Language.Java
                    )
                }
            }.awaitAll()
        }
        assertTrue(Files.readString(root.resolve("code/java/pom.xml")).endsWith("</project>\n"))
        val failed = initialize(root, Language.GO, goVersion = { error("local Go unavailable") })
        assertEquals("partial", failed.status); assertFalse(failed.associated)
        assertTrue(Files.exists(root.resolve("code/go/leetcodecn/p0001_two_sum/main.go")))
        assertFalse(Files.exists(root.resolve("code/go/go.mod")))
        assertEquals("already_exists", initialize(root, Language.GO).status)
    }

    @Test
    fun descriptorCreatedAfterLookupIsReusedWithoutOverwriting() = fixture { root ->
        val descriptor = root.resolve("code/go/go.mod")
        val existing = "module concurrent.example/practice\n\ngo 1.19\n"
        val result = initialize(root, Language.GO, goVersion = {
            // 工具链查询位于祖先查找之后、CREATE_NEW 之前，确定性模拟外部进程抢先创建。
            Files.writeString(descriptor, existing)
            "go version go1.20.14 linux/amd64"
        })
        assertEquals("created", result.status)
        assertEquals(existing, Files.readString(descriptor))
    }

    @Test
    fun symlinksNonRegularDescriptorsAndOtherBuildsAreNotTouched() = fixture { root ->
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
    }

    @Test
    fun conventionalSourceParentsCannotBeSymlinksOrRegularFiles() = fixture { root ->
        val outside = Files.createTempDirectory("language-project-outside-")
        try {
            val code = root.resolve("code")
            Files.createSymbolicLink(code, outside)
            for (language in listOf(Language.Java, Language.GO)) {
                try {
                    ensureLanguageProject(root, CodeDojo.LeetCodeCN, language)
                    fail("Symlink parent must be rejected")
                } catch (_: IllegalArgumentException) {
                }
            }
            Files.list(outside).use { assertEquals(0L, it.count()) }
            Files.delete(code)
            Files.writeString(code, "keep")
            for (language in listOf(Language.Java, Language.GO)) {
                try {
                    ensureLanguageProject(root, CodeDojo.LeetCodeCN, language)
                    fail("Non-directory parent must be rejected")
                } catch (_: IllegalArgumentException) {
                }
            }
            assertEquals("keep", Files.readString(code))
        } finally {
            outside.toFile().deleteRecursively()
        }
    }

    @Test
    fun otherBuildConfigurationsInAncestorsAreRejectedWithoutCreatingDescriptors() = fixture { root ->
        for ((language, builds) in listOf(
            Language.Java to listOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts"),
            Language.GO to listOf("go.work")
        )) {
            val layout = ChallengeConventions.layout(root, CodeDojo.LeetCodeCN, language)
            val descriptor = if (language == Language.Java) "pom.xml" else "go.mod"
            for (build in builds) {
                val existing = Files.writeString(root.resolve(build), "keep")
                val result = initialize(root, language, goVersion = { error("no query") })
                assertEquals("partial", result.status)
                assertTrue(result.reason!!.contains("Existing build/workspace configuration"))
                assertFalse(Files.exists(layout.moduleRoot.resolve(descriptor)))
                assertEquals("keep", Files.readString(existing))
                Files.delete(existing)
            }
        }
    }

    @Test
    fun conflictingExistingCodeDoesNotInitializeAProject() = fixture { root ->
        val target = prepared(Language.GO, setting(root, Language.GO)).file.toPath()
        Files.createDirectories(target.parent); Files.writeString(target, "keep")
        assertEquals("conflict", initialize(root, Language.GO).status)
        assertEquals("keep", Files.readString(target)); assertFalse(Files.exists(root.resolve("code/go/go.mod")))
    }

    @Test
    fun goDirectiveAcceptsStableAndPrereleaseButRejectsUnknownOutput() {
        assertEquals("1.26", goDirective("go version go1.26.4 darwin/arm64\n"))
        assertEquals("1.27", goDirective("go version go1.27rc1 linux/amd64"))
        assertThrows(IllegalStateException::class.java) { goDirective("unknown") }
    }

    @Test
    fun optionalLocalGoValidationDoesNotDownloadDependencies() = fixture { root ->
        // CI 默认只做离线纯逻辑验证；显式启用时使用本机 Go 验证真实生成文件。
        if (System.getenv("ALGOSTACK_VERIFY_GO") != "true") return@fixture
        val settings = setting(root, Language.GO)
        ensureLanguageProject(root, CodeDojo.LeetCodeCN, Language.GO)
        val source = prepared(Language.GO, settings)
        Files.createDirectories(source.file.toPath().parent); Files.writeString(source.file.toPath(), source.code)
        Files.createDirectories(root.resolve("code/go/leetcodecn/p0002_another"))
        Files.writeString(root.resolve("code/go/leetcodecn/p0002_another/main.go"), "package main\nfunc solve() {}\n")
        for (arguments in listOf(listOf("mod", "edit", "-json"), listOf("test", "./..."))) {
            val process = ProcessBuilder(listOf("go") + arguments).directory(root.resolve("code/go").toFile())
                .redirectErrorStream(true).apply {
                    environment().putAll(
                        mapOf(
                            "GOPROXY" to "off",
                            "GOSUMDB" to "off",
                            "GOTOOLCHAIN" to "local",
                            "GOWORK" to "off"
                        )
                    )
                }.start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            assertEquals(output, 0, process.waitFor())
        }
    }
}
