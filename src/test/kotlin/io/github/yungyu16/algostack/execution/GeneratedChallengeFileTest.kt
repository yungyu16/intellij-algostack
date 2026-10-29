package io.github.yungyu16.algostack.execution

import com.intellij.testFramework.TestApplicationManager
import io.github.yungyu16.algostack.domain.*
import io.github.yungyu16.algostack.leetcode.model.LeetCodeChallengeData
import io.github.yungyu16.algostack.leetcode.model.LeetCodeQuestionCodeSnippet
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class GeneratedChallengeFileTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun initializeApplication() {
            TestApplicationManager.getInstance()
        }
    }

    private fun prepare(root: Path, language: Language): PreparedChallenge {
        val snippet = if (language == Language.Java) """
            class Solution {
                String originalName = "P0001_TwoSum";
                // public class P0001_TwoSum must not be renamed here
                public int solve() {}
                public String done() { return "P0001_TwoSum"; }
            }
        """.trimIndent() else "func solve() string { return \"public class P0001_TwoSum\" }"
        val slug = language.toLeetCode(LanguageVersion.AnyVersion)
        val data = LeetCodeChallengeData(
            "9001", "1", "Two Sum", "two-sum", "<p>public class P0001_TwoSum</p>",
            difficulty = "Easy",
            exampleTestcaseList = emptyList(),
            codeSnippets = listOf(LeetCodeQuestionCodeSnippet(slug, slug, snippet))
        )
        return renderChallenge(
            data, CodeDojo.LeetCodeCN, language, LanguageVersion.AnyVersion,
            ChallengeConventions.generation(root, CodeDojo.LeetCodeCN, language)
        )
    }

    @Test
    fun pivotIndexTemplatesKeepRelatedProblemLinksWithoutLosingIdentity() {
        for ((language, version) in ChallengeConventions.languages) {
            val platform = if (language == Language.Java) "class Solution { public int pivotIndex(int[] nums) {} }"
            else "func pivotIndex(nums []int) int {}"
            val reference = "https://leetcode.cn/problems/find-the-middle-index-in-array/"
            val data = LeetCodeChallengeData(
                "724", "724", "Find Pivot Index", "find-pivot-index", "",
                translatedTitle = "寻找数组的中心下标",
                translatedContent = "<p>注意：本题与1991题相同：<a href='$reference'>$reference</a></p>",
                difficulty = "Easy", exampleTestcaseList = listOf("[1,7,3,6,5,6]"),
                codeSnippets = listOf(LeetCodeQuestionCodeSnippet("fixture", language.toLeetCode(version), platform))
            )
            val prepared = renderChallenge(
                data, CodeDojo.LeetCodeCN, language, version,
                ChallengeConventions.generation(Path.of("/unused"), CodeDojo.LeetCodeCN, language)
            )
            assertTrue(prepared.code.contains("@leetcode https://leetcode.cn/problems/find-pivot-index/"))
            assertTrue(prepared.code.contains(reference))
            assertEquals(prepared.identity, ChallengeRecognition.recognize(prepared.code, language.fileExt))
            assertEquals(prepared.identity, io.github.yungyu16.algostack.utils.ChallengeFiles.resolve("moved/renamed.${language.fileExt}", prepared.code))
            assertFalse(language.extractCodeFromRegion(prepared.code).contains("@leetcode"))
            assertFalse(prepared.file.exists())
        }
    }

    @Test
    fun javaRenameRequiresAClassIdentifierButGoKeepsItsExistingNameRules() {
        for (name in listOf("Retry", "Solution", "第二次", "Retry_2", "\$Retry")) {
            assertTrue(name, isChallengeFileNameValid(name, Language.Java))
        }
        for (name in listOf(
            "", " ", "2Retry", "Retry.java", "Retry-other", "../Retry", "nested/Retry", "nested\\Retry",
            "class", "public", "true", "null", "var", "yield", "record", "sealed", "permits", "_"
        )) {
            assertFalse(name, isChallengeFileNameValid(name, Language.Java))
        }
        assertTrue(isChallengeFileNameValid("retry-other", Language.GO))
        assertFalse(isChallengeFileNameValid(" ", Language.GO))
    }

    @Test
    fun renamedJavaWrapperMatchesFileAndCompilesWithoutChangingPlatformRegionOrExistingSource() {
        val root = Files.createTempDirectory("renamed-java-")
        try {
            val prepared = prepare(root, Language.Java)
            Files.createDirectories(prepared.file.toPath().parent)
            val existing = "// user implementation must remain untouched"
            Files.writeString(prepared.file.toPath(), existing)
            val target = File(prepared.file.parentFile, "Retry.java")
            val renamed = prepared.codeForFile(target)
            assertTrue(renamed.contains("public class Retry {"))
            assertEquals(prepared.code.replace("public class P0001_TwoSum {", "public class Retry {"), renamed)
            assertEquals(
                Language.Java.extractCodeFromRegion(prepared.code),
                Language.Java.extractCodeFromRegion(renamed)
            )
            assertTrue(renamed.contains("class Solution {"))
            assertTrue(renamed.contains("throw new UnsupportedOperationException(\"Not implemented\");"))
            assertEquals(prepared.identity, ChallengeRecognition.recognize(renamed, "java"))
            assertEquals(existing, Files.readString(prepared.file.toPath()))
            assertEquals(prepared.code, prepared.codeForFile(prepared.file))
            assertThrows(IllegalArgumentException::class.java) {
                prepared.codeForFile(
                    File(
                        target.parentFile,
                        "bad-name.java"
                    )
                )
            }
            Files.writeString(target.toPath(), renamed)
            val compiler = ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "javac").toString(),
                "--release", "8", "-Xlint:-options", "-d", root.toString(), target.toString()
            ).redirectErrorStream(true).start()
            val output = compiler.inputStream.bufferedReader().use { it.readText() }
            assertEquals(output, 0, compiler.waitFor())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun refusesToRenameAPlatformClassInsideTheSubmissionRegion() {
        val prepared = prepare(Path.of("unused"), Language.Java)
        val platformOnly = prepared.copy(code = Language.Java.encloseCodeInRegion("public class P0001_TwoSum {}"))
        assertThrows(IllegalArgumentException::class.java) {
            platformOnly.codeForFile(File(prepared.file.parentFile, "Retry.java"))
        }
    }

    @Test
    fun goRenameDoesNotTransformAnySourceText() {
        val prepared = prepare(Path.of("unused"), Language.GO)
        assertEquals(prepared.code, prepared.codeForFile(File(prepared.file.parentFile, "retry-other.go")))
        assertEquals(prepared.code, prepared.codeForFile(prepared.file))
    }
}
