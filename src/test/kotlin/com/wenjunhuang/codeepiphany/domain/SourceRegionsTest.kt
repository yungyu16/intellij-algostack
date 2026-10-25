package com.wenjunhuang.codeepiphany.domain

import com.wenjunhuang.codeepiphany.configuration.ChallengeSettings
import com.wenjunhuang.codeepiphany.model.Constants
import org.junit.Assert.*
import org.junit.Test

class SourceRegionsTest {
    private val url = "https://leetcode.cn/problems/two-sum/"
    private val languages = listOf(Language.Java, Language.GO)

    @Test fun commentPositionsAndLineEndingsHaveIdenticalIdentityAndExtraction() {
        for (language in languages) {
            val identity = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", language, LanguageVersion.SpecificVersion("17"))
            val pkg = if (language == Language.Java) "package practice;" else "package main"
            val imports = if (language == Language.Java) "import java.util.List;" else "import \"fmt\""
            val type = if (language == Language.Java) "class Fixture {" else "type Fixture struct {"
            val method = if (language == Language.Java) "void fixture() {" else "func fixture() {"
            val prefixes = listOf("", "$pkg\n", "$pkg\n$imports\n", "$pkg\n$imports\n$type\n", "$pkg\n$imports\n$method\n")
            for (prefix in prefixes) for (newline in listOf("\n", "\r\n", "\r")) {
                val source = ("\uFEFF" + prefix + ChallengeRecognition.header(identity) + language.encloseCodeInRegion("// fixture"))
                    .replace("\n", newline)
                assertEquals(identity, ChallengeRecognition.recognize(source, language.fileExt))
                assertEquals("// fixture$newline", language.extractCodeFromRegion(source))
            }
        }
    }

    @Test fun allRealCommentsContributeUrlsAndMetadataButLiteralsDoNot() {
        for (language in languages) {
            val metadata = "leetcode-editor: language=${language.value}; version=17"
            val delimiter = if (language == Language.Java) "\"\"\"" else "`"
            val source = "var text = $delimiter\n// https://leetcode.cn/problems/three-sum/\n// leetcode-editor: language=unknown; version=99\n$delimiter;\n" +
                "var s = \"https://leetcode.com/problems/two-sum/\";\n" +
                "/* [$url](https://www.leetcode.cn/problems/two-sum/solutions/?x=1#answer). */\n" +
                language.encloseCodeInRegion("// fixture\n// $metadata")
            val identity = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", language, LanguageVersion.SpecificVersion("17"))
            assertEquals(identity, ChallengeRecognition.recognize(source, language.fileExt))
            assertNull(ChallengeRecognition.recognize(source + "\n// leetcode-editor: language=${language.value}; version=21", language.fileExt))
            assertNull(ChallengeRecognition.recognize(source + "\n/* https://leetcode.cn/problems/three-sum/ */", language.fileExt))
            assertNull(ChallengeRecognition.recognize(source + "\n// https://leetcode.com/problems/two-sum/", language.fileExt))
            assertNull(ChallengeRecognition.recognize(source + "\n// leetcode-editor: language=unknown; version=17", language.fileExt))
            assertEquals(identity, ChallengeRecognition.recognize(source + "\n// $metadata", language.fileExt))
        }
    }

    @Test fun malformedAndAmbiguousRegionsRejectInRecognitionAndExtraction() {
        for (language in Language.entries) {
            val begin = language.createComment(Constants.SUBMIT_CODE_REGION_BEGIN)
            val end = language.createComment(Constants.SUBMIT_CODE_REGION_END)
            val region = language.encloseCodeInRegion("fixture")
            for (invalid in listOf(begin, end, "$end\n$begin", "$begin\n$begin\n$end", "$begin\n$end\n$end",
                region + region, "$begin\n$begin\n$end\n$end", "$end\n$begin\n$end")) {
                val source = ChallengeRecognition.header(ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", language)) + invalid
                assertNull(language.name, ChallengeRecognition.recognize(source, language.fileExt))
                assertEquals(language.name, "", language.extractCodeFromRegion(source))
            }
        }
    }

    @Test fun existingLanguageTemplatesStillRecognizeAndExtractOneRegion() {
        for (language in Language.entries) {
            val identity = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", language)
            val source = ChallengeRecognition.header(identity) + language.encloseCodeInRegion("fixture")
            assertEquals(language.name, identity, ChallengeRecognition.recognize(source, language.fileExt))
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

    @Test fun contentSnapshotsReidentifyWithoutWritingStaleXmlIdentityOrCases() {
        val settings = ChallengeSettings()
        val identity = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", Language.Java)
        settings.addChallenge("fixture.java", ChallengeSettings.Association(identity, listOf(ChallengeSettings.TestCase("custom"))))
        val saved = "import java.util.List;\n// $url\n" + Language.Java.encloseCodeInRegion("// saved")
        val unsaved = saved.replace("two-sum", "three-sum").replace("// saved", "// unsaved")
        assertEquals(identity, settings.resolve("fixture.java", saved)?.identity)
        assertEquals("three-sum", settings.resolve("fixture.java", unsaved)?.slug)
        assertTrue(settings.resolve("fixture.java", unsaved)!!.testCases.isEmpty())
        assertEquals("// unsaved\n", Language.Java.extractCodeFromRegion(unsaved))
        assertNull(settings.resolve("fixture.java", unsaved.replace("// https://leetcode.cn/problems/three-sum/", "")))
        assertEquals("custom", settings.resolve("fixture.java", saved)!!.testCases.single().input)
        assertEquals(identity, settings.findAssociation("fixture.java")?.identity)
    }
}
