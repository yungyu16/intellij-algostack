package io.github.yungyu16.algostack.execution

import io.github.yungyu16.algostack.domain.*
import io.github.yungyu16.algostack.leetcode.api.model.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class DefaultLanguageSettingsTest {
    @Test fun onlyJavaAndGoAnyVersionAreOffered() {
        assertEquals(listOf(Language.Java to LanguageVersion.AnyVersion, Language.GO to LanguageVersion.AnyVersion), ChallengeConventions.languages)
        for ((language, version) in listOf(Language.Python to LanguageVersion.AnyVersion, Language.Java to LanguageVersion.SpecificVersion("17"))) {
            assertThrows(IllegalArgumentException::class.java) {
                ChallengeConventions.generation(Path.of("/unused"), CodeDojo.LeetCodeCN, language, version)
            }
        }
    }

    @Test fun generationValidatesRenderedUrlAndRegionWithoutAddingAHeader() {
        for ((language, version) in ChallengeConventions.languages) {
            val snippet = if (language == Language.Java) "class Solution {}" else "func solve() {}"
            val slug = language.toLeetCode(version)
            val data = LeetCodeChallengeData("9001", "1", "Two Sum", "two-sum", "<p>Fixture</p>",
                isPaidOnly = false, difficulty = "Easy", likes = 0, dislikes = 0, similarQuestions = "[]",
                exampleTestcaseList = emptyList(), topicTags = emptyList(), hints = emptyList(), testCase = "", metaData = "{}",
                codeSnippets = listOf(LeetCodeQuestionCodeSnippet(slug, slug, snippet)))
            val url = "// https://leetcode.cn/problems/two-sum/\n"
            val region = language.encloseCodeInRegion(snippet)
            val valid = "// template-owned\n$url$region"
            val setting = GenerationSettings("/unused", "Fixture", valid)
            assertEquals(valid, renderChallenge(data, CodeDojo.LeetCodeCN, language, version, setting).code)
            for (invalid in listOf(region, url, valid.replace("two-sum", "three-sum"), valid + region,
                "var url = \"https://leetcode.cn/problems/two-sum/\";\n$region")) {
                assertThrows(IllegalArgumentException::class.java) {
                    renderChallenge(data, CodeDojo.LeetCodeCN, language, version, setting.copy(code = invalid))
                }
            }
            // 固定 AnyVersion 必须精确匹配实际 API slug，不能悄悄选另一版本的签名。
            val otherVersion = data.copy(codeSnippets = listOf(LeetCodeQuestionCodeSnippet("fixture", "${slug}17", snippet)))
            assertThrows(IllegalArgumentException::class.java) {
                renderChallenge(otherVersion, CodeDojo.LeetCodeCN, language, version, setting)
            }
        }
    }

    @Test fun legacyPreferencesCannotChangeConventionsAndRemainUntouched() {
        val root = Files.createTempDirectory("retired-preferences-")
        try {
            val xml = root.resolve(".idea/CodeEpiphany/leetcodecn.xml")
            Files.createDirectories(xml.parent)
            val old = """<component><option name="descriptionCSS" value="custom css"/><option name="languageSettings"><list><LanguageSettingsState language="java" sourceFolder="custom/path" fileNameTemplate="OldName" codeTemplate="old code"/></list></option></component>"""
            Files.writeString(xml, old)
            val setting = ChallengeConventions.generation(root, CodeDojo.LeetCodeCN, Language.Java)
            assertEquals(root.resolve("code/java/src/main/java/leetcodecn").toString(), setting.folder)
            assertFalse(setting.fileName.contains("OldName"))
            assertFalse(setting.code.contains("old code"))
            assertEquals(2, ChallengeConventions.languages.size)
            assertEquals(old, Files.readString(xml))
            assertFalse(Files.exists(root.resolve("code")))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun defaultsRenderSiteSpecificPathsPlainTextAndUnchangedSubmissionWithNoEarlyFiles() {
        val root = Files.createTempDirectory("default-templates-")
        try {
            for (dojo in CodeDojo.entries) {
                for ((id, name) in listOf("1" to "P0001_TwoSum", "12345" to "P12345_TwoSum", "Interview 4" to "TwoSum")) {
                    for ((language, version) in ChallengeConventions.languages) {
                        val setting = ChallengeConventions.generation(root, dojo, language, version)
                        val snippet = if (language == Language.Java) "class Solution { public int solve() { return 0; } }" else "func solve() int { return 0 }"
                        val data = LeetCodeChallengeData("9001", id, "Two Sum", "two-sum", "<p>Compare &lt;= 2 */</p>",
                            isPaidOnly = false, difficulty = "Easy", likes = 0, dislikes = 0, similarQuestions = "[]",
                            exampleTestcaseList = emptyList(), topicTags = emptyList(), hints = emptyList(), testCase = "", metaData = "{}",
                            codeSnippets = listOf(LeetCodeQuestionCodeSnippet("fixture", if (language == Language.Java) "java" else "golang", snippet)))
                        val prepared = renderChallenge(data, dojo, language, LanguageVersion.AnyVersion, setting)
                        val expected = if (language == Language.Java) "code/java/src/main/java/${dojo.value}/$name.java"
                            else "code/go/${dojo.value}/$name/main.go"
                        assertEquals(root.resolve(expected).toFile(), prepared.file)
                        assertFalse(prepared.file.exists())
                        assertFalse(Files.exists(root.resolve("code")))
                        assertTrue(prepared.code.contains("Compare <= 2 * /"))
                        assertFalse(prepared.code.contains("<p>"))
                        assertFalse(prepared.code.contains("leetcode-editor:"))
                        assertEquals(renderTemplate(setting.code, language, prepared.template), prepared.code)
                        assertEquals(snippet, language.extractCodeFromRegion(prepared.code).trim())
                        assertEquals(1, Regex("Submit Code Region Begin").findAll(prepared.code).count())
                        assertEquals(1, Regex("https://").findAll(prepared.code).count())
                        assertEquals(prepared.identity, ChallengeRecognition.recognize(prepared.code, language.fileExt))
                        assertEquals("9001", prepared.template.questionId)
                        assertEquals(id, prepared.template.frontendQuestionId)
                        if (language == Language.Java) {
                            assertTrue(prepared.code.contains("public class $name"))
                            // 只在测试临时目录编译；默认配置与 renderChallenge 本身不写文件。
                            val source = root.resolve("$name.java")
                            Files.writeString(source, prepared.code)
                            val compiler = ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "javac").toString(),
                                "--release", "8", "-Xlint:-options", "-d", root.toString(), source.toString())
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
