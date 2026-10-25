package com.wenjunhuang.algostack.configuration

import com.intellij.openapi.components.State
import com.intellij.util.xmlb.XmlSerializer
import com.wenjunhuang.algostack.domain.*
import com.wenjunhuang.algostack.model.Constants
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

    @Test fun xmlNeverProvesIdentityOrSuppliesLanguageVersion() {
        for (language in listOf(Language.Java, Language.GO)) for (slug in listOf("", "two-sum")) {
            val path = "old.${language.fileExt}"
            val original = ChallengeSettings().apply {
                state.challenges[path] = ChallengeSettings.ChallengeSettingsStateItem().also {
                    it.dojo = CodeDojo.LeetCodeCN; it.slug = slug; it.language = language
                    it.languageVersion = LanguageVersion.SpecificVersion("17")
                    it.testCases.add(ChallengeSettings.TestCase("custom", "keep"))
                }
            }
            // 通过真实 XML 序列化契约加载，不依赖用户路径或本地数据。
            val settings = ChallengeSettings().apply {
                loadState(XmlSerializer.deserialize(XmlSerializer.serialize(original.state), ChallengeSettings.ChallengeSettingsState::class.java))
            }
            val region = language.encloseCodeInRegion("// skeleton")
            val identity = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", language)
            assertNull(settings.resolve(path, region))
            val urlOnly = "// https://leetcode.cn/problems/two-sum/\n$region"
            assertEquals(identity, settings.resolve(path, urlOnly)?.identity)
            assertTrue(settings.resolve(path, urlOnly)!!.testCases.isEmpty())
            assertNull(settings.resolve(path, urlOnly.replace("leetcode.cn", "evil.test")))
            settings.state.challenges[path]!!.languageVersion = LanguageVersion.AnyVersion
            assertEquals("custom", settings.resolve(path, urlOnly)!!.testCases.single().input)
            // XML 语言不匹配也不能污染已由扩展名确认的身份或用例。
            settings.state.challenges[path]!!.language = if (language == Language.Java) Language.GO else Language.Java
            assertEquals(identity, settings.resolve(path, urlOnly)?.identity)
            assertTrue(settings.resolve(path, urlOnly)!!.testCases.isEmpty())
        }
    }

    @Test fun retiredInternationalXmlValuesAreIgnoredWithoutMigration() {
        val association = Element("ChallengeSettingsStateItem")
            .addContent(Element("option").setAttribute("name", "dojo").setAttribute("value", "leetcode"))
            .addContent(Element("option").setAttribute("name", "slug").setAttribute("value", "two-sum"))
            .addContent(Element("option").setAttribute("name", "language").setAttribute("value", "java"))
        val item = XmlSerializer.deserialize(association, ChallengeSettings.ChallengeSettingsStateItem::class.java)
        assertNull(item.dojo)
        val settings = ChallengeSettings().apply { state.challenges["old.java"] = item }
        assertNull(settings.findAssociation("old.java"))
        assertNull(ConfigConverters.CodeDojoConverter().fromString("leetcode"))
    }

    @Test fun stateComponentNamesAndStorageFilesStayStable() {
        assertEquals(Constants.CHALLENGE_SETTING, ChallengeSettings::class.java.getAnnotation(State::class.java).name)
    }

    @Test fun newIdentityRoundTripRetainsAnyVersionWithoutLocalIds() {
        val settings = ChallengeSettings()
        val identity = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", Language.Java)
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
