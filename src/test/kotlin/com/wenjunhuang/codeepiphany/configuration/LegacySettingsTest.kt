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
        val item = ChallengeSettings.Association(ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", Language.Java), emptyList(), emptyList())
        settings.addChallenge("/code/old/A.java", item)
        settings.addChallenge("/code/old/nested/B.java", item)
        settings.addChallenge("/code/old-neighbor/C.java", item)
        settings.relocatePaths("/code/old", "/code/new", true)
        assertNull(settings.findAssociation("/code/old/A.java"))
        assertNotNull(settings.findAssociation("/code/new/nested/B.java"))
        assertNotNull(settings.findAssociation("/code/old-neighbor/C.java"))
        settings.relocatePaths("/code/new", null, true)
        assertNull(settings.findAssociation("/code/new/A.java"))
        assertNotNull(settings.findAssociation("/code/old-neighbor/C.java"))
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
        val content = "/* https://leetcode.cn/problems/two-sum/ */\n" + Language.Java.encloseCodeInRegion("class Solution {}")
        assertNull(settings.findAssociation("/fixture/P0001_TwoSum.java"))
        val association = settings.resolve("/fixture/P0001_TwoSum.java", content)!!
        assertEquals("two-sum", association.slug)
        assertEquals(CodeDojo.LeetCodeCN, association.dojo)
        assertEquals("[1,2]", association.testCases.single().input)
        assertEquals("[0,1]", association.defaultTestCases.single().expectedOutput)
        val restored = XmlSerializer.deserialize(XmlSerializer.serialize(state), ChallengeSettings.ChallengeSettingsState::class.java)
        assertEquals(association, ChallengeSettings().apply { loadState(restored) }.resolve("/fixture/P0001_TwoSum.java", content))
    }

    @Test fun stateComponentNamesAndStorageFilesStayStable() {
        val global = CodeEpiphanySettings::class.java.getAnnotation(State::class.java)
        assertEquals(Constants.SETTING, global.name)
        assertEquals(Constants.SETTING_FILE, global.storages.single().value)
        assertEquals(Constants.LEETCODE_CN_SETTING, LeetCodeCNSettings::class.java.getAnnotation(State::class.java).name)
        assertEquals(Constants.CHALLENGE_SETTING, ChallengeSettings::class.java.getAnnotation(State::class.java).name)
    }

    @Test fun newIdentityRoundTripRetainsLanguageVersionWithoutLocalIds() {
        val settings = ChallengeSettings()
        val identity = ChallengeIdentity(CodeDojo.LeetCode, "two-sum", Language.Java, LanguageVersion.SpecificVersion("17"))
        settings.addChallenge("new.java", ChallengeSettings.Association(identity))
        val xml = XmlSerializer.serialize(settings.state)
        val restored = ChallengeSettings().apply { loadState(XmlSerializer.deserialize(xml, ChallengeSettings.ChallengeSettingsState::class.java)) }
        assertEquals(identity, restored.findAssociation("new.java")?.identity)
        assertFalse(org.jdom.output.XMLOutputter().outputString(xml).contains("challengeId"))
    }

    @Test fun fileAssociationsAreReadAsIndependentSnapshotsAndRenamedAtomically() {
        val settings = ChallengeSettings()
        settings.addChallenge("old.java", ChallengeSettings.Association(ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", Language.Java),
            listOf(ChallengeSettings.TestCase("[1,2]", "[0,1]")), emptyList()))
        val first = settings.findAssociation("old.java")!!
        first.testCases.single().input = "changed"
        assertEquals("[1,2]", settings.findAssociation("old.java")!!.testCases.single().input)
        settings.modifyCodeFilePath("old.java", "new.java")
        assertNull(settings.findAssociation("old.java"))
        assertEquals(Language.Java, settings.findAssociation("new.java")!!.language)
    }
}
