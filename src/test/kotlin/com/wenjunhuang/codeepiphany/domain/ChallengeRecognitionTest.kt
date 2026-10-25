package com.wenjunhuang.codeepiphany.domain

import com.wenjunhuang.codeepiphany.configuration.ChallengeSettings
import com.wenjunhuang.codeepiphany.presentation.sidebar.SidebarPolicy
import org.junit.Assert.*
import org.junit.Test

class ChallengeRecognitionTest {
    @Test fun existingJavaAndGoAreRecognizedWithoutXmlOrDatabase() {
        for (dojo in CodeDojo.entries) for (language in listOf(Language.Java, Language.GO)) {
            val text = "/*\n * 1. 两数之和\n * https://${dojo.domain}/problems/two-sum/description/\n */\n" +
                (if (language == Language.Java) "package practice;\n" else "package main\n") +
                language.encloseCodeInRegion("// existing answer")
            val result = ChallengeSettings().resolve("/practice/old.${language.fileExt}", text)
            assertEquals(ChallengeIdentity(dojo, "two-sum", language), result?.identity)
            assertTrue(SidebarPolicy.visibleTab("Description", result?.identity))
        }
    }

    @Test fun legacyJavaPackageBeforeProblemCommentEnablesBothContextTabs() {
        // 用户旧文件的最小形状：package、题面块注释、外层类、原有提交区域；不复制题解。
        val text = """
            package leetcodecn;

            /*
            8. 字符串转换整数 (atoi)
            https://leetcode.cn/problems/string-to-integer-atoi/
            */
            public class P0008_StringToIntegerAtoi {
            ${Language.Java.encloseCodeInRegion("class Solution {}")}
            }
        """.trimIndent()
        val result = ChallengeSettings().resolve("legacy/P0008_StringToIntegerAtoi.java", text)
        assertEquals(ChallengeIdentity(CodeDojo.LeetCodeCN, "string-to-integer-atoi", Language.Java), result?.identity)
        assertTrue(SidebarPolicy.visibleTab("Description", result?.identity))
        assertTrue(SidebarPolicy.visibleTab("Solutions", result?.identity))
    }

    @Test fun legacyGoPackageBeforeProblemCommentIsRecognized() {
        val text = "package main\n/* https://leetcode.cn/problems/two-sum/ */\n" +
            Language.GO.encloseCodeInRegion("func twoSum() {}")
        assertEquals(ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", Language.GO),
            ChallengeRecognition.recognize(text, "go"))
    }

    @Test fun acceptsPackageWhitespaceBomAndMetadataOnEitherSide() {
        val declarations = mapOf(
            Language.Java to listOf("package leetcodecn;\n", "\uFEFF \t\r\npackage\tpractice . leetcodecn ;\r\n",
                "package\n练习.题目;", "package\u000cpractice; "),
            Language.GO to listOf("package main\n", "\uFEFF \t\r\npackage\tmain\r\n", "package\n练习;\n")
        )
        for ((language, packages) in declarations) for (declaration in packages) for (before in listOf(true, false)) {
            val identity = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", language, LanguageVersion.SpecificVersion("17"))
            val header = ChallengeRecognition.header(identity)
            // BOM 只能位于文件开头；metadata 与 URL 均可位于 package 前或后。
            val code = if (before) declaration.takeWhile { it == '\uFEFF' } + header + declaration.removePrefix("\uFEFF")
                else declaration + header
            assertEquals("$language, metadataBefore=$before, package=$declaration", identity,
                ChallengeRecognition.recognize(code + language.encloseCodeInRegion("// skeleton"), language.fileExt))
        }
        for (language in listOf(Language.Java, Language.GO)) {
            val declaration = if (language == Language.Java) "package practice;\n" else "package main\n"
            val identity = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", language, LanguageVersion.SpecificVersion("17"))
            val (url, metadata) = ChallengeRecognition.header(identity).trimEnd().lines()
            for ((before, after) in listOf(url to metadata, metadata to url)) {
                assertEquals(identity, ChallengeRecognition.recognize("$before\n$declaration$after\n" +
                    language.encloseCodeInRegion("// skeleton"), language.fileExt))
            }
        }
        val go = "package main // https://leetcode.cn/problems/two-sum/\n" + Language.GO.encloseCodeInRegion("func solve() {}")
        assertEquals("two-sum", ChallengeRecognition.recognize(go, "go")?.slug)
    }

    @Test fun rejectsPackageLookalikesMalformedDeclarationsAndOtherLanguagePreambles() {
        val invalid = mapOf(
            Language.Java to listOf("package", "package leetcodecn", "package 123;", "package a..b;", "package a-b;",
                "package class;", "package a.int;", "package _;", "packageName p;", "package() {}", "package p; package q;",
                "@Deprecated\npackage p;", "package a /* comment */ . b;", "package \\u0061;",
                "package p; String s = \"not a comment\";"),
            Language.GO to listOf("package", "package main.extra", "package 123", "package main()", "package main garbage",
                "package func", "package _", "packageName main", "package main; package other;", "package main; var s = `text`",
                "package main /* same-line block comment */")
        )
        for ((language, declarations) in invalid) for (declaration in declarations) {
            val code = "$declaration\n// https://leetcode.cn/problems/two-sum/\n" + language.encloseCodeInRegion("// skeleton")
            assertNull("$language: $declaration", ChallengeRecognition.recognize(code, language.fileExt))
        }
        assertNull(ChallengeRecognition.recognize("package practice\n// https://leetcode.cn/problems/two-sum/\n" +
            Language.Kotlin.encloseCodeInRegion("fun solve() {}"), "kt"))
    }

