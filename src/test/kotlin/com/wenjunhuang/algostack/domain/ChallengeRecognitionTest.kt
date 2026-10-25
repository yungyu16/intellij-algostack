package com.wenjunhuang.algostack.domain

import com.wenjunhuang.algostack.configuration.ChallengeSettings
import com.wenjunhuang.algostack.presentation.sidebar.SidebarPolicy
import org.junit.Assert.*
import org.junit.Test

class ChallengeRecognitionTest {
    @Test fun defaultProblemsAndConsoleRemainWhenContextualTabsAreHidden() {
        val identity = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", Language.Java)
        assertEquals(listOf("Console", "Problems"), SidebarPolicy.visibleTabs(null))
        assertEquals(listOf("Console", "Problems", "Description", "Solutions"), SidebarPolicy.visibleTabs(identity))
        assertFalse(SidebarPolicy.visibleTab("LeetCodeCN", identity))
        assertFalse(SidebarPolicy.visibleTab("LeetCode", null))
        // 切工具窗口 tab 不写入 identity；普通编辑器产生 null 后只移除题目页。
        assertEquals(listOf("Console", "Problems"), SidebarPolicy.visibleTabs(null))
    }

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
        // 只读核对旧 Go 文件：package main、题面块注释、独占行提交区域；不复制用户解法。
        val text = "package main\n\n/*\n1. 两数之和\nhttps://leetcode.cn/problems/two-sum/\n*/\n" +
            Language.GO.encloseCodeInRegion("func twoSum() {}")
        assertEquals(ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", Language.GO),
            ChallengeRecognition.recognize(text, "go"))
    }

    @Test fun acceptsPackageWhitespaceBomAndUrlOnEitherSide() {
        val declarations = mapOf(
            Language.Java to listOf("package leetcodecn;\n", "\uFEFF \t\r\npackage\tpractice . leetcodecn ;\r\n",
                "package\n练习.题目;", "package\u000cpractice; "),
            Language.GO to listOf("package main\n", "\uFEFF \t\r\npackage\tmain\r\n", "package\n练习;\n")
        )
        for ((language, packages) in declarations) for (declaration in packages) for (before in listOf(true, false)) {
            val identity = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", language)
            val header = language.createComment(" https://leetcode.cn/problems/two-sum/") + "\n"
            // BOM 只能位于文件开头；URL 可位于 package 前或后。
            val code = if (before) declaration.takeWhile { it == '\uFEFF' } + header + declaration.removePrefix("\uFEFF")
                else declaration + header
            assertEquals("$language, urlBefore=$before, package=$declaration", identity,
                ChallengeRecognition.recognize(code + language.encloseCodeInRegion("// skeleton"), language.fileExt))
        }
        val go = "package main // https://leetcode.cn/problems/two-sum/\n" + Language.GO.encloseCodeInRegion("func solve() {}")
        assertEquals("two-sum", ChallengeRecognition.recognize(go, "go")?.slug)
    }

    @Test fun recognitionDoesNotPretendToValidatePackageSyntax() {
        // 不再把 package/import 当作扫描边界，也不验证源文件可编译性。
        for (declaration in listOf("@Deprecated\npackage practice;", "package a /* comment */ . b;", "package malformed")) {
            val code = "$declaration\n// https://leetcode.cn/problems/two-sum/\n" +
                Language.Java.encloseCodeInRegion("class Solution {}")
            assertEquals("two-sum", ChallengeRecognition.recognize(code, "java")?.slug)
        }
    }

    @Test fun importsAndImplementationDoNotLimitCommentPositions() {
        for (language in listOf(Language.Java, Language.GO)) {
            val declaration = if (language == Language.Java) "package practice;\n" else "package main\n"
            val imports = if (language == Language.Java) "import java.util.List;\n" else "import \"fmt\"\n"
            val header = "/* https://leetcode.cn/problems/two-sum/ */\n"
            val region = language.encloseCodeInRegion("// skeleton")
            assertEquals("two-sum", ChallengeRecognition.recognize(declaration + header + imports + region, language.fileExt)?.slug)
            assertEquals("two-sum", ChallengeRecognition.recognize(declaration + imports + header + region, language.fileExt)?.slug)
            val body = if (language == Language.Java) "class Solution {\n" else "func solve() {\n"
            assertEquals("two-sum", ChallengeRecognition.recognize(declaration + body + header + region + "\n}", language.fileExt)?.slug)
            // 全文件真实注释都参与身份冲突检查。
            val unrelated = "// https://leetcode.cn/problems/three-sum/\n"
            assertNull(ChallengeRecognition.recognize(declaration + header + imports + unrelated + region, language.fileExt))
            val string = if (language == Language.Java) "String s = \"https://leetcode.cn/problems/two-sum/\";\n"
                else "var s = `https://leetcode.cn/problems/two-sum/`\n"
            assertNull(ChallengeRecognition.recognize(declaration + string + region, language.fileExt))
        }
    }

    @Test fun packageDoesNotWeakenIdentityOrRegionChecks() {
        for (language in listOf(Language.Java, Language.GO)) {
            val declaration = if (language == Language.Java) "package practice;\n" else "package main\n"
            val identity = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", language)
            val header = language.createComment(" https://leetcode.cn/problems/two-sum/") + "\n"
            val region = language.encloseCodeInRegion("// skeleton")
            assertNull(ChallengeRecognition.recognize(header + declaration + "// https://leetcode.cn/problems/three-sum/\n" + region, language.fileExt))
            assertNull(ChallengeRecognition.recognize(header + declaration + "// https://leetcode.com/problems/two-sum/\n" + region, language.fileExt))
            assertEquals(identity, ChallengeRecognition.recognize(header + declaration + "// ordinary comment\n" + region, language.fileExt))
            assertNull(ChallengeRecognition.recognize(declaration + "// https://leetcode.cn.evil.test/problems/two-sum/\n" + region, language.fileExt))
            assertNull(ChallengeRecognition.recognize(declaration + header, language.fileExt))
            assertNull(ChallengeRecognition.recognize(declaration + header + region.lines().reversed().joinToString("\n"), language.fileExt))
        }
    }

    @Test fun rejectsNonQuestionsUntrustedHostsCodeStringsAndMissingOrReversedRegions() {
        val region = Language.Java.encloseCodeInRegion("class Solution {}")
        for (url in listOf("https://leetcode.cn.evil.test/problems/two-sum/", "https://leetcode.cn@evil.test/problems/two-sum/",
            "http://leetcode.cn/problems/two-sum/", "https://leetcode.cn:444/problems/two-sum/",
            "https://leetcode.cn:443/problems/two-sum/", "https://user@leetcode.cn/problems/two-sum/", "https://leetcode.cn/problemset/")) {
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
        assertTrue(SidebarPolicy.visibleTab("Problems", null))
    }

    @Test fun internationalHeadersCannotUseChineseAssociations() {
        val settings = ChallengeSettings()
        settings.addChallenge("old.java", ChallengeSettings.Association(ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", Language.Java)))
        for (host in listOf("leetcode.com", "www.leetcode.com")) {
            val code = "// https://$host/problems/two-sum/\n" + Language.Java.encloseCodeInRegion("class Solution {}")
            assertNull(ChallengeRecognition.recognize(code, "java"))
            assertNull(settings.resolve("old.java", code))
        }
    }

    @Test fun acceptsUrlSuffixesAndCommonMarkdownPunctuation() {
        for (host in listOf("leetcode.cn", "www.leetcode.cn")) for (suffix in listOf(
            "", "/", "/description/", "/solutions/123/title/", "/submissions/42/", "/arbitrary/path",
            "?envType=study-plan-v2&envId=top-100-liked", "/?a=b#description", "/#题目", "/.", "/,", "/;", "/*", "/)", "/\"", "/>", "/!", "/。", "]", "，", "。", "!", "）"
        )) {
            val text = "// https://$host/problems/two-sum$suffix\n" + Language.Java.encloseCodeInRegion("class Solution {}")
            assertEquals(suffix, "two-sum", ChallengeRecognition.recognize(text, "java")?.slug)
        }
        for (suffix in listOf("@", ":", "%invalid")) {
            val text = "// https://leetcode.cn/problems/two-sum$suffix\n" + Language.Java.encloseCodeInRegion("class Solution {}")
            assertNull(suffix, ChallengeRecognition.recognize(text, "java"))
        }
        // 相同 slug 的不同子页并不歧义；不验证子页是否真实存在。
        val text = "// https://leetcode.cn/problems/two-sum/description/\n// https://www.leetcode.cn/problems/two-sum/solutions/\n" +
            Language.Java.encloseCodeInRegion("class Solution {}")
        assertEquals("two-sum", ChallengeRecognition.recognize(text, "java")?.slug)
    }

    @Test fun rejectsRegionsInsideMultilineLiteralsOrBlockComments() {
        for (language in listOf(Language.Java, Language.GO)) {
            val region = language.encloseCodeInRegion("// not submission code")
            val delimiter = if (language == Language.Java) "\"\"\"" else "`"
            val literal = if (language == Language.Java) "String s = " else "var s = "
            val header = "// https://leetcode.cn/problems/two-sum/\n"
            for (fake in listOf("$literal$delimiter\n$region\n$delimiter", "/*\n$region\n*/")) {
                assertNull(language.toString(), ChallengeRecognition.recognize(header + fake, language.fileExt))
                val source = header + fake + "\n" + language.encloseCodeInRegion("// actual code")
                assertEquals("two-sum", ChallengeRecognition.recognize(source, language.fileExt)?.slug)
                assertEquals("// actual code\n", language.extractCodeFromRegion(source))
            }
        }
    }

    @Test fun literalEscapesAndCommentsDoNotHideRealRegions() {
        for (language in listOf(Language.Java, Language.GO)) {
            val header = "\uFEFF/* https://leetcode.cn/problems/two-sum/?x=1#description */\r\n"
            val declaration = if (language == Language.Java) "package practice;\n" else "package main\n"
            val literals = "// quotes in comment: \" ' ` /*\n/* quotes in block: \" ' ` */\n" +
                "var s = \"escaped \\\" quote and // text\";\nvar c = '\\\'';\n"
            val code = header + declaration + literals + language.encloseCodeInRegion("// skeleton")
            assertEquals("two-sum", ChallengeRecognition.recognize(code.replace("\n", "\r\n"), language.fileExt)?.slug)
        }
    }

    @Test fun extensionAndUniqueProblemUrlDetermineIdentityWithAnyVersion() {
        val code = "// https://leetcode.cn/problems/two-sum/\n" + Language.Java.encloseCodeInRegion("class Solution {}")
        val identity = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", Language.Java)
        assertEquals(identity, ChallengeRecognition.recognize(code, "java"))
        assertEquals(identity.copy(language = Language.GO), ChallengeRecognition.recognize(code, "go"))
        assertNull(ChallengeRecognition.recognize("// https://leetcode.cn/problems/three-sum/\n$code", "java"))
    }

    @Test fun contentWinsOverStaleMappingAndNeverReusesAnotherQuestionsCases() {
        val settings = ChallengeSettings()
        val identity = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", Language.GO)
        settings.addChallenge("old.go", ChallengeSettings.Association(identity, listOf(ChallengeSettings.TestCase("custom"))))
        val other = identity.copy(slug = "three-sum")
        val code = "// https://leetcode.cn/problems/three-sum/\n" + Language.GO.encloseCodeInRegion("func solve() {}")
        assertEquals(other, settings.resolve("old.go", code)?.identity)
        assertTrue(settings.resolve("old.go", code)!!.testCases.isEmpty())
        assertNull(settings.resolve("old.go", "package unrelated"))
        // 识别不写回关联；没有后台迁移过程。
        assertEquals(identity, settings.findAssociation("old.go")?.identity)
    }
}
