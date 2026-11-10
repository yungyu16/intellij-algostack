package io.github.yungyu16.algostack.execution

import io.github.yungyu16.algostack.domain.*
import io.github.yungyu16.algostack.leetcode.model.LeetCodeChallengeData
import io.github.yungyu16.algostack.leetcode.model.LeetCodeQuestionCodeSnippet
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class DefaultLanguageSettingsTest {
    companion object {
        @org.junit.BeforeClass
        @JvmStatic
        fun initializeApplication() {
            com.intellij.testFramework.TestApplicationManager.getInstance()
        }
    }

    @Test
    fun onlyJavaAndGoAreOffered() {
        assertEquals(listOf(Language.Java, Language.GO), ChallengeConventions.languages)
    }

    @Test
    fun challengeTemplateMapsPlatformFieldsAndFallsBackWhenTranslationsAreAbsent() {
        for (language in ChallengeConventions.languages) {
            val snippet = if (language == Language.Java) "class Solution {}" else "func solve() {}"
            val slug = language.toLeetCode()
            val data = LeetCodeChallengeData(
                "9001", "26", "Two Sum", "two-sum", "<p>Original</p>",
                translatedTitle = "两数之和", translatedContent = "<p>中文题面</p>",
                difficulty = "Easy", exampleTestcaseList = emptyList(),
                codeSnippets = listOf(LeetCodeQuestionCodeSnippet(slug, slug, snippet))
            )
            val template = challengeTemplate(data, CodeDojo.LeetCodeCN, language)
            assertEquals("9001", template.questionId)
            assertEquals("26", template.frontendQuestionId)
            assertEquals("two-sum", template.slug)
            assertEquals("两数之和", template.name)
            assertEquals("easy", template.difficulty)
            assertEquals("leetcodecn", template.getCodeDojo())
            assertEquals(language.value, template.getLanguage())
            assertSame(data, template.content)
            assertTrue(template.description.contains("<p>中文题面</p>"))
            assertFalse(template.description.contains("Original"))
            assertEquals(if (language == Language.GO) goTemplateStub(snippet) else snippet, language.extractCodeFromRegion(template.getCode()).trim())
            assertEquals("26. 两数之和", template.getHeadingText())
            if (language == Language.GO) assertTrue(template.getCode().contains("panic(\"Not implemented\")"))
            val unsafeTitle = challengeTemplate(data.copy(translatedTitle = "标题 */\r\n下一行"), CodeDojo.LeetCodeCN, language)
            assertEquals("26. 标题 * /  下一行", unsafeTitle.getHeadingText())
            assertEquals(
                "9001/26/two-sum",
                renderTemplate("{{questionId}}/{{frontendQuestionId}}/{{slug}}", template)
            )
            for (translation in listOf(null, "")) {
                val fallback = challengeTemplate(
                    data.copy(translatedTitle = translation, translatedContent = translation),
                    CodeDojo.LeetCodeCN, language
                )
                assertEquals("Two Sum", fallback.name)
                assertEquals("26. Two Sum", fallback.getHeadingText())
                assertTrue(fallback.description.contains("<p>Original</p>"))
            }
        }
    }

    @Test
    fun generationValidatesRenderedUrlAndRegionWithoutAddingAHeader() {
        for (language in ChallengeConventions.languages) {
            val snippet = if (language == Language.Java) "class Solution {}" else "func solve() { return }"
            val slug = language.toLeetCode()
            val data = LeetCodeChallengeData(
                "9001", "1", "Two Sum", "two-sum", "<p>Fixture</p>",
                difficulty = "Easy",
                exampleTestcaseList = emptyList(),
                codeSnippets = listOf(LeetCodeQuestionCodeSnippet(slug, slug, snippet))
            )
            val url = "// https://leetcode.cn/problems/two-sum/\n"
            val region = language.encloseCodeInRegion(snippet)
            val valid = "// template-owned\n$url$region"
            val setting = GenerationSettings("/unused", "Fixture", valid)
            assertEquals(if (language == Language.Java) formatGeneratedJava("Fixture.java", valid) else valid,
                renderChallenge(data, CodeDojo.LeetCodeCN, language, setting).code)
            for (invalid in listOf(
                region, url, valid.replace("two-sum", "three-sum"), valid + region,
                "var url = \"https://leetcode.cn/problems/two-sum/\";\n$region"
            )) {
                assertThrows(IllegalArgumentException::class.java) {
                    renderChallenge(data, CodeDojo.LeetCodeCN, language, setting.copy(code = invalid))
                }
            }
            // 平台签名必须精确匹配内置 slug，不能悄悄选另一带版本号的签名。
            val otherVersion =
                data.copy(codeSnippets = listOf(LeetCodeQuestionCodeSnippet("fixture", "${slug}17", snippet)))
            assertThrows(IllegalArgumentException::class.java) {
                renderChallenge(otherVersion, CodeDojo.LeetCodeCN, language, setting)
            }
        }
    }

    @Test
    fun legacyPreferencesCannotChangeConventionsAndRemainUntouched() {
        val root = Files.createTempDirectory("retired-preferences-")
        try {
            val xml = root.resolve(".idea/CodeEpiphany/leetcodecn.xml")
            Files.createDirectories(xml.parent)
            val old =
                """<component><option name="descriptionCSS" value="custom css"/><option name="languageSettings"><list><LanguageSettingsState language="java" sourceFolder="custom/path" fileNameTemplate="OldName" codeTemplate="old code"/></list></option></component>"""
            Files.writeString(xml, old)
            val setting = ChallengeConventions.generation(root, CodeDojo.LeetCodeCN, Language.Java)
            assertEquals(root.resolve("code/java/src/main/java/leetcodecn").toString(), setting.folder)
            assertFalse(setting.fileName.contains("OldName"))
            assertFalse(setting.code.contains("old code"))
            assertEquals(old, Files.readString(xml))
            assertFalse(Files.exists(root.resolve("code")))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun defaultsRenderSiteSpecificPathsPlainTextAndUnchangedSubmissionWithNoEarlyFiles() {
        val root = Files.createTempDirectory("default-templates-")
        try {
            for (dojo in CodeDojo.entries) {
                for ((id, javaName, goName) in listOf(
                    Triple("1", "P0001_TwoSum", "p0001_two_sum"),
                    Triple("12345", "P12345_TwoSum", "p12345_two_sum"),
                    Triple("Interview 4", "TwoSum", "two_sum")
                )) {
                    for (language in ChallengeConventions.languages) {
                        val setting = ChallengeConventions.generation(root, dojo, language)
                        val snippet =
                            if (language == Language.Java) "class Solution { public int solve() { return 0; } }" else "func solve() int { return 0 }"
                        val data = LeetCodeChallengeData(
                            "9001", id, "Two Sum", "two-sum", "<p>Compare &lt;= 2 */</p>",
                            difficulty = "Easy",
                            exampleTestcaseList = emptyList(),
                            codeSnippets = listOf(
                                LeetCodeQuestionCodeSnippet(
                                    "fixture",
                                    if (language == Language.Java) "java" else "golang",
                                    snippet
                                )
                            )
                        )
                        val prepared = renderChallenge(data, dojo, language, setting)
                        val expected = if (language == Language.Java) "code/java/src/main/java/${dojo.value}/$javaName.java"
                        else "code/go/${dojo.value}/$goName/main.go"
                        assertEquals(root.resolve(expected).toFile(), prepared.file)
                        assertFalse(prepared.file.exists())
                        assertFalse(Files.exists(root.resolve("code")))
                        assertTrue(prepared.code.contains("/*\n$id. Two Sum\n@leetcode https://leetcode.cn/problems/two-sum/\n\nCompare <= 2 * /"))
                        assertFalse(prepared.code.contains("<p>"))
                        assertFalse(prepared.code.contains("leetcode-editor:"))
                        val extracted = language.extractCodeFromRegion(prepared.code)
                        assertEquals(if (language == Language.Java) formatGeneratedJava("Solution.java", snippet).trim() else snippet,
                            if (language == Language.Java) extracted.trimIndent().trim() else extracted.trim())
                        assertEquals(1, Regex("Submit Code Region Begin").findAll(prepared.code).count())
                        assertEquals(1, Regex("https://").findAll(prepared.code).count())
                        assertEquals(prepared.identity, ChallengeRecognition.recognize(prepared.code, language.fileExt))
                        if (language == Language.Java) {
                            assertTrue(prepared.code.contains("public class $javaName"))
                            // 只在测试临时目录编译；默认配置与 renderChallenge 本身不写文件。
                            val source = root.resolve("$javaName.java")
                            Files.writeString(source, prepared.code)
                            val compiler = ProcessBuilder(
                                Path.of(System.getProperty("java.home"), "bin", "javac").toString(),
                                "--release", "8", "-Xlint:-options", "-d", root.toString(), source.toString()
                            )
                                .redirectErrorStream(true).start()
                            val output = compiler.inputStream.bufferedReader().use { it.readText() }
                            assertEquals(output, 0, compiler.waitFor())
                        } else assertTrue(prepared.code.contains("package main"))
                    }
                }
            }
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
