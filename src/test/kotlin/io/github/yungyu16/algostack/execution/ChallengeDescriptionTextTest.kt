package io.github.yungyu16.algostack.execution

import io.github.yungyu16.algostack.domain.CodeDojo
import io.github.yungyu16.algostack.domain.Language
import io.github.yungyu16.algostack.domain.LanguageVersion
import io.github.yungyu16.algostack.leetcode.api.model.LeetCodeChallengeData
import io.github.yungyu16.algostack.leetcode.api.model.LeetCodeQuestionCodeSnippet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeDescriptionTextTest {
    @Test fun preservesFontNumbersAndMath() {
        assertEquals("1 <= n <= 10^5; a_i > 0", ChallengeDescriptionText.convert(
            "<p><font color='red'>1</font> &lt;= n &lt;= <FONT size=2>10</FONT><SUP>5</SUP>; a<sub>i</sub> &gt; 0</p>"
        ))
    }

    @Test fun preservesGenericsComparisonsAndUnknownTags() {
        val text = "List<Node> List&lt;Node&gt; a < b && b > c <custom>value</custom>"
        assertEquals("List<Node> List<Node> a < b && b > c <custom>value</custom>", ChallengeDescriptionText.convert(text))
    }

    @Test fun preservesExistingWhitespaceAndDecodesEntitiesOnlyAfterTagRemoval() {
        assertEquals("  one\n\n  two  & <b> &#49; 1\n", ChallengeDescriptionText.convert(
            "<pre>  one\n\n  two&nbsp;\u00a0&amp; &lt;b&gt; &amp;#49; &#49;\n</pre>"
        ))
        assertEquals("ab", ChallengeDescriptionText.convert("<p>a</p><br/><p>b</p>"))
        assertEquals("", ChallengeDescriptionText.convert(""))
    }

    @Test fun rendersImageSourcesWithoutFetchingThem() {
        assertEquals("[图片: https://example.invalid/a b.png?x=1&y=2]\n[图片: /b.png]\n[图片: /c.png]", ChallengeDescriptionText.convert(
            "<IMG alt='x > y' SRC=\"https://example.invalid/a b.png?x=1&amp;y=2\" />\n<img src='/b.png'>\n<img src=/c.png><img alt='missing'>"
        ))
        assertEquals("[图片: /a&amp;b]", ChallengeDescriptionText.convert("<img src='/a&amp;amp;b'>"))
    }

    @Test fun neutralizesCommentTerminatorsAfterDecodingAndTagRemoval() {
        assertEquals("* / * / * / [图片: /a* /b]", ChallengeDescriptionText.convert(
            "*/ *<b></b>/ &#42;&#47; <img src='/a*/b'>"
        ))
    }

    @Test fun exposesTextThroughVelocityWithoutChangingHtmlOrSubmissionCode() {
        for (dojo in CodeDojo.entries) {
            for ((language, slug) in listOf(Language.Java to "java", Language.GO to "golang")) {
                val html = "<p><font color='red'>10</font><sup>5</sup> &lt;= n; List&lt;Node&gt; */</p>\n"
                val data = LeetCodeChallengeData(
                    questionId = "1", frontendQuestionId = "1", title = "Fixture", titleSlug = "fixture",
                    content = html, isPaidOnly = false, difficulty = "Easy", likes = 0, dislikes = 0,
                    similarQuestions = "[]", exampleTestcaseList = emptyList(), topicTags = emptyList(),
                    codeSnippets = listOf(LeetCodeQuestionCodeSnippet(slug, slug, "fixture code")),
                    hints = emptyList(), testCase = "", metaData = "{}"
                )
                val challenge = challengeTemplate(data, dojo, language, LanguageVersion.AnyVersion)
                val originalHtml = challenge.description
                assertTrue(originalHtml.contains("<font"))
                assertEquals(originalHtml, renderTemplate("\${challenge.description}", language, challenge))
                val rendered = renderTemplate("/*\n\${challenge.descriptionText}*/\n\${challenge.code}", language, challenge)
                assertEquals("/*\n10^5 <= n; List<Node> * /\n*/\n${challenge.getCode()}", rendered)
                assertEquals(originalHtml, challenge.description)
                // 新默认资源包含纯文本题面，但不改变平台代码和提交区域。
                val defaultTemplate = javaClass.getResource("/templates/${dojo.value}/code/${language.fileExt}.ft")!!.readText()
                val defaultCode = renderTemplate(defaultTemplate, language, challenge)
                assertTrue(defaultCode.contains(challenge.getCode()))
                assertTrue(defaultCode.contains("10^5 <= n; List<Node> * /"))
                assertEquals("fixture code", language.extractCodeFromRegion(defaultCode).trim())
            }
        }
    }
}
