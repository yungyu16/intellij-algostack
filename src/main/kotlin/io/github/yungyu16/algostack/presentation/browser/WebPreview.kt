package io.github.yungyu16.algostack.presentation.browser

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.jcef.JBCefCookie
import com.intellij.ui.jcef.JCEFHtmlPanel
import io.github.yungyu16.algostack.domain.CodeDojo
import io.github.yungyu16.algostack.runtime.HttpService
import java.beans.PropertyChangeListener
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import io.github.yungyu16.algostack.runtime.ProjectTasks
import io.github.yungyu16.algostack.runtime.onEdt
import javax.swing.Icon
import javax.swing.JComponent

object PreviewFileType : FakeFileType() {
    override fun getName(): String = "WebPreviewFileType"
    override fun getDescription(): String = "Web Preview File Type"
    override fun isMyFileType(file: VirtualFile): Boolean = file is PreviewFile
    override fun getIcon(): Icon = com.intellij.icons.AllIcons.Nodes.PpWeb
}
class PreviewFile(val previewUrl: String, val dojo: CodeDojo, val label: String) : LightVirtualFile() {
    init { setFileType(PreviewFileType); isWritable = false }
    override fun getName(): String = label
    override fun hashCode(): Int = previewUrl.hashCode()
    override fun equals(other: Any?): Boolean = other is PreviewFile && other.previewUrl == previewUrl && other.dojo == dojo && other.label == label
}
class PreviewEditor(private val project: Project, private val preview: PreviewFile) : UserDataHolderBase(), FileEditor {
    private val panel = JCEFHtmlPanel("about:blank")
    @Volatile private var closed = false
    private var cookieWrites: List<Future<Boolean>> = emptyList()
    private var loading: Job? = null
    init { reload() }
    fun reload() {
        loading?.cancel(); cookieWrites.forEach { it.cancel(true) }
        val pending = HttpService.getInstance().cookies.get(preview.dojo).map { cookie ->
            panel.getJBCefCookieManager().setCookie(preview.previewUrl,
                JBCefCookie(cookie.name, cookie.value, preview.dojo.domain, "/", true, cookie.isHttpOnly))
        }
        cookieWrites = pending
        loading = ProjectTasks.getInstance(project).launch {
            try { runInterruptible(Dispatchers.IO) { pending.forEach { it.get(10, TimeUnit.SECONDS) } } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* The page can still use its existing browser session. */ }
            onEdt { if (!closed) panel.loadURL(preview.previewUrl) }
        }
    }
    override fun getFile(): VirtualFile = preview
    override fun getComponent(): JComponent = panel.component
    override fun getPreferredFocusedComponent(): JComponent = panel.component
    override fun getName(): String = preview.label
    override fun setState(state: FileEditorState) = Unit
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = !closed
    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun dispose() { closed = true; loading?.cancel(); cookieWrites.forEach { it.cancel(true) }; Disposer.dispose(panel) }
}
class PreviewEditorProvider : FileEditorProvider {
    override fun accept(project: Project, file: VirtualFile): Boolean = file is PreviewFile
    override fun createEditor(project: Project, file: VirtualFile): FileEditor = PreviewEditor(project, file as PreviewFile)
    override fun getEditorTypeId(): String = "AlgoStack.WebPreviewEditorProvider"
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
fun openWebPreview(project: Project, dojo: CodeDojo, url: String, title: String) {
    val file = PreviewFile(url, dojo, title)
    val manager = FileEditorManager.getInstance(project)
    if (manager.isFileOpen(file)) manager.getEditors(file).filterIsInstance<PreviewEditor>().forEach { it.reload() }
    manager.openFile(file, true)
}
