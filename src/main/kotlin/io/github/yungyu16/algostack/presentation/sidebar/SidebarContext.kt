package io.github.yungyu16.algostack.presentation.sidebar

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import io.github.yungyu16.algostack.domain.ChallengeIdentity
import io.github.yungyu16.algostack.runtime.onEdt
import io.github.yungyu16.algostack.utils.ChallengeFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class SidebarContext(private val project: Project, private val scope: CoroutineScope) : Disposable {
    private val current = MutableStateFlow<ChallengeIdentity?>(null)
    val challenge = current.asStateFlow()
    private var loading: Job? = null

    init {
        project.messageBus.connect(this)
            .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    refresh()
                }
            })
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (FileDocumentManager.getInstance()
                        .getFile(event.document) in FileEditorManager.getInstance(project).selectedFiles
                ) refresh()
            }
        }, this)
        refresh()
    }

    private fun refresh() {
        loading?.cancel()
        loading = scope.launch {
            // 依据编辑器选择，而非焦点组件；进入工具窗口不丢失题目上下文。
            val file = onEdt { FileEditorManager.getInstance(project).selectedFiles.firstOrNull() }
            // cached Document（含未保存修改）和 VFS 回退读取均在 read action 内，不能只切到后台线程。
            val identity = file?.let { readAction { ChallengeFiles.resolve(it) } }
            onEdt {
                ensureActive()
                current.value = identity
            }
        }
    }

    override fun dispose() {
        loading?.cancel()
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): SidebarContext = project.getService(SidebarContext::class.java)
    }
}
