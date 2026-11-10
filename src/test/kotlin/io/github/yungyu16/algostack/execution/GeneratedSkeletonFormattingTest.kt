package io.github.yungyu16.algostack.execution

import com.intellij.testFramework.TestApplicationManager
import io.github.yungyu16.algostack.domain.*
import io.github.yungyu16.algostack.leetcode.model.LeetCodeChallengeData
import io.github.yungyu16.algostack.leetcode.model.LeetCodeQuestionCodeSnippet
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.nio.file.Path

class GeneratedSkeletonFormattingTest {
    companion object {
        @BeforeClass @JvmStatic
        fun initializeApplication() { TestApplicationManager.getInstance() }
    }

    private fun generate(language: Language, snippet: String): PreparedChallenge {
        val platformLanguage = language.toLeetCode()
        val data = LeetCodeChallengeData(
            "724", "724", "Find Pivot Index", "find-pivot-index", "<p>Compare &lt;= 10000; List&lt;Node&gt;</p>",
            difficulty = "Easy", exampleTestcaseList = emptyList(),
            codeSnippets = listOf(LeetCodeQuestionCodeSnippet(platformLanguage, platformLanguage, snippet))
        )
        return renderChallenge(data, CodeDojo.LeetCodeCN, language,
            ChallengeConventions.generation(Path.of("/unused"), CodeDojo.LeetCodeCN, language))
    }

    @Test
    fun javaWrapperIndentsTheEntireSubmissionAndRemovesWhitespaceOnlyLines() {
        val result = generate(Language.Java, "class Solution {\n    public int pivotIndex(int[] nums) {\n        \n    }\n}")
        val code = result.code
        assertTrue(code.contains("    // IMPORTANT!! Submit Code Region Begin"))
        assertTrue(code.contains("    class Solution {\n        public int pivotIndex(int[] nums) {\n            throw new UnsupportedOperationException(\"Not implemented\");\n        }\n    }"))
        assertTrue(code.contains("    // IMPORTANT!! Submit Code Region End"))
        assertFalse(code, code.lineSequence().any { it.isNotEmpty() && it.isBlank() })
        assertTrue(code.contains("@leetcode https://leetcode.cn/problems/find-pivot-index/"))
        assertTrue(code.contains("Compare <= 10000; List<Node>"))
        assertEquals(result.identity, ChallengeRecognition.recognize(code, "java"))
    }

    @Test
    fun emptyJavaAndGoBodiesHaveExactlyOnePlaceholderLine() {
        assertEquals("class Solution {\n    public int solve() {\n        throw new UnsupportedOperationException(\"Not implemented\");\n    }\n}",
            javaTemplateStub("class Solution {\n    public int solve() {\n        \n    }\n}"))
        val go = generate(Language.GO, "func solve() int {\n    \n\n}").code
        assertTrue(go.contains("func solve() int {\n\tpanic(\"Not implemented\")\n}"))
        assertTrue(go.contains("// IMPORTANT!! Submit Code Region Begin"))
        assertTrue(go.contains("// IMPORTANT!! Submit Code Region End"))
        assertFalse(go.lineSequence().any { it.isNotEmpty() && it.isBlank() })
    }

    @Test
    fun javaFormattingKeepsLiteralWhitespaceAndCanRunOnEdtOrBackground() {
        val code = "class Literal { String text = \"  keep  \"; String newline = \"\\n  keep  \"; }"
        val background = formatGeneratedJava("Literal.java", code)
        assertTrue(background.contains("\"  keep  \""))
        assertTrue(background.contains("\"\\n  keep  \""))
        var edt: String? = null
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait {
            edt = formatGeneratedJava("Literal.java", code)
        }
        assertEquals(background, edt)
        assertEquals(background, formatGeneratedJava("Literal.java", background))
    }

    @Test
    fun textBlockValueIsPreservedAndBackgroundReadLockIsRejectedBeforeEdtWait() {
        val code = "class Text { String value = \"\"\"\n    keep  spaces\\s\n      nested\n    \"\"\"; }"
        fun literalValue(text: String): Any? = com.intellij.openapi.application.ReadAction.compute<Any?, RuntimeException> {
            val project = com.intellij.openapi.project.ProjectManager.getInstance().defaultProject
            val file = com.intellij.psi.PsiFileFactory.getInstance(project).createFileFromText("Text.java", com.intellij.ide.highlighter.JavaFileType.INSTANCE, text)
            com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, com.intellij.psi.PsiLiteralExpression::class.java).single().value
        }
        val expected = literalValue(code)
        assertNotNull(expected)
        assertEquals(expected, literalValue(formatGeneratedJava("Text.java", code)))
        assertFalse(com.intellij.openapi.application.ApplicationManager.getApplication().isDispatchThread)
        val failure = assertThrows(IllegalStateException::class.java) {
            com.intellij.openapi.application.ReadAction.compute<String, RuntimeException> {
                formatGeneratedJava("Text.java", code)
            }
        }
        assertTrue(failure.message!!.contains("outside a background read action"))
    }

    @Test
    fun placeholdersKeepCommentsAndNonemptyImplementations() {
        val java = javaTemplateStub("class Solution {\n    public int solve() {\n        // 待实现\n\n    }\n    public String text() { return \"  keep  \"; }\n}")
        assertTrue(java.contains("// 待实现\n        throw"))
        assertTrue(java.contains("return \"  keep  \";"))
        val go = goTemplateStub("func solve() int {\n\t// 待实现\n\n}\nfunc done() string { return `  keep  ` }")
        assertTrue(go.contains("// 待实现\n\tpanic"))
        assertTrue(go.contains("func done() string { return `  keep  ` }"))
        assertEquals(java, javaTemplateStub(java))
        assertEquals(go, goTemplateStub(go))
    }
}
