package io.github.yungyu16.algostack.presentation.editor

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.vfs.VirtualFile

/** Kotlin 的 by 不转发 Java 接口默认方法，文本编辑器包装必须显式保留这些契约。 */
open class DelegatingTextEditor(protected val delegate: TextEditor, private val sourceFile: VirtualFile) : TextEditor by delegate {
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