    @Test fun importsAndImplementationRemainHeaderBoundaries() {
        for (language in listOf(Language.Java, Language.GO)) {
            val declaration = if (language == Language.Java) "package practice;\n" else "package main\n"
            val imports = if (language == Language.Java) "import java.util.List;\n" else "import \"fmt\"\n"
            val header = "/* https://leetcode.cn/problems/two-sum/ */\n"
            val region = language.encloseCodeInRegion("// skeleton")
            assertEquals("two-sum", ChallengeRecognition.recognize(declaration + header + imports + region, language.fileExt)?.slug)
            assertNull(ChallengeRecognition.recognize(declaration + imports + header + region, language.fileExt))
            val body = if (language == Language.Java) "class Solution {\n" else "func solve() {\n"
            assertNull(ChallengeRecognition.recognize(declaration + body + header + region + "\n}", language.fileExt))
            // 已有头部身份不受 import 后或实现中的其他 URL 干扰；不扫描这些位置。
            val unrelated = "// https://leetcode.cn/problems/three-sum/\n"
            assertEquals("two-sum", ChallengeRecognition.recognize(declaration + header + imports + unrelated + region, language.fileExt)?.slug)
            val string = if (language == Language.Java) "String s = \"https://leetcode.cn/problems/two-sum/\";\n"
                else "var s = `https://leetcode.cn/problems/two-sum/`\n"
            assertNull(ChallengeRecognition.recognize(declaration + string + region, language.fileExt))
        }
    }

    @Test fun packageDoesNotWeakenIdentityOrRegionChecks() {
        for (language in listOf(Language.Java, Language.GO)) {
            val declaration = if (language == Language.Java) "package practice;\n" else "package main\n"
            val identity = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", language)
            val header = ChallengeRecognition.header(identity)
            val region = language.encloseCodeInRegion("// skeleton")
            assertNull(ChallengeRecognition.recognize(header + declaration + "// https://leetcode.cn/problems/three-sum/\n" + region, language.fileExt))
            assertNull(ChallengeRecognition.recognize(header + declaration + "// https://leetcode.com/problems/two-sum/\n" + region, language.fileExt))
            assertNull(ChallengeRecognition.recognize(header + declaration + "// leetcode-editor: language=${language.value}; version=17\n" + region, language.fileExt))
            assertNull(ChallengeRecognition.recognize(declaration + "// https://leetcode.cn.evil.test/problems/two-sum/\n" + region, language.fileExt))
            assertNull(ChallengeRecognition.recognize(declaration + header, language.fileExt))
            assertNull(ChallengeRecognition.recognize(declaration + header + region.lines().reversed().joinToString("\n"), language.fileExt))
        }
    }

    @Test fun rejectsNonQuestionsUntrustedHostsCodeStringsAndMissingOrReversedRegions() {
        val region = Language.Java.encloseCodeInRegion("class Solution {}")
        for (url in listOf("https://leetcode.cn.evil.test/problems/two-sum/", "https://leetcode.cn@evil.test/problems/two-sum/",
            "http://leetcode.cn/problems/two-sum/", "https://leetcode.cn:444/problems/two-sum/", "https://leetcode.cn/problemset/")) {
            assertNull(url, ChallengeRecognition.recognize("// $url\n$region", "java"))
        }
        assertNull(ChallengeRecognition.recognize("/* header */ String url = \"https://leetcode.cn/problems/two-sum/\"\n$region", "java"))
        assertNull(ChallengeRecognition.recognize("val url = \"https://leetcode.cn/problems/two-sum/\"\n$region", "java"))
        assertNull(ChallengeRecognition.recognize("// https://leetcode.cn/problems/two-sum/\nclass Solution {}", "java"))
        assertNull(ChallengeRecognition.recognize("// https://leetcode.cn/problems/two-sum/\n" + region.lines().reversed().joinToString("\n"), "java"))
        assertNull(ChallengeRecognition.recognize("// https://leetcode.cn/problems/two-sum/\n$region", "txt"))
        assertFalse(SidebarPolicy.visibleTab("Description", null))
        assertFalse(SidebarPolicy.visibleTab("Solutions", null))
        assertTrue(SidebarPolicy.visibleTab("Console", null))
        assertTrue(SidebarPolicy.visibleTab("LeetCode", null))
        assertTrue(SidebarPolicy.visibleTab("LeetCodeCN", null))
    }

    @Test fun rejectsAmbiguousProblemHeadersAndPreservesLanguageVersion() {
        val identity = ChallengeIdentity(CodeDojo.LeetCode, "two-sum", Language.Java, LanguageVersion.SpecificVersion("17"))
        val code = ChallengeRecognition.header(identity) + Language.Java.encloseCodeInRegion("class Solution {}")
        assertEquals(identity, ChallengeRecognition.recognize(code, "java"))
        assertNull(ChallengeRecognition.recognize("// https://leetcode.cn/problems/three-sum/\n$code", "java"))
        assertNull(ChallengeRecognition.recognize(code.replace("language=java", "language=go"), "java"))
    }

    @Test fun contentWinsOverStaleMappingAndNeverReusesAnotherQuestionsCases() {
        val settings = ChallengeSettings()
        val identity = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", Language.GO)
        settings.addChallenge("old.go", ChallengeSettings.Association(identity, listOf(ChallengeSettings.TestCase("custom"))))
        val other = identity.copy(slug = "three-sum")
        val code = ChallengeRecognition.header(other) + Language.GO.encloseCodeInRegion("func solve() {}")
        assertEquals(other, settings.resolve("old.go", code)?.identity)
        assertTrue(settings.resolve("old.go", code)!!.testCases.isEmpty())
        assertNull(settings.resolve("old.go", "package unrelated"))
        // 识别不写回关联；没有后台迁移过程。
        assertEquals(identity, settings.findAssociation("old.go")?.identity)
    }
}
