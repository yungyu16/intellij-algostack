package io.github.yungyu16.algostack.execution

import com.intellij.testFramework.TestApplicationManager
import io.github.yungyu16.algostack.domain.*
import io.github.yungyu16.algostack.leetcode.model.LeetCodeChallengeData
import io.github.yungyu16.algostack.leetcode.model.LeetCodeQuestionCodeSnippet
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class JavaTemplateStubTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun initializeApplication() {
            TestApplicationManager.getInstance()
        }
    }

    @Test
    fun emptyMethodsThrowButConstructorsImplementationsAndCommentsRemain() {
        val code = """
            class Solution {
                Solution() {}
                /** public int notAMethod() {} */
                String example = "public int notAMethod() {}";
                public int count() {
                    // TODO: implement
                }
                public String[] names() {}
                public <T> T value(T input) throws IllegalArgumentException {}
                public void update(int... values) {}
                public int done() { return 42; }
            }
        """.trimIndent()
        val result = javaTemplateStub(code)
        assertEquals(4, Regex("throw new UnsupportedOperationException").findAll(result).count())
        assertTrue(result.contains("Solution() {}"))
        assertTrue(result.contains("/** public int notAMethod() {} */"))
        assertTrue(result.contains("\"public int notAMethod() {}\""))
        assertTrue(result.contains("// TODO: implement"))
        assertTrue(result.contains("public <T> T value(T input) throws IllegalArgumentException"))
        assertTrue(result.contains("public int done() { return 42; }"))
        assertEquals(result, javaTemplateStub(result))
        val abstractCode = "interface Api { int count(); } abstract class Base { abstract int count(); }"
        assertEquals(abstractCode, javaTemplateStub(abstractCode))
        assertFalse(javaTemplateStub(code.replace("\n", "\r\n")).replace("\r\n", "").contains('\n'))
    }

    @Test
    fun generatedJavaTemplateCompilesWithEmptyPlatformMethodsAndGoIsUnchanged() {
        val root = Files.createTempDirectory("java-stub-")
        try {
            for ((language, snippet) in listOf(
                Language.Java to "class Solution { public int solve() {} public void update() {} }",
                Language.GO to "func solve() int { panic(\"not implemented\") }"
            )) {
                val slug = language.toLeetCode()
                val data = LeetCodeChallengeData(
                    "9001", "1", "Two Sum", "two-sum", "<p>Fixture</p>",
                    difficulty = "Easy",
                    exampleTestcaseList = emptyList(),
                    codeSnippets = listOf(LeetCodeQuestionCodeSnippet(slug, slug, snippet))
                )
                val prepared = renderChallenge(
                    data, CodeDojo.LeetCodeCN, language,
                    ChallengeConventions.generation(root, CodeDojo.LeetCodeCN, language)
                )
                assertEquals(prepared.identity, ChallengeRecognition.recognize(prepared.code, language.fileExt))
                if (language == Language.GO) {
                    assertEquals(snippet, language.extractCodeFromRegion(prepared.code).trim())
                    continue
                }
                assertEquals(2, Regex("throw new UnsupportedOperationException").findAll(prepared.code).count())
                assertTrue(prepared.code.contains("throw new UnsupportedOperationException(\"Not implemented\");"))
                val source = root.resolve("P0001_TwoSum.java")
                Files.writeString(source, prepared.code)
                val compiler = ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "javac").toString(),
                    "--release", "8", "-Xlint:-options", "-d", root.toString(), source.toString()
                ).redirectErrorStream(true).start()
                val output = compiler.inputStream.bufferedReader().use { it.readText() }
                assertEquals(output, 0, compiler.waitFor())
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
