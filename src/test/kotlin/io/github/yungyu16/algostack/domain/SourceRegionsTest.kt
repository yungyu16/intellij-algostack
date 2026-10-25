package io.github.yungyu16.algostack.domain

import io.github.yungyu16.algostack.utils.ChallengeFiles
import io.github.yungyu16.algostack.model.Constants
import org.junit.Assert.*
import org.junit.Test

class SourceRegionsTest {
    private val url = "https://leetcode.cn/problems/two-sum/"
    private val languages = listOf(Language.Java, Language.GO)

    @Test fun commentPositionsAndLineEndingsHaveIdenticalIdentityAndExtraction() {
        for (language in languages) {
            val identity = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", language)
            val pkg = if (language == Language.Java) "package practice;" else "package main"
            val imports = if (language == Language.Java) "import java.util.List;" else "import \"fmt\""
            val type = if (language == Language.Java) "class Fixture {" else "type Fixture struct {"
            val method = if (language == Language.Java) "void fixture() {" else "func fixture() {"
            val prefixes = listOf("", "$pkg\n", "$pkg\n$imports\n", "$pkg\n$imports\n$type\n", "$pkg\n$imports\n$method\n")
            for (prefix in prefixes) for (newline in listOf("\n", "\r\n", "\r")) {
                val source = ("\uFEFF" + prefix + "// $url\n" + language.encloseCodeInRegion("// fixture"))
                    .replace("\n", newline)
                assertEquals(identity, ChallengeRecognition.recognize(source, language.fileExt))
                assertEquals("// fixture$newline", language.extractCodeFromRegion(source))
            }
        }
    }

    @Test fun allRealCommentsContributeUrlsButLiteralsDoNot() {
        for (language in languages) {
            val delimiter = if (language == Language.Java) "\"\"\"" else "`"
            val source = "var text = $delimiter\n// https://leetcode.cn/problems/three-sum/\n$delimiter;\n" +
                "var s = \"https://leetcode.com/problems/two-sum/\";\n" +
                "/* [$url](https://www.leetcode.cn/problems/two-sum/solutions/?x=1#answer). */\n" +
                language.encloseCodeInRegion("// fixture")
            val identity = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", language)
            assertEquals(identity, ChallengeRecognition.recognize(source, language.fileExt))
            assertNull(ChallengeRecognition.recognize(source + "\n/* https://leetcode.cn/problems/three-sum/ */", language.fileExt))
            assertNull(ChallengeRecognition.recognize(source + "\n// https://leetcode.com/problems/two-sum/", language.fileExt))
        }
    }

    @Test fun malformedAndAmbiguousRegionsRejectInRecognitionAndExtraction() {
        for (language in Language.entries) {
            val begin = language.createComment(Constants.SUBMIT_CODE_REGION_BEGIN)
            val end = language.createComment(Constants.SUBMIT_CODE_REGION_END)
            val region = language.encloseCodeInRegion("fixture")
            for (invalid in listOf(begin, end, "$end\n$begin", "$begin\n$begin\n$end", "$begin\n$end\n$end",
                region + region, "$begin\n$begin\n$end\n$end", "$end\n$begin\n$end")) {
                val source = language.createComment(" $url") + "\n" + invalid
                assertNull(language.name, ChallengeRecognition.recognize(source, language.fileExt))
                assertEquals(language.name, "", language.extractCodeFromRegion(source))
            }
        }
    }

    @Test fun unambiguousExtensionsRecognizeAndAllLanguagesExtractOneRegion() {
        for (language in Language.entries) {
            val identity = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", language)
            val source = language.createComment(" $url") + "\n" + language.encloseCodeInRegion("fixture")
            if (Language.entries.count { it.fileExt == language.fileExt } == 1)
                assertEquals(language.name, identity, ChallengeRecognition.recognize(source, language.fileExt))
            else
                assertNull(language.name, ChallengeRecognition.recognize(source, language.fileExt))
            assertEquals(language.name, "fixture\n", language.extractCodeFromRegion(source))
        }
    }

    @Test fun fakeRegionsOnBothSidesAndInsideRealCodeNeverChangeSelectedBytes() {
        for (language in languages) {
            val fake = language.encloseCodeInRegion("// fake")
            val delimiter = if (language == Language.Java) "\"\"\"" else "`"
            val literal = "var text = $delimiter\n$fake\n$delimiter;"
            val code = "// fixture\n$literal\n/*\n$fake\n*/"
            val source = "// $url\n$literal\n/*\n$fake\n*/\n" + language.encloseCodeInRegion(code) + "\n$literal"
            assertNotNull(ChallengeRecognition.recognize(source, language.fileExt))
            assertEquals(code + "\n", language.extractCodeFromRegion(source))
        }
    }

    @Test fun urlSubstringsAndUntrustedHostsCannotSupplyIdentity() {
        for (value in listOf("evilhttps://leetcode.cn/problems/two-sum/", "https://evil.test/https://leetcode.cn/problems/two-sum/",
            "https://leetcode.cn.evil.test/problems/two-sum/", "https://leetcode.cn@evil.test/problems/two-sum/")) {
            val source = "// $value\n" + Language.Java.encloseCodeInRegion("fixture")
            assertNull(value, ChallengeRecognition.recognize(source, "java"))
        }
        val source = "// $url\n// https://leetcode.cn.evil.test/problems/three-sum/\n" + Language.Java.encloseCodeInRegion("fixture")
        assertNotNull(ChallengeRecognition.recognize(source, "java"))
    }

    @Test fun compactCommentsAndStringsHaveDistinctTrustBoundaries() {
        for (language in languages) for (comment in listOf("//$url", "/*$url*/", "value(); //$url")) {
            val source = comment + "\n" + language.encloseCodeInRegion("fixture")
            assertNotNull(comment, ChallengeRecognition.recognize(source, language.fileExt))
        }
        for (language in languages) {
            val source = "var s = \"$url\";\n" + language.encloseCodeInRegion("fixture")
            assertNull(ChallengeRecognition.recognize(source, language.fileExt))
        }
    }

    @Test fun inlineMarkersAreNotSubmissionBoundaries() {
        for (language in languages) {
            val source = "// $url\nvalue(); " + language.encloseCodeInRegion("fixture").trimStart()
            assertNull(ChallengeRecognition.recognize(source, language.fileExt))
            assertEquals("", language.extractCodeFromRegion(source))
        }
    }

    @Test fun contentSnapshotsReidentifyWithoutSavedState() {
        val identity = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", Language.Java)
        val saved = "import java.util.List;\n// $url\n" + Language.Java.encloseCodeInRegion("// saved")
        val unsaved = saved.replace("two-sum", "three-sum").replace("// saved", "// unsaved")
        assertEquals(identity, ChallengeFiles.resolve("fixture.java", saved))
        assertEquals("three-sum", ChallengeFiles.resolve("fixture.java", unsaved)?.slug)
        assertEquals("// unsaved\n", Language.Java.extractCodeFromRegion(unsaved))
        assertNull(ChallengeFiles.resolve("fixture.java", unsaved.replace("// https://leetcode.cn/problems/three-sum/", "")))
        assertEquals(identity, ChallengeFiles.resolve("fixture.java", saved))
    }
}
