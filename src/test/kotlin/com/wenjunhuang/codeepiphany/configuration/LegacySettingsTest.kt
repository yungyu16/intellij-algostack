package com.wenjunhuang.codeepiphany.configuration

import com.intellij.openapi.components.State
import com.intellij.util.xmlb.XmlSerializer
import com.wenjunhuang.codeepiphany.domain.*
import com.wenjunhuang.codeepiphany.model.Constants
import org.jdom.Element
import org.junit.Assert.*
import org.junit.Test

class LegacySettingsTest {
    @Test fun directoryMovesAndDeletesKeepFileAssociationsConsistent() {
        val settings = ChallengeSettings()
        val item = ChallengeSettings.Association(1, 2, CodeDojo.LeetCodeCN, Language.Java, 3, emptyList(), emptyList())
        settings.addChallenge("/code/old/A.java", item)
        settings.addChallenge("/code/old/nested/B.java", item)
        settings.addChallenge("/code/old-neighbor/C.java", item)
        settings.relocatePaths("/code/old", "/code/new", true)
        assertNull(settings.findChallengeId("/code/old/A.java"))
        assertNotNull(settings.findChallengeId("/code/new/nested/B.java"))
        assertNotNull(settings.findChallengeId("/code/old-neighbor/C.java"))
        settings.relocatePaths("/code/new", null, true)
        assertNull(settings.findChallengeId("/code/new/A.java"))
        assertNotNull(settings.findChallengeId("/code/old-neighbor/C.java"))
    }

    private fun languageElement() = Element("LanguageSettingsState")
        .setAttribute("sourceFolder", "\$PROJECT_DIR\$/code/java/src/main/java/leetcodecn")
        .setAttribute("language", "java").setAttribute("languageVersion", "")
        .setAttribute("fileNameTemplate", "P\${challenge.frontendQuestionId}_\${velocity.camelCaseName(\$challenge.slug)}")
        .setAttribute("codeTemplate", "class Solution {\n}\n")

    @Test fun readsAndWritesTheExistingLanguageAttributes() {
        val element = languageElement()
        val state = XmlSerializer.deserialize(element, PlatformSettings.LanguageSettingsState::class.java)
        assertEquals(Language.Java, state.language)
        assertEquals(LanguageVersion.AnyVersion, state.languageVersion)
        assertEquals(element.getAttributeValue("fileNameTemplate"), state.fileNameTemplate)
        val output = XmlSerializer.serialize(state)
        for (name in listOf("sourceFolder", "language", "languageVersion", "fileNameTemplate", "codeTemplate")) {
            assertEquals(name, element.getAttributeValue(name), output.getAttributeValue(name))
        }
    }

    @Test fun readsTheExistingListAndRetainsOtherSettings() {
        val element = Element("component").addContent(Element("option").setAttribute("name", "languageSettings")
            .addContent(Element("list").addContent(languageElement())))
        val settings = XmlSerializer.deserialize(element, PlatformSettings.CodeDojoSettingsState::class.java)
        assertEquals(1, settings.languageSettings.size)
        assertEquals(Language.Java, settings.languageSettings.single().language)
    }

    @Test fun readsLegacyFileAssociationsIncludingDirectCollectionTags() {
        val element = javaClass.getResourceAsStream("/legacy/challenge.xml").use {
            org.jdom.input.SAXBuilder().build(it).rootElement
        }
        val state = XmlSerializer.deserialize(element, ChallengeSettings.ChallengeSettingsState::class.java)
        val settings = ChallengeSettings().apply { loadState(state) }
        val association = settings.findChallengeId("/fixture/P0001_TwoSum.java")!!
        assertEquals(1234567890123456789L, association.challengeId)
        assertEquals(CodeDojo.LeetCodeCN, association.dojo)
        assertEquals("[1,2]", association.testCases.single().input)
        assertEquals("[0,1]", association.defaultTestCases.single().expectedOutput)
        val restored = XmlSerializer.deserialize(XmlSerializer.serialize(state), ChallengeSettings.ChallengeSettingsState::class.java)
        assertEquals(association, ChallengeSettings().apply { loadState(restored) }.findChallengeId("/fixture/P0001_TwoSum.java"))
    }

    @Test fun stateComponentNamesAndStorageFilesStayStable() {
        val global = CodeEpiphanySettings::class.java.getAnnotation(State::class.java)
        assertEquals(Constants.SETTING, global.name)
        assertEquals(Constants.SETTING_FILE, global.storages.single().value)
        assertEquals(Constants.LEETCODE_CN_SETTING, LeetCodeCNSettings::class.java.getAnnotation(State::class.java).name)
        assertEquals(Constants.CHALLENGE_SETTING, ChallengeSettings::class.java.getAnnotation(State::class.java).name)
    }

    @Test fun fileAssociationsAreReadAsIndependentSnapshotsAndRenamedAtomically() {
        val settings = ChallengeSettings()
        settings.addChallenge("old.java", ChallengeSettings.Association(1, 2, CodeDojo.LeetCodeCN, Language.Java, 3,
            listOf(ChallengeSettings.TestCase("[1,2]", "[0,1]")), emptyList()))
        val first = settings.findChallengeId("old.java")!!
        first.testCases.single().input = "changed"
        assertEquals("[1,2]", settings.findChallengeId("old.java")!!.testCases.single().input)
        settings.modifyCodeFilePath("old.java", "new.java")
        assertNull(settings.findChallengeId("old.java"))
        assertEquals(3L, settings.findChallengeId("new.java")!!.solutionId)
    }
}
