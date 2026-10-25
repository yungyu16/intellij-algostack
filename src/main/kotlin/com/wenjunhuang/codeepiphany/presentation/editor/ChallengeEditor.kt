package com.wenjunhuang.codeepiphany.presentation.editor

import com.intellij.ide.DataManager
import com.intellij.ide.FileIconPatcher
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.ui.components.JBLayeredPane
import com.intellij.util.Alarm
import com.intellij.util.ui.StartupUiUtil
import com.wenjunhuang.codeepiphany.presentation.icon
import com.wenjunhuang.codeepiphany.presentation.sidebar.SidebarPolicy
import com.wenjunhuang.codeepiphany.configuration.ChallengeSettings
import com.wenjunhuang.codeepiphany.model.Actions
import com.wenjunhuang.codeepiphany.runtime.ProjectTasks
import com.wenjunhuang.codeepiphany.runtime.onEdt
import kotlinx.coroutines.*
import org.jdom.Element
import java.awt.AWTEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLayeredPane
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class EditorState(val project: Project, val file: VirtualFile) : Disposable {
    val association get() = ChallengeSettings.getInstance(project).resolve(file)
    val title: String? get() = association?.let { it.title.ifBlank { it.slug } }
    override fun dispose() = Unit
    companion object { val KEY = Key.create<EditorState>("CodeEpiphany.EditorState") }
}

class ChallengeEditor(delegate: TextEditor, project: Project, file: VirtualFile) : DelegatingTextEditor(delegate, file) {
    private val logger = Logger.getInstance(ChallengeEditor::class.java)
    val state = EditorState(project, file)
    private val ui by lazy {
        val editorComponent = delegate.component
        val layered = object : JBLayeredPane() {
            override fun doLayout() {
                components.forEach { component ->
                    if (component === editorComponent) component.setBounds(0, 0, width, height)
                    else component.setBounds(width - component.preferredSize.width - 25, 20, component.preferredSize.width, component.preferredSize.height)
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
        // 悬浮工具栏问题需要悬停现场才能定位；按时间间隔记录悬停/隐藏状态与数据上下文。
        val hoverLogs = AtomicInteger(); val hideLogs = AtomicInteger()
        val lastHoverLog = AtomicLong(0); val lastHideLog = AtomicLong(0)
        StartupUiUtil.addAwtListener(AWTEvent.MOUSE_MOTION_EVENT_MASK, this) { event ->
            logger.debug("mouse motion event: $event")
            val now = System.currentTimeMillis()
            if (layered.mousePosition != null) {
                alarm.cancelAllRequests()
                if (now - lastHoverLog.get() >= 1500 && hoverLogs.getAndIncrement() < 10) {
                    lastHoverLog.set(now)
                    val context = DataManager.getInstance().getDataContext(toolbar)
                    logger.info("challenge toolbar hover: file=${state.file.name}, toolbarVisible=${toolbar.isVisible}, visibleActions=${toolbar.hasVisibleActions()}, bounds=${toolbar.bounds}, preferred=${toolbar.preferredSize}, layer=${JLayeredPane.getLayer(toolbar)}, virtualFile=${CommonDataKeys.VIRTUAL_FILE.getData(context)?.name}, fileEditor=${PlatformCoreDataKeys.FILE_EDITOR.getData(context)?.javaClass?.simpleName}, project=${PlatformCoreDataKeys.PROJECT.getData(context)?.name}")
                    alarm.addRequest({
                        ApplicationManager.getApplication().invokeLater {
                            logger.info("challenge toolbar after show: file=${state.file.name}, toolbarVisible=${toolbar.isVisible}, visibleActions=${toolbar.hasVisibleActions()}, bounds=${toolbar.bounds}, preferred=${toolbar.preferredSize}, layer=${JLayeredPane.getLayer(toolbar)}")
                        }
                    }, 900)
                }
                toolbar.scheduleShow()
                if (toolbar.mousePosition == null) alarm.addRequest({ toolbar.scheduleHide() }, 1400)
            } else if (toolbar.mousePosition == null) {
                if (now - lastHideLog.get() >= 5000 && hideLogs.getAndIncrement() < 5) {
                    lastHideLog.set(now)
                    logger.info("challenge toolbar leave: file=${state.file.name}, toolbarVisible=${toolbar.isVisible}")
                }
                toolbar.scheduleHide()
            }
        }
        val listener = object : KeyAdapter() { override fun keyTyped(event: KeyEvent) { toolbar.scheduleHide() } }
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
        SidebarPolicy.enabled(project.basePath) && delegate.accept(project, file) && ChallengeSettings.getInstance(project).resolve(file) != null
    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        ChallengeEditor(delegate.createEditor(project, file) as TextEditor, project, file)
    override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR
    override fun getEditorTypeId() = "CodeEpiphany.${delegate.editorTypeId}"
    override fun readState(element: Element, project: Project, file: VirtualFile): FileEditorState = delegate.readState(element, project, file)
    override fun writeState(state: FileEditorState, project: Project, element: Element) = delegate.writeState(state, project, element)
}

class ChallengeEditorIconProvider : FileIconPatcher {
    override fun patchIcon(icon: Icon, file: VirtualFile, flags: Int, project: Project?): Icon =
        project?.takeIf { SidebarPolicy.enabled(it.basePath) }?.let { ChallengeSettings.getInstance(it).resolve(file)?.dojo?.icon } ?: icon
}
class ChallengeEditorTabTitleProvider : EditorTabTitleProvider {
    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? =
        FileEditorManager.getInstance(project).getEditors(file).filterIsInstance<ChallengeEditor>().firstOrNull()?.state?.title
}

class ChallengeFileRenameListener : AsyncFileListener {
    override fun prepareChange(events: MutableList<out VFileEvent>): AsyncFileListener.ChangeApplier {
        val changes = events.mapNotNull { event -> when (event) {
            is VFilePropertyChangeEvent -> if (event.oldPath != event.newPath) Triple(event.oldPath, event.newPath, event.file.isDirectory) else null
            is VFileMoveEvent -> if (event.isValid) Triple(event.oldPath, event.newPath, event.file.isDirectory) else null
            is VFileDeleteEvent -> Triple(event.path, null, event.file.isDirectory)
            else -> null
        } }
        return object : AsyncFileListener.ChangeApplier {
            override fun afterVfsChange() {
                for (project in ProjectManager.getInstance().openProjects) {
                    if (project.isDisposed || project.isDefault || !SidebarPolicy.enabled(project.basePath)) continue
                    val settings = ChallengeSettings.getInstance(project)
                    for ((old, new, directory) in changes) settings.relocatePaths(old, new, directory)
                }
            }
        }
    }
}
