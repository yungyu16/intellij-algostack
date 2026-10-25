package io.github.yungyu16.algostack.model

import org.junit.Assert.*
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

class PluginDescriptorTest {
    @Test
    fun artifactKeepsAlgoStackNameAndPluginIdentity() {
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

    @Test
    fun retiredDatabaseAndHistoryFeaturesAreAbsent() {
        // IDEA 自身携带 org.sqlite.JDBC；这里只核对插件产物，依赖图另由 verifyRuntimeLibraries 检查。
        val artifact = requireNotNull(System.getProperty("algostack.pluginArtifact"))
        java.util.jar.JarFile(artifact).use { jar ->
            val entries = jar.entries().asSequence().map { it.name }.toList()
            for (retired in listOf(
                "org/sqlite/", "org/flywaydb/", "com/wenjunhuang/codeepiphany/storage/",
                "com/wenjunhuang/codeepiphany/settings/", "com/wenjunhuang/codeepiphany/model/template/"
            ))
                assertFalse(retired, entries.any { it.startsWith(retired) })
        }
        assertNull(javaClass.getResource("/db/migration/V001__initial.sql"))
        val xml = javaClass.getResourceAsStream("/META-INF/plugin.xml")!!.bufferedReader().use { it.readText() }
        for (retired in listOf(
            "ActivateNotes",
            "ActivateSubmissions",
            "SubmissionCodeEditor",
            "solution-remark",
            "submission-code"
        ))
            assertFalse(retired, xml.contains(retired))
        assertTrue(xml.contains("ActivateSolutions"))
        assertTrue(xml.contains("SidebarWindowFactory"))
    }

    @Test
    fun internationalSettingsTemplatesAndOperationsAreNotPackaged() {
        val artifact = requireNotNull(System.getProperty("algostack.pluginArtifact"))
        java.util.jar.JarFile(artifact).use { jar ->
            val entries = jar.entries().asSequence().map { it.name }.toList()
            assertFalse(entries.any { it.startsWith("templates/leetcode/") })
            assertFalse(entries.any { it.endsWith("/LeetCodeSettings.class") || it.endsWith("/LeetCodeSettingsConfigurable.class") })
            assertFalse(entries.any { it.contains("ugcArticle") || it.endsWith("problemsetQuestionListV2.graphql") })
            assertTrue("templates/leetcodecn/code/java.ft" in entries)
            assertFalse(entries.any {
                it.endsWith("/PlatformSettings.class") || it.endsWith("/LeetCodeCNSettings.class") || it.endsWith(
                    "/CodeEpiphanySettings.class"
                )
            })
            assertFalse(entries.any { it.startsWith("settings/") || it.contains("SetupRequiredPromptView") })
        }
        val configs = descriptor().getElementsByTagName("configurable")
        assertEquals(0, configs.length)
        assertEquals(0, descriptor().getElementsByTagName("projectConfigurable").length)
    }

    @Test
    fun customCasesAndProjectPersistenceAreRemoved() {
        val artifact = requireNotNull(System.getProperty("algostack.pluginArtifact"))
        java.util.jar.JarFile(artifact).use { jar ->
            val entries = jar.entries().asSequence().map { it.name }.toList()
            for (retired in listOf(
                "ChallengeSettings",
                "TestCaseItemPanel",
                "TestCasesDialog",
                "TestCasesEditionAction",
                "ChallengeFileRenameListener"
            ))
                assertFalse(retired, entries.any { it.contains(retired) })
            assertFalse(entries.any { it.endsWith("vialVirus.svg") })
        }
        val xml = javaClass.getResourceAsStream("/META-INF/plugin.xml")!!.bufferedReader().use { it.readText() }
        assertFalse(xml.contains("EditTestCases"))
        assertEquals(0, descriptor().getElementsByTagName("vfs.asyncListener").length)
        assertTrue(xml.contains("AlgoStack.Actions.RunTest"))
        assertTrue(xml.contains("AlgoStack.Actions.SubmitCode"))
    }

    @Test
    fun packagedResourcesKeepSupportedTemplatesAndExcludeRetiredAssets() {
        val artifact = requireNotNull(System.getProperty("algostack.pluginArtifact"))
        java.util.jar.JarFile(artifact).use { jar ->
            val entries = jar.entries().asSequence().filterNot { it.isDirectory }.map { it.name }.toSet()
            val templates = setOf("code/java.ft", "code/go.ft", "filename/java.ft", "filename/go.ft")
                .map { "templates/leetcodecn/$it" }.toSet()
            assertEquals(templates, entries.filter { it.startsWith("templates/") }.toSet())
            for (name in listOf("building", "console", "filter", "search", "vialVirus")) {
                for (suffix in listOf("", "_dark")) {
                    val path = "icons/$name$suffix.svg"
                    assertFalse(path, path in entries)
                }
            }
            assertFalse("META-INF/pluginIcon.png" in entries)
            assertFalse("graphql/problemsetCompanyTags_cn.graphql" in entries)
            // 暗色变体由平台隐式加载，不能因缺少字面量引用而删除。
            for (name in listOf(
                "app-brackets",
                "book",
                "login",
                "logout",
                "run",
                "solution",
                "submit",
                "dojos/leetcodecn"
            )) {
                for (suffix in listOf("", "_dark")) {
                    val path = "icons/$name$suffix.svg"
                    assertTrue(path, path in entries)
                }
            }
            for (path in listOf(
                "META-INF/pluginIcon.svg", "icons/pluginIcon.svg", "icons/languages/java.svg", "icons/languages/go.svg",
                "graphql/questionCompanyTagsV2_cn.graphql"
            ))
                assertTrue(path, path in entries)
        }
    }

    @Test
    fun allGraphqlResourcesLoadFromTheFlattenedDirectory() {
        val expected = setOf(
            "discussTopic_cn.graphql",
            "favoriteQuestionList_cn.graphql",
            "globalData_cn.graphql",
            "problemsetPositionTags_cn.graphql",
            "problemsetQuestionList_cn.graphql",
            "questionCompanyTagsV2_cn.graphql",
            "questionData.graphql",
            "questionTagTypeWithTags_cn.graphql",
            "questionTopicsList_cn.graphql",
            "solutionTags_cn.graphql"
        ).map { "graphql/$it" }.toSet()
        val artifact = requireNotNull(System.getProperty("algostack.pluginArtifact"))
        java.util.jar.JarFile(artifact).use { jar ->
            val entries = jar.entries().asSequence().map { it.name }.toSet()
            assertEquals(expected, entries.filter { it.endsWith(".graphql") }.toSet())
            assertFalse(entries.any { it.startsWith("leetcode/graphql/") })
            for (path in expected) {
                val bundled =
                    jar.getInputStream(requireNotNull(jar.getJarEntry(path))).bufferedReader().use { it.readText() }
                assertTrue(path, bundled.isNotBlank())
                val loaded = requireNotNull(javaClass.getResourceAsStream("/$path")) { "Missing resource: $path" }
                    .bufferedReader().use { it.readText() }
                assertEquals(path, bundled, loaded)
                assertNull(javaClass.getResource("/leetcode/$path"))
            }
        }
    }

    @Test
    fun descriptorClassesAndAllFoldingRegistrationsResolve() {
        val document = descriptor()
        val folding = document.getElementsByTagName("lang.foldingBuilder")
        assertEquals(16, folding.length)
        for (index in 0 until folding.length) {
            val name = folding.item(index).attributes.getNamedItem("implementationClass").nodeValue
            assertEquals("io.github.yungyu16.algostack.presentation.editor.SubmissionRegionFoldingBuilder", name)
            assertTrue(
                com.intellij.lang.folding.FoldingBuilderEx::class.java.isAssignableFrom(
                    Class.forName(name, false, javaClass.classLoader)
                )
            )
        }
        val nodes = document.getElementsByTagName("*")
        for (index in 0 until nodes.length) {
            val attributes = nodes.item(index).attributes
            for (attribute in listOf("class", "implementation", "implementationClass", "factoryClass")) {
                val name = attributes.getNamedItem(attribute)?.nodeValue ?: continue
                Class.forName(name, false, javaClass.classLoader)
            }
        }
    }

    @Test
    fun packagedBundlesExcludeRetiredKeysAndKeepMatchingKeySets() {
        val retired = setOf(
            "background.runningTest", "background.submittingCode", "console.loggedIn", "console.loggingIn",
            "console.loginCancelled", "needFetchQuestions.tips", "query.cancelled", "query.challenge.title",
            "query.progress.title", "query.solution.title", "leetcode.submissionResult.code.text",
            "leetcode.submissionResult.memory.text", "leetcode.submissionResult.runtime.text",
            "leetcode.submissionResult.submitDateTime.text", "leetcode.submissionResult.testcases.text",
            "group.AlgoStack.Editor.Menu.login"
        )
        val artifact = requireNotNull(System.getProperty("algostack.pluginArtifact"))
        java.util.jar.JarFile(artifact).use { jar ->
            val keySets = listOf("", "_zh").map { suffix ->
                val path = "messages/PluginBundle$suffix.properties"
                val properties = java.util.Properties()
                jar.getInputStream(requireNotNull(jar.getJarEntry(path))).reader(Charsets.UTF_8)
                    .use { properties.load(it) }
                properties.stringPropertyNames().also { keys ->
                    for (key in retired) assertFalse("$path: $key", key in keys)
                    for (key in listOf(
                        "group.AlgoStack.Editor.Menu.text", "group.AlgoStack.Editor.Menu.description",
                        "action.AlgoStack.Actions.RunTest.text", "challenge.status.solved", "submissionResult.success"
                    ))
                        assertTrue("$path: $key", key in keys)
                }
            }
            assertEquals(keySets[0], keySets[1])
        }
    }

    @Test
    fun javaPsiDependencyIsDeclaredForTemplateStubs() {
        val dependencies = descriptor().getElementsByTagName("depends")
        assertTrue((0 until dependencies.length).any { dependencies.item(it).textContent.trim() == "com.intellij.java" })
    }

    @Test
    fun providersHidingDefaultEditorSupportDumbMode() {
        val registrations = descriptor().getElementsByTagName("fileEditorProvider")
        assertTrue("No editor providers checked", registrations.length > 0)
        for (index in 0 until registrations.length) {
            val name = registrations.item(index).attributes.getNamedItem("implementation").nodeValue
            // 只构造 Provider，不创建编辑器或启动 JCEF；核对 XML 实际注册项而非手写类清单。
            val provider = Class.forName(name).getDeclaredConstructor()
                .newInstance() as com.intellij.openapi.fileEditor.FileEditorProvider
            if (provider.policy == com.intellij.openapi.fileEditor.FileEditorPolicy.HIDE_DEFAULT_EDITOR) {
                assertTrue(
                    "$name hides the default editor but is not DumbAware",
                    provider is com.intellij.openapi.project.DumbAware
                )
            }
        }
    }

    @Test
    fun keepsJcefDependencyForTheLoginBrowser() {
        val dependencies = descriptor().getElementsByTagName("depends")
        assertTrue((0 until dependencies.length).any { dependencies.item(it).textContent.trim() == "com.intellij.modules.jcef" })
    }

    @Test
    fun customReferencesFollowTheirRegistrations() {
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

    @Test
    fun updateNotificationAndPluginMetadataShareReleaseNotes() {
        val notes =
            javaClass.getResourceAsStream("/META-INF/release-notes.html")!!.bufferedReader().use { it.readText() }
        assertEquals(notes.trim(), descriptor().getElementsByTagName("change-notes").item(0).textContent.trim())
        assertTrue(notes.isNotBlank())
    }

    @Test
    fun registeredImplementationsCanBeLoaded() {
        val elements = descriptor().getElementsByTagName("*")
        for (index in 0 until elements.length) for (attribute in listOf(
            "class",
            "implementation",
            "implementationClass",
            "factoryClass",
            "instance"
        )) {
            val name = elements.item(index).attributes.getNamedItem(attribute)?.nodeValue ?: continue
            if (name.startsWith("io.github.yungyu16.algostack.")) Class.forName(name, false, javaClass.classLoader)
        }
    }

    @Test
    fun customActionReferencesResolve() {
        val document = descriptor()
        val elements = document.getElementsByTagName("*")
        val ids =
            (0 until elements.length).mapNotNull { elements.item(it).attributes.getNamedItem("id")?.nodeValue }.toSet()
        val references = document.getElementsByTagName("reference")
        val missing = (0 until references.length).map { references.item(it).attributes.getNamedItem("ref").nodeValue }
            .filter { it.startsWith("AlgoStack.") && it !in ids }
        assertEquals(emptyList<String>(), missing)
        assertFalse("AlgoStack.Actions.LeetCode.ChangeUI" in ids)
        assertTrue("AlgoStack.Dojos.Actions.OpenChallengeGroup" in ids)
    }
}
