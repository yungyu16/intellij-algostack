package com.wenjunhuang.codeepiphany.presentation.editor

import com.intellij.ide.FileIconPatcher
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
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
import com.wenjunhuang.codeepiphany.configuration.ChallengeSettings
import com.wenjunhuang.codeepiphany.model.Actions
import com.wenjunhuang.codeepiphany.runtime.ProjectTasks
import com.wenjunhuang.codeepiphany.runtime.onEdt
import com.wenjunhuang.codeepiphany.storage.ChallengeRepository
import com.wenjunhuang.codeepiphany.storage.SolutionRecord
import kotlinx.coroutines.*
import org.jdom.Element
import java.awt.AWTEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLayeredPane

class EditorState(val project: Project, val file: VirtualFile) : Disposable {
    private val parent = ProjectTasks.getInstance(project).scope
    private val job = SupervisorJob(parent.coroutineContext[Job])
    private val scope = CoroutineScope(parent.coroutineContext + job)
    private var loading: Job? = null
    @Volatile var title: String? = null; private set
    @Volatile var solutions: List<SolutionRecord> = emptyList(); private set
    val association get() = ChallengeSettings.getInstance(project).findChallengeId(file)
    init {
        project.messageBus.connect(this).subscribe(ChallengeRepository.CHANGED, ChallengeRepository.Listener { reload() })
        reload()
    }
    fun reload() {
        loading?.cancel()
        val association = association ?: return
        loading = scope.launch {
            try {
                val data = ChallengeRepository.getInstance(project).query { it.challenge(association.challengeId) to it.solutions(association.challengeId) }
                onEdt {
                    title = data.first?.title; solutions = data.second
                    FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(file)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { com.intellij.openapi.diagnostic.Logger.getInstance(EditorState::class.java).warn("Cannot load editor context", failure) }
        }
    }
    fun select(solutionId: Long) {
        ChallengeSettings.getInstance(project).setChallengeSolutionId(file, solutionId)
    }
    override fun dispose() { job.cancel() }
    companion object { val KEY = Key.create<EditorState>("CodeEpiphany.EditorState") }
}

class ChallengeEditor(private val delegate: TextEditor, project: Project, file: VirtualFile) : TextEditor by delegate {
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
        layered.add(editorComponent, JLayeredPane.DEFAULT_LAYER); layered.add(toolbar, JLayeredPane.POPUP_LAYER)
        val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
        StartupUiUtil.addAwtListener(AWTEvent.MOUSE_MOTION_EVENT_MASK, this) {
            if (layered.mousePosition != null) {
                alarm.cancelAllRequests(); toolbar.scheduleShow()
                if (toolbar.mousePosition == null) alarm.addRequest({ toolbar.scheduleHide() }, 1400)
            } else if (toolbar.mousePosition == null) toolbar.scheduleHide()
        }
        val listener = object : KeyAdapter() { override fun keyTyped(event: KeyEvent) { toolbar.scheduleHide() } }
        delegate.editor.contentComponent.addKeyListener(listener)
        Disposer.register(this, Disposable { delegate.editor.contentComponent.removeKeyListener(listener) })
        layered
    }
    init {
        delegate.putUserData(EditorState.KEY, state)
        Disposer.register(this, state); Disposer.register(this, delegate)
    }
    override fun getComponent(): JComponent = ui
    override fun getName(): String = "ChallengeEditor"
    override fun dispose() = Unit
}

class ChallengeEditorProvider : FileEditorProvider, DumbAware {
    private val delegate = PsiAwareTextEditorProvider()
    override fun accept(project: Project, file: VirtualFile): Boolean =
        delegate.accept(project, file) && ChallengeSettings.getInstance(project).findChallengeId(file) != null
    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        ChallengeEditor(delegate.createEditor(project, file) as TextEditor, project, file)
    override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR
    override fun getEditorTypeId() = "CodeEpiphany.${delegate.editorTypeId}"
    override fun readState(element: Element, project: Project, file: VirtualFile): FileEditorState = delegate.readState(element, project, file)
    override fun writeState(state: FileEditorState, project: Project, element: Element) = delegate.writeState(state, project, element)
}

class ChallengeEditorIconProvider : FileIconPatcher {
    override fun patchIcon(icon: Icon, file: VirtualFile, flags: Int, project: Project?): Icon =
        project?.let { ChallengeSettings.getInstance(it).findChallengeId(file)?.dojo?.icon } ?: icon
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
                    val settings = ChallengeSettings.getInstance(project)
                    for ((old, new, directory) in changes) settings.relocatePaths(old, new, directory)
                }
            }
        }
    }
}
