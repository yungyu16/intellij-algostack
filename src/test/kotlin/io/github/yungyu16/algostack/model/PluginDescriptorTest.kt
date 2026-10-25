package io.github.yungyu16.algostack.model

import org.junit.Assert.*
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

class PluginDescriptorTest {
    @Test fun artifactKeepsAlgoStackNameAndPluginIdentity() {
        val artifact = java.io.File(requireNotNull(System.getProperty("algostack.pluginArtifact")))
        val version = requireNotNull(System.getProperty("algostack.pluginVersion"))
        assertEquals("AlgoStack-$version.jar", artifact.name)
        java.util.jar.JarFile(artifact).use { jar ->
            val document = jar.getInputStream(requireNotNull(jar.getJarEntry("META-INF/plugin.xml"))).use {
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
            }
            assertEquals("com.yungyu.leetcode-editor", document.getElementsByTagName("id").item(0).textContent.trim())
            assertEquals(version, document.getElementsByTagName("version").item(0).textContent.trim())
        }
    }

    @Test fun retiredDatabaseAndHistoryFeaturesAreAbsent() {
        // IDEA 自身携带 org.sqlite.JDBC；这里只核对插件产物，依赖图另由 verifyRuntimeLibraries 检查。
        val artifact = requireNotNull(System.getProperty("algostack.pluginArtifact"))
        java.util.jar.JarFile(artifact).use { jar ->
            val entries = jar.entries().asSequence().map { it.name }.toList()
            for (retired in listOf("org/sqlite/", "org/flywaydb/", "com/wenjunhuang/codeepiphany/storage/",
                "com/wenjunhuang/codeepiphany/settings/", "com/wenjunhuang/codeepiphany/model/template/"))
                assertFalse(retired, entries.any { it.startsWith(retired) })
        }
        assertNull(javaClass.getResource("/db/migration/V001__initial.sql"))
        val xml = javaClass.getResourceAsStream("/META-INF/plugin.xml")!!.bufferedReader().use { it.readText() }
        for (retired in listOf("ActivateNotes", "ActivateSubmissions", "SubmissionCodeEditor", "solution-remark", "submission-code"))
            assertFalse(retired, xml.contains(retired))
        assertTrue(xml.contains("ActivateSolutions"))
        assertTrue(xml.contains("SidebarWindowFactory"))
    }

    @Test fun internationalSettingsTemplatesAndOperationsAreNotPackaged() {
        val artifact = requireNotNull(System.getProperty("algostack.pluginArtifact"))
        java.util.jar.JarFile(artifact).use { jar ->
            val entries = jar.entries().asSequence().map { it.name }.toList()
            assertFalse(entries.any { it.startsWith("templates/leetcode/") })
            assertFalse(entries.any { it.endsWith("/LeetCodeSettings.class") || it.endsWith("/LeetCodeSettingsConfigurable.class") })
            assertFalse(entries.any { it.contains("ugcArticle") || it.endsWith("problemsetQuestionListV2.graphql") })
            assertTrue("templates/leetcodecn/code/java.ft" in entries)
            assertFalse(entries.any { it.endsWith("/PlatformSettings.class") || it.endsWith("/LeetCodeCNSettings.class") || it.endsWith("/CodeEpiphanySettings.class") })
            assertFalse(entries.any { it.startsWith("settings/") || it.contains("SetupRequiredPromptView") })
        }
        val configs = descriptor().getElementsByTagName("configurable")
        assertEquals(0, configs.length)
        assertEquals(0, descriptor().getElementsByTagName("projectConfigurable").length)
    }

    @Test fun customCasesAndProjectPersistenceAreRemoved() {
        val artifact = requireNotNull(System.getProperty("algostack.pluginArtifact"))
        java.util.jar.JarFile(artifact).use { jar ->
            val entries = jar.entries().asSequence().map { it.name }.toList()
            for (retired in listOf("ChallengeSettings", "TestCaseItemPanel", "TestCasesDialog", "TestCasesEditionAction", "ChallengeFileRenameListener"))
                assertFalse(retired, entries.any { it.contains(retired) })
            assertFalse(entries.any { it.endsWith("vialVirus.svg") })
        }
        val xml = javaClass.getResourceAsStream("/META-INF/plugin.xml")!!.bufferedReader().use { it.readText() }
        assertFalse(xml.contains("EditTestCases"))
        assertEquals(0, descriptor().getElementsByTagName("vfs.asyncListener").length)
        assertTrue(xml.contains("AlgoStack.Actions.RunTest"))
        assertTrue(xml.contains("AlgoStack.Actions.SubmitCode"))
    }

    @Test fun keepsJcefDependencyForTheLoginBrowser() {
        val dependencies = descriptor().getElementsByTagName("depends")
        assertTrue((0 until dependencies.length).any { dependencies.item(it).textContent.trim() == "com.intellij.modules.jcef" })
    }

    @Test fun customReferencesFollowTheirRegistrations() {
        val registered = mutableSetOf<String>()
        fun visit(node: org.w3c.dom.Node) {
            val id = node.attributes?.getNamedItem("id")?.nodeValue
            if (id != null) registered += id
            val reference = node.attributes?.getNamedItem("ref")?.nodeValue
            if (reference?.startsWith("AlgoStack.") == true)
                assertTrue("Reference appears before registration: $reference", reference in registered)
            for (index in 0 until node.childNodes.length) visit(node.childNodes.item(index))
        }
        visit(descriptor().getElementsByTagName("actions").item(0))
    }

    private fun descriptor() = javaClass.getResourceAsStream("/META-INF/plugin.xml").use {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
    }
    @Test fun updateNotificationAndPluginMetadataShareReleaseNotes() {
        val notes = javaClass.getResourceAsStream("/META-INF/release-notes.html")!!.bufferedReader().use { it.readText() }
        assertEquals(notes.trim(), descriptor().getElementsByTagName("change-notes").item(0).textContent.trim())
        assertTrue(notes.isNotBlank())
    }
    @Test fun registeredImplementationsCanBeLoaded() {
        val elements = descriptor().getElementsByTagName("*")
        for (index in 0 until elements.length) for (attribute in listOf("class", "implementation", "implementationClass", "factoryClass", "instance")) {
            val name = elements.item(index).attributes.getNamedItem(attribute)?.nodeValue ?: continue
            if (name.startsWith("io.github.yungyu16.algostack.")) Class.forName(name, false, javaClass.classLoader)
        }
    }
    @Test fun customActionReferencesResolve() {
        val document = descriptor()
        val elements = document.getElementsByTagName("*")
        val ids = (0 until elements.length).mapNotNull { elements.item(it).attributes.getNamedItem("id")?.nodeValue }.toSet()
        val references = document.getElementsByTagName("reference")
        val missing = (0 until references.length).map { references.item(it).attributes.getNamedItem("ref").nodeValue }
            .filter { it.startsWith("AlgoStack.") && it !in ids }
        assertEquals(emptyList<String>(), missing)
        assertFalse("AlgoStack.Actions.LeetCode.ChangeUI" in ids)
        assertTrue("AlgoStack.Dojos.Actions.OpenChallengeGroup" in ids)
    }
}
