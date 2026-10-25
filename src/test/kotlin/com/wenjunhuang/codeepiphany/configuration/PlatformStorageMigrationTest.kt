package com.wenjunhuang.codeepiphany.configuration

import com.intellij.util.xmlb.XmlSerializer
import com.wenjunhuang.codeepiphany.model.Constants
import com.wenjunhuang.codeepiphany.presentation.sidebar.SidebarPolicy
import org.jdom.Element
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class PlatformStorageMigrationTest {
    @Test fun stableTemplatesRemainInIdeaAndQueryPreferencesAreNotPersisted() {
        assertEquals("CodeEpiphany/leetcodecn.xml", Constants.LEETCODE_CN_SETTING_FILE)
        assertEquals("CodeEpiphany/leetcode.xml", Constants.LEETCODE_SETTING_FILE)
        val legacy = Element("component").addContent(Element("option").setAttribute("name", "queryCriteria")
            .setAttribute("value", "old filters"))
        val state = XmlSerializer.deserialize(legacy, PlatformSettings.CodeDojoSettingsState::class.java)
        val serialized = org.jdom.output.XMLOutputter().outputString(XmlSerializer.serialize(state))
        assertFalse(serialized.contains("queryCriteria"))
        assertFalse(PlatformSettings.CodeDojoSettingsState::class.java.declaredFields.any { it.name == "queryCriteria" })
    }

    @Test fun rootMarkerAcceptsFileOrDirectoryWithoutWritingAnything() {
        val root = Files.createTempDirectory("leetcode-marker")
        try {
            assertFalse(SidebarPolicy.enabled(root.toString()))
            val marker = Files.createFile(root.resolve(".leetcode"))
            assertTrue(SidebarPolicy.enabled(root.toString()))
            Files.delete(marker); Files.createDirectory(marker)
            assertTrue(SidebarPolicy.enabled(root.toString()))
            assertEquals(1, root.toFile().listFiles()!!.size)
            assertFalse(SidebarPolicy.enabled(null))
        } finally { root.toFile().deleteRecursively() }
    }
}
