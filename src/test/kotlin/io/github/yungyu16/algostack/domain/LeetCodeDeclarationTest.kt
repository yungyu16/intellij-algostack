package io.github.yungyu16.algostack.domain

import io.github.yungyu16.algostack.utils.ChallengeFiles
import org.junit.Assert.*
import org.junit.Test

class LeetCodeDeclarationTest {
    private val mainUrl = "https://leetcode.cn/problems/find-pivot-index/"
    private val reference = "https://leetcode.cn/problems/find-the-middle-index-in-array/"
    private val languages = listOf(Language.Java, Language.GO)

    @Test
    fun declarationSelectsIdentityAndOrdinaryRelatedLinksDoNotCompete() {
        for (language in languages) for (newline in listOf("\n", "\r\n", "\r")) {
            val region = language.encloseCodeInRegion("// user code")
            val source = ("\uFEFF/*\n724. 寻找数组的中心下标\n@leetcode $mainUrl\n\n" +
                "参考：$reference\nhttps://leetcode.com/problems/another-reference/\n*/\n$region")
                .replace("\n", newline)
            val expected = ChallengeIdentity(CodeDojo.LeetCodeCN, "find-pivot-index", language)
            assertEquals(expected, ChallengeRecognition.recognize(source, language.fileExt))
            assertEquals(expected, ChallengeFiles.resolve("renamed.${language.fileExt}", source))
            assertEquals("// user code$newline", language.extractCodeFromRegion(source))
        }
    }

    @Test
    fun malformedOrConflictingDeclarationsNeverFallBackToLegacyUrl() {
        for (language in languages) {
            val region = language.encloseCodeInRegion("// user code")
            for (line in listOf(
                "@leetcode", "@leetcode invalid", "@leetcode $mainUrl extra", "@leetcode=$mainUrl",
                "@leetcode-problem $mainUrl", "@LEETCODE $mainUrl",
                "@leetcode http://leetcode.cn/problems/find-pivot-index/",
                "@leetcode https://leetcode.com/problems/find-pivot-index/",
                "@leetcode https://leetcode.cn.evil.test/problems/find-pivot-index/",
                "@leetcode https://user@leetcode.cn/problems/find-pivot-index/",
                "@leetcode https://leetcode.cn:443/problems/find-pivot-index/",
                "@leetcode https://leetcode.cn/problemset/",
                "@leetcode $mainUrl\n@leetcode $reference"
            )) {
                val source = "/*\n$mainUrl\n$line\n*/\n$region"
                assertNull(line, ChallengeRecognition.recognize(source, language.fileExt))
            }
            val duplicate = "// @leetcode $mainUrl\n// @leetcode $mainUrl\n$region"
            assertNull(ChallengeRecognition.recognize(duplicate, language.fileExt))
        }
    }

    @Test
    fun declarationMustBeACommentLineAndCannotBypassRegionValidation() {
        for (language in languages) {
            val region = language.encloseCodeInRegion("// user code")
            for (comment in listOf("// @leetcode $mainUrl", "/* @leetcode $mainUrl */", "/**\n * @leetcode $mainUrl\n */")) {
                assertNotNull(ChallengeRecognition.recognize("$comment\n$region", language.fileExt))
                assertNull(ChallengeRecognition.recognize(comment, language.fileExt))
                assertNull(ChallengeRecognition.recognize("$comment\n$region$region", language.fileExt))
                assertNull(ChallengeRecognition.recognize("$comment\n" + region.lines().reversed().joinToString("\n"), language.fileExt))
            }
            val delimiter = if (language == Language.Java) "\"\"\"" else "`"
            for (literal in listOf("var s = \"// @leetcode $mainUrl\";", "var s = $delimiter\n// @leetcode $mainUrl\n$delimiter;")) {
                assertNull(ChallengeRecognition.recognize("$literal\n$region", language.fileExt))
                assertEquals("find-pivot-index", ChallengeRecognition.recognize("// $mainUrl\n$literal\n$region", language.fileExt)?.slug)
            }
            val prose = "// example: @leetcode $reference\n"
            assertNull(ChallengeRecognition.recognize("$prose// $mainUrl\n$region", language.fileExt))
        }
    }

    @Test
    fun recognitionRejectionsHaveControlledDistinctReasons() {
        val region = Language.Java.encloseCodeInRegion("// user code")
        val cases = listOf(
            "// @leetcode invalid\n$region" to RecognitionFailure.INVALID_DECLARATION,
            "// @leetcode $mainUrl\n// @leetcode $reference\n$region" to RecognitionFailure.CONFLICTING_DECLARATIONS,
            "// $mainUrl\n// $reference\n$region" to RecognitionFailure.CONFLICTING_URLS,
            "// @leetcode $mainUrl" to RecognitionFailure.INVALID_REGION,
            "// ordinary comment\n$region" to RecognitionFailure.MISSING_URL,
            "// https://leetcode.com/problems/two-sum/\n$region" to RecognitionFailure.INTERNATIONAL_URL
        )
        for ((source, reason) in cases) {
            val result = ChallengeRecognition.inspect(source, "java")
            assertNull(result.identity)
            assertEquals(reason, result.failure)
            assertFalse(reason.description.contains("private-response"))
        }
    }

    @Test
    fun legacyAmbiguityStaysStrictAndUpdatedContentChangesIdentity() {
        for (language in languages) {
            val region = language.encloseCodeInRegion("// user code")
            assertNull(ChallengeRecognition.recognize("// $mainUrl\n// $reference\n$region", language.fileExt))
            val saved = "// @leetcode $mainUrl\n$region"
            val edited = saved.replace(mainUrl, reference)
            assertEquals("find-pivot-index", ChallengeFiles.resolve("old.${language.fileExt}", saved)?.slug)
            assertEquals("find-the-middle-index-in-array", ChallengeFiles.resolve("old.${language.fileExt}", edited)?.slug)
        }
    }
}
