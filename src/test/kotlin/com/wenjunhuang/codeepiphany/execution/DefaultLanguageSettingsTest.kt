package com.wenjunhuang.codeepiphany.execution

import com.intellij.util.xmlb.XmlSerializer
import com.wenjunhuang.codeepiphany.configuration.*
import com.wenjunhuang.codeepiphany.domain.*
import com.wenjunhuang.codeepiphany.leetcode.api.model.*
import org.jdom.Element
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class DefaultLanguageSettingsTest {
    private fun restore(settings: PlatformSettings): PlatformSettings = PlatformSettings().apply {
        loadState(XmlSerializer.deserialize(XmlSerializer.serialize(settings.state), PlatformSettings.CodeDojoSettingsState::class.java))
    }

    @Test fun newAndLegacyEmptySettingsInitializeOnceAndExplicitEmptySurvivesReload() {
        val settings = LeetCodeCNSettings()
        val expected = listOf(Language.Java to LanguageVersion.AnyVersion, Language.GO to LanguageVersion.AnyVersion)
        assertEquals(expected, settings.getSelectedLanguages())
        for (xml in listOf(Element("component"), Element("component").addContent(
            Element("option").setAttribute("name", "languageSettings").addContent(Element("list"))))) {
            settings.loadState(XmlSerializer.deserialize(xml, PlatformSettings.CodeDojoSettingsState::class.java))
            assertEquals(expected, settings.getSelectedLanguages())
        }
        assertEquals(expected, restore(settings).getSelectedLanguages())
        // 设置页 Apply 直接替换当前 state 的列表；读取和持久化都不能补回删除项。
        settings.state.languageSettings = arrayListOf()
        assertTrue(settings.getSelectedLanguages().isEmpty())
        assertTrue(restore(settings).getSelectedLanguages().isEmpty())
        assertEquals(2, LeetCodeCNSettings().getSelectedLanguages().size)
    }

    @Test fun legacyCustomJavaOnlyIsNotAugmentedOrRewrittenOnEitherSite() {
        val language = Element("LanguageSettingsState").setAttribute("language", "java")
            .setAttribute("languageVersion", "").setAttribute("sourceFolder", "custom/path")
            .setAttribute("fileNameTemplate", "MyName").setAttribute("codeTemplate", "my template")
        val xml = Element("component").addContent(Element("option").setAttribute("name", "languageSettings")
            .addContent(Element("list").addContent(language)))
            .addContent(Element("option").setAttribute("name", "descriptionCSS").setAttribute("value", "custom css"))
        for (settings in listOf(LeetCodeSettings(), LeetCodeCNSettings())) {
            settings.loadState(XmlSerializer.deserialize(xml, PlatformSettings.CodeDojoSettingsState::class.java))
            for (loaded in listOf(settings, restore(settings))) {
                val only = loaded.state.languageSettings.single()
                assertEquals(Language.Java, only.language)
                assertEquals(LanguageVersion.AnyVersion, only.languageVersion)
                assertEquals("custom/path", only.sourceFolder)
                assertEquals("MyName", only.fileNameTemplate)
                assertEquals("my template", only.codeTemplate)
                assertEquals("custom css", loaded.state.descriptionCSS)
            }
        }
    }

    @Test fun defaultsRenderSiteSpecificPathsPlainTextAndUnchangedSubmissionWithNoEarlyFiles() {
        val root = Files.createTempDirectory("default-templates-")
        try {
            for (dojo in CodeDojo.entries) {
                val settings = if (dojo == CodeDojo.LeetCodeCN) LeetCodeCNSettings() else LeetCodeSettings()
                for ((id, name) in listOf("1" to "P0001_TwoSum", "12345" to "P12345_TwoSum", "Interview 4" to "TwoSum")) {
                    for (setting in settings.state.languageSettings) {
                        val language = setting.language!!
                        val snippet = if (language == Language.Java) "class Solution { public int solve() { return 0; } }" else "func solve() int { return 0 }"
                        val data = LeetCodeChallengeData("9001", id, "Two Sum", "two-sum", "<p>Compare &lt;= 2 */</p>",
                            isPaidOnly = false, difficulty = "Easy", likes = 0, dislikes = 0, similarQuestions = "[]",
                            exampleTestcaseList = emptyList(), topicTags = emptyList(), hints = emptyList(), testCase = "", metaData = "{}",
                            codeSnippets = listOf(LeetCodeQuestionCodeSnippet("fixture", if (language == Language.Java) "java" else "golang", snippet)))
                        val folder = setting.sourceFolder!!.replace("\$PROJECT_DIR\$", root.toString())
                        val prepared = renderChallenge(data, dojo, language, LanguageVersion.AnyVersion,
                            GenerationSettings(folder, setting.fileNameTemplate!!, setting.codeTemplate!!))
                        val expected = if (language == Language.Java) "code/java/src/main/java/${dojo.value}/$name.java"
                            else "code/go/${dojo.value}/$name/main.go"
                        assertEquals(root.resolve(expected).toFile(), prepared.file)
                        assertFalse(prepared.file.exists())
                        assertFalse(Files.exists(root.resolve("code")))
                        assertTrue(prepared.code.contains("Compare <= 2 * /"))
                        assertFalse(prepared.code.contains("<p>"))
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
