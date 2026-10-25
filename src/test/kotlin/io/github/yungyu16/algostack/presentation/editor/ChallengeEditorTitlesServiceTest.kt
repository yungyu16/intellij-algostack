package io.github.yungyu16.algostack.presentation.editor

import com.intellij.mock.Mock
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.TestApplicationManager
import com.intellij.util.ui.UIUtil
import io.github.yungyu16.algostack.domain.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.SwingUtilities

/** 使用真实 provider、service、文档 multicaster；只替代编辑器窗口管理，不发起网络请求。 */
class ChallengeEditorTitlesServiceTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun initializeApplication() {
            TestApplicationManager.getInstance()
        }
    }

    @Test
    fun providerDoesNotLookUpServiceUntilProjectHasAFileOrDirectoryMarker() = withFixture { fixture ->
        val provider = ChallengeEditorTabTitleProvider()
        val file = LightVirtualFile("ordinary.txt")
        repeat(3) { assertNull(provider.getEditorTabTitle(fixture.project, file)) }
        assertEquals(0, fixture.project.titleServiceReads)

        Files.createFile(fixture.marker)
        assertNull(provider.getEditorTabTitle(fixture.project, file))
        assertEquals(1, fixture.project.titleServiceReads)
        Files.delete(fixture.marker)
        Files.createDirectory(fixture.marker)
        assertNull(provider.getEditorTabTitle(fixture.project, file))
        assertEquals(2, fixture.project.titleServiceReads)
        Files.delete(fixture.marker)
        assertNull(provider.getEditorTabTitle(fixture.project, file))
        assertEquals(2, fixture.project.titleServiceReads)
    }

    @Test
    fun unopenedFileIsFilteredBeforeQueueingEvenIfOpenedBeforeQueueDrains() = withFixture { fixture ->
        Files.createFile(fixture.marker)
        val file = LightVirtualFile("other-project.java", source("two-sum"))
        val document = fixture.document(file)
        fixture.replace(document, source("three-sum"))
        // 若在过滤前已经排队，回调执行时文件打开会造成一次多余刷新。
        fixture.manager.openedFiles.add(file)
        UIUtil.dispatchAllInvocationEvents()
        assertTrue(fixture.manager.presentations.isEmpty())
    }

    @Test
    fun ordinaryDocumentsAndUnchangedChallengeIdentityDoNotRefresh() = withFixture { fixture ->
        Files.createFile(fixture.marker)
        val ordinary = LightVirtualFile("notes.txt", "notes")
        fixture.manager.openedFiles.add(ordinary)
        val notes = fixture.document(ordinary)
        repeat(3) { fixture.replace(notes, "notes $it") }
        UIUtil.dispatchAllInvocationEvents()
        assertTrue(fixture.manager.presentations.isEmpty())

        val challenge = LightVirtualFile("solution.java", source("two-sum"))
        fixture.manager.openedFiles.add(challenge)
        val solution = fixture.document(challenge)
        assertNull(ChallengeEditorTabTitleProvider().getEditorTabTitle(fixture.project, challenge))
        repeat(3) { fixture.replace(solution, source("two-sum") + "\n// edit $it") }
        UIUtil.dispatchAllInvocationEvents()
        assertTrue(fixture.manager.presentations.isEmpty())

        fixture.replace(solution, source("three-sum"))
        fixture.replace(solution, source("two-sum"))
        UIUtil.dispatchAllInvocationEvents()
        assertEquals(listOf(challenge, challenge), fixture.manager.presentations)
        fixture.manager.presentations.clear()
        fixture.replace(solution, "class Solution {}")
        UIUtil.dispatchAllInvocationEvents()
        assertEquals(listOf(challenge), fixture.manager.presentations)
        fixture.manager.presentations.clear()
        fixture.replace(solution, "class Solution { /* no identity */ }")
        UIUtil.dispatchAllInvocationEvents()
        assertTrue(fixture.manager.presentations.isEmpty())
    }

    @Test
    fun closeSuppressesQueuedPresentationAndReopenObservesIdentityAgain() = withFixture { fixture ->
        Files.createFile(fixture.marker)
        val file = LightVirtualFile("solution.java", source("two-sum"))
        fixture.manager.openedFiles.add(file)
        val document = fixture.document(file)
        fixture.replace(document, source("three-sum"))
        fixture.manager.openedFiles.remove(file)
        val publisher = fixture.project.messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER)
        publisher.fileClosed(fixture.manager, file)
        UIUtil.dispatchAllInvocationEvents()
        assertTrue(fixture.manager.presentations.isEmpty())
        fixture.manager.openedFiles.add(file)
        publisher.fileOpened(fixture.manager, file)
        UIUtil.dispatchAllInvocationEvents()
        assertEquals(listOf(file), fixture.manager.presentations)
    }

    private fun source(slug: String) =
        "// https://leetcode.cn/problems/$slug/\n" + Language.Java.encloseCodeInRegion("class Solution {}")

    private fun withFixture(test: (Fixture) -> Unit) {
        SwingUtilities.invokeAndWait {
            val root = Files.createTempDirectory("algostack-editor-titles-")
            val disposable = Disposer.newDisposable()
            try {
                test(Fixture(root, disposable))
            } finally {
                Disposer.dispose(disposable)
                UIUtil.dispatchAllInvocationEvents()
                Files.deleteIfExists(root.resolve(".leetcode"))
                Files.delete(root)
            }
        }
    }

    private class TitleProject(root: Path, disposable: Disposable) : MockProject(null, disposable) {
        private val rootPath = root.toString()
        var titleServiceReads = 0
        override fun getBasePath(): String = rootPath
        override fun <T : Any?> getService(serviceClass: Class<T>): T? {
            if (serviceClass == ChallengeEditorTitles::class.java) titleServiceReads++
            return super.getService(serviceClass)
        }
    }

    private class RecordingFileEditorManager : Mock.MyFileEditorManager() {
        val openedFiles = mutableSetOf<VirtualFile>()
        val presentations = mutableListOf<VirtualFile>()
        override fun isFileOpen(file: VirtualFile): Boolean = file in openedFiles
        override fun updateFilePresentation(file: VirtualFile) {
            presentations.add(file)
        }
    }

    private class Fixture(root: Path, disposable: Disposable) {
        val marker: Path = root.resolve(".leetcode")
        val project = TitleProject(root, disposable)
        val manager = RecordingFileEditorManager()

        init {
            project.registerService(FileEditorManager::class.java, manager)
            // 请求仍经过真实缓存和取消逻辑，但取消的父 Job 保证不访问真实站点。
            val scope = CoroutineScope(Job().apply { cancel() })
            val service = ChallengeEditorTitles(project, scope)
            project.registerService(ChallengeEditorTitles::class.java, service)
            Disposer.register(disposable, service)
        }

        fun document(file: VirtualFile): Document = ReadAction.compute<Document, RuntimeException> {
            FileDocumentManager.getInstance().getDocument(file)!!
        }

        fun replace(document: Document, text: String) {
            ApplicationManager.getApplication().runWriteAction { document.setText(text) }
        }
    }
}
