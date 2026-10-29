package io.github.yungyu16.algostack.presentation.editor

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.*

/** Kotlin 的 by 不转发 Java 接口默认方法，文本编辑器包装必须显式保留这些契约。 */
open class DelegatingTextEditor(protected val delegate: TextEditor, private val sourceFile: VirtualFile) :
    TextEditor by delegate {
    private val listenerBridges = IdentityHashMap<PropertyChangeListener, MutableList<PropertyChangeListener>>()

    // 平台登记的是包装器；底层事件不能以 delegate 身份交付，否则平台找不到所属 composite。
    @Synchronized
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        val bridge = PropertyChangeListener { event ->
            val forwarded = PropertyChangeEvent(this, event.propertyName, event.oldValue, event.newValue).apply {
                propagationId = event.propagationId
            }
            listener.propertyChange(forwarded)
        }
        delegate.addPropertyChangeListener(bridge)
        listenerBridges.getOrPut(listener) { mutableListOf() }.add(bridge)
    }

    @Synchronized
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        val bridges = listenerBridges[listener] ?: return
        // 与 PropertyChangeSupport 一致：同一监听器可重复注册，每次 remove 只移除一次。
        delegate.removePropertyChangeListener(bridges.first())
        bridges.removeAt(0)
        if (bridges.isEmpty()) listenerBridges.remove(listener)
    }

    // 不覆盖 TextEditor 的内部加载探针；加载/恢复仍由 getEditor 返回的底层编辑器负责。
    // PsiAwareTextEditorImpl 未加载时的 backgroundHighlighter 为 null，避免提前创建高亮任务。
    override fun getFile(): VirtualFile = sourceFile
    override fun getState(level: FileEditorStateLevel): FileEditorState = delegate.getState(level)
    override fun setState(state: FileEditorState, exactState: Boolean) = delegate.setState(state, exactState)
    override fun selectNotify() = delegate.selectNotify()
    override fun deselectNotify() = delegate.deselectNotify()
    override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = delegate.backgroundHighlighter
    override fun getCurrentLocation(): FileEditorLocation? = delegate.currentLocation
    override fun getStructureViewBuilder(): StructureViewBuilder? = delegate.structureViewBuilder
    override fun getFilesToRefresh(): List<VirtualFile> = delegate.filesToRefresh
    override fun getTabActions(): ActionGroup? = delegate.tabActions
}
