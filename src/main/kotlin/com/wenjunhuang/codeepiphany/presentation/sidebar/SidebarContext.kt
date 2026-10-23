package com.wenjunhuang.codeepiphany.presentation.sidebar

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.wenjunhuang.codeepiphany.configuration.ChallengeSettings
import com.wenjunhuang.codeepiphany.presentation.LatestQuery
import com.wenjunhuang.codeepiphany.presentation.QueryState
import com.wenjunhuang.codeepiphany.presentation.files.CodeFile
import com.wenjunhuang.codeepiphany.presentation.files.NoteFile
import com.wenjunhuang.codeepiphany.storage.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class SidebarContext(private val project: Project, scope: CoroutineScope) : Disposable {
    private val current = MutableStateFlow<ChallengeRecord?>(null)
    val challenge = current.asStateFlow()
    private var selectedId: Long? = null
    private val query = LatestQuery<Long?, ChallengeRecord?>(scope, 150) { id ->
        id?.let { ChallengeRepository.getInstance(project).query { store -> store.challenge(it) } }
    }
    init {
        project.messageBus.connect(this).apply {
            subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) { select(event.newFile) }
            })
            subscribe(ChallengeRepository.CHANGED, ChallengeRepository.Listener { query.submit(selectedId) })
            subscribe(com.wenjunhuang.codeepiphany.configuration.CodeEpiphanySettings.DATABASE_FOLDER_TOPIC,
                com.wenjunhuang.codeepiphany.configuration.CodeEpiphanySettings.DatabaseFolderNotifier {
                    current.value = null; query.submit(selectedId)
                })
        }
        scope.launch { query.states.collect { if (it is QueryState.Success) current.value = it.value } }
        select(FileEditorManager.getInstance(project).selectedFiles.firstOrNull())
    }
    private fun select(file: VirtualFile?) {
        selectedId = when (file) {
            is NoteFile -> file.notePath.challengeId
            is CodeFile -> file.record.challengeId
            null -> null
            else -> ChallengeSettings.getInstance(project).findChallengeId(file)?.challengeId
        }
        query.submit(selectedId)
    }
    override fun dispose() = query.close()
    companion object { @JvmStatic fun getInstance(project: Project): SidebarContext = project.getService(SidebarContext::class.java) }
}
