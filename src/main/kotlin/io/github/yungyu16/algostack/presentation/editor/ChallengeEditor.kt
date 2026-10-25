package io.github.yungyu16.algostack.presentation.editor

import com.intellij.ide.FileIconPatcher
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLayeredPane
import com.intellij.util.Alarm
import com.intellij.util.ui.StartupUiUtil
import io.github.yungyu16.algostack.model.Actions
import io.github.yungyu16.algostack.presentation.icon
import io.github.yungyu16.algostack.presentation.sidebar.SidebarPolicy
import io.github.yungyu16.algostack.utils.ChallengeFiles
import org.jdom.Element
import java.awt.AWTEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLayeredPane

class EditorState(val project: Project, val file: VirtualFile) : Disposable {
    val identity get() = ChallengeFiles.resolve(file)
    override fun dispose() = Unit

    companion object {
        val KEY = Key.create<EditorState>("AlgoStack.EditorState")
    }
}

class ChallengeEditor(delegate: TextEditor, project: Project, file: VirtualFile) :
    DelegatingTextEditor(delegate, file) {
    private val logger = Logger.getInstance(ChallengeEditor::class.java)
    val state = EditorState(project, file)
    private val ui by lazy {
        val editorComponent = delegate.component
        val layered = object : JBLayeredPane(), UiDataProvider {
            override fun uiDataSnapshot(sink: DataSink) {
                // 工具栏是编辑器的兄弟组件，不能继承编辑器子树里的选区上下文。
                sink[CommonDataKeys.PROJECT] = state.project
                sink[CommonDataKeys.VIRTUAL_FILE] = state.file
                sink[CommonDataKeys.EDITOR] = delegate.editor
                sink[PlatformCoreDataKeys.FILE_EDITOR] = this@ChallengeEditor
            }

            override fun doLayout() {
                components.forEach { component ->
                    if (component === editorComponent) component.setBounds(0, 0, width, height)
                    else component.setBounds(
                        width - component.preferredSize.width - 25,
                        20,
                        component.preferredSize.width,
                        component.preferredSize.height
                    )
                }
            }
        }
        val group = ActionManager.getInstance().getAction(Actions.CHALLENGE_EDITOR_TOOLBAR_GROUP) as ActionGroup
        val toolbar = LayoutActionsFloatingToolbar(layered, group, this)
        // JLayeredPane 的层常量是 Integer；Kotlin 会优先匹配 add(Component, Int) 重载，
        // 把层约束退化成索引，工具栏就会与编辑器同层而无法保证绘制在上层。显式装箱走 Object 重载。
        layered.add(editorComponent, JLayeredPane.DEFAULT_LAYER as Any)
        layered.add(toolbar, JLayeredPane.POPUP_LAYER as Any)
        logger.info("challenge editor ui built: file=${state.file.name}, group=${Actions.CHALLENGE_EDITOR_TOOLBAR_GROUP}, toolbar=${toolbar.javaClass.simpleName}")
        val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
        StartupUiUtil.addAwtListener(AWTEvent.MOUSE_MOTION_EVENT_MASK, this) {
            if (layered.mousePosition != null) {
                alarm.cancelAllRequests()
                toolbar.scheduleShow()
                if (toolbar.mousePosition == null) alarm.addRequest({ toolbar.scheduleHide() }, 1400)
            } else if (toolbar.mousePosition == null) {
                toolbar.scheduleHide()
            }
        }
        val listener = object : KeyAdapter() {
            override fun keyTyped(event: KeyEvent) {
                toolbar.scheduleHide()
            }
        }
        delegate.editor.contentComponent.addKeyListener(listener)
        Disposer.register(this, Disposable { delegate.editor.contentComponent.removeKeyListener(listener) })
        layered
    }

    init {
        logger.info("challenge editor created: file=${file.name}")
        delegate.putUserData(EditorState.KEY, state)
        Disposer.register(this, state); Disposer.register(this, delegate)
    }

    override fun getComponent(): JComponent = ui
    override fun getName(): String = "ChallengeEditor"
    override fun dispose() {
        logger.info("challenge editor disposed: file=${state.file.name}")
    }
}

class ChallengeEditorProvider : FileEditorProvider, DumbAware {
    private val delegate = PsiAwareTextEditorProvider()
    override fun accept(project: Project, file: VirtualFile): Boolean =
        SidebarPolicy.enabled(project.basePath) && delegate.accept(
            project,
            file
        ) && ChallengeFiles.resolve(file) != null

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        ChallengeEditor(delegate.createEditor(project, file) as TextEditor, project, file)

    override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR
    override fun getEditorTypeId() = "AlgoStack.${delegate.editorTypeId}"
    override fun readState(element: Element, project: Project, file: VirtualFile): FileEditorState =
        delegate.readState(element, project, file)

    override fun writeState(state: FileEditorState, project: Project, element: Element) =
        delegate.writeState(state, project, element)
}

class ChallengeEditorIconProvider : FileIconPatcher {
    override fun patchIcon(icon: Icon, file: VirtualFile, flags: Int, project: Project?): Icon =
        project?.takeIf { SidebarPolicy.enabled(it.basePath) }?.let { ChallengeFiles.resolve(file)?.dojo?.icon } ?: icon
}

class ChallengeEditorTabTitleProvider : EditorTabTitleProvider {
    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? =
        if (project.isDisposed || !SidebarPolicy.enabled(project.basePath)) null
        else project.getService(ChallengeEditorTitles::class.java).title(file)
}
