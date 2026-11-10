package io.github.yungyu16.algostack.execution

import io.github.yungyu16.algostack.domain.CodeDojo
import io.github.yungyu16.algostack.domain.Language
import io.github.yungyu16.algostack.leetcode.model.LeetCodeChallengeData
import io.github.yungyu16.algostack.leetcode.model.LeetCodeQuestionCodeSnippet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeDescriptionTextTest {
    companion object {
        @org.junit.BeforeClass
        @JvmStatic
        fun initializeApplication() {
            com.intellij.testFramework.TestApplicationManager.getInstance()
        }
    }

    @Test
    fun preservesFontNumbersAndMath() {
        assertEquals(
            "1 <= n <= 10^5; a_i > 0", ChallengeDescriptionText.convert(
                "<p><font color='red'>1</font> &lt;= n &lt;= <FONT size=2>10</FONT><SUP>5</SUP>; a<sub>i</sub> &gt; 0</p>"
            )
        )
    }

    @Test
    fun preservesGenericsComparisonsAndUnknownTags() {
        val text = "List<Node> List&lt;Node&gt; a < b && b > c <custom>value</custom>"
        assertEquals(
            "List<Node> List<Node> a < b && b > c <custom>value</custom>",
            ChallengeDescriptionText.convert(text)
        )
    }

    @Test
    fun preservesExistingWhitespaceAndDecodesEntitiesOnlyAfterTagRemoval() {
        assertEquals(
            "  one\n\n  two  & <b> &#49; 1\n", ChallengeDescriptionText.convert(
                "<pre>  one\n\n  two&nbsp;\u00a0&amp; &lt;b&gt; &amp;#49; &#49;\n</pre>"
            )
        )
        assertEquals("ab", ChallengeDescriptionText.convert("<p>a</p><br/><p>b</p>"))
        assertEquals("", ChallengeDescriptionText.convert(""))
    }

    @Test
    fun rendersImageSourcesWithoutFetchingThem() {
        assertEquals(
            "[图片: https://example.invalid/a b.png?x=1&y=2]\n[图片: /b.png]\n[图片: /c.png]",
            ChallengeDescriptionText.convert(
                "<IMG alt='x > y' SRC=\"https://example.invalid/a b.png?x=1&amp;y=2\" />\n<img src='/b.png'>\n<img src=/c.png><img alt='missing'>"
            )
        )
        assertEquals("[图片: /a&amp;b]", ChallengeDescriptionText.convert("<img src='/a&amp;amp;b'>"))
    }

    @Test
    fun neutralizesCommentTerminatorsAfterDecodingAndTagRemoval() {
        assertEquals(
            "* / * / * / [图片: /a* /b]", ChallengeDescriptionText.convert(
                "*/ *<b></b>/ &#42;&#47; <img src='/a*/b'>"
            )
        )
    }

    @Test
    fun exposesTextThroughTemplateWithoutChangingHtmlOrSubmissionCode() {
        for (dojo in CodeDojo.entries) {
            for ((language, slug) in listOf(Language.Java to "java", Language.GO to "golang")) {
                val html = "<p><font color='red'>10</font><sup>5</sup> &lt;= n; List&lt;Node&gt; */</p>\n"
                val data = LeetCodeChallengeData(
                    questionId = "1", frontendQuestionId = "1", title = "Fixture", titleSlug = "fixture",
                    content = html, difficulty = "Easy",
                    exampleTestcaseList = emptyList(),
                    codeSnippets = listOf(LeetCodeQuestionCodeSnippet(slug, slug, "fixture code"))
                )
                val challenge = challengeTemplate(data, dojo, language)
                val originalHtml = challenge.description
                assertTrue(originalHtml.contains("<font"))
                assertEquals(originalHtml, challenge.description)
                val rendered = renderTemplate("/*\n{{descriptionText}}*/\n{{code}}", challenge)
                assertEquals("/*\n10^5 <= n; List<Node> * /\n*/\n${challenge.getCode()}", rendered)
                assertEquals(originalHtml, challenge.description)
                // 内置代码模板包含纯文本题面，但不改变平台代码和提交区域。
                val defaultTemplate = ChallengeConventions.generation(java.nio.file.Path.of("/unused"), dojo, language).code
                val defaultCode = renderTemplate(defaultTemplate, challenge)
                assertTrue(defaultCode.contains(challenge.getCode()))
                assertTrue(defaultCode.contains("10^5 <= n; List<Node> * /"))
                assertEquals("fixture code", language.extractCodeFromRegion(defaultCode).trim())
            }
        }
    }
}
