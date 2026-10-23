package com.wenjunhuang.codeepiphany.toolwindows.sidebar.notes

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo
import com.intellij.util.ui.ColumnInfo
import com.wenjunhuang.codeepiphany.domain.ChallengeDifficulty
import com.wenjunhuang.codeepiphany.domain.Language
import com.wenjunhuang.codeepiphany.presentation.*
import com.wenjunhuang.codeepiphany.presentation.files.openNote
import com.wenjunhuang.codeepiphany.presentation.sidebar.SidebarContext
import com.wenjunhuang.codeepiphany.runtime.ProjectTasks
import com.wenjunhuang.codeepiphany.runtime.onEdt
import com.wenjunhuang.codeepiphany.storage.*
import kotlinx.coroutines.*
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class NotesListPresenter(val project: Project) : Disposable {
    private val parent = ProjectTasks.getInstance(project).scope
    private val job = SupervisorJob(parent.coroutineContext[Job])
    private val scope = CoroutineScope(parent.coroutineContext + job)
    private val root = DefaultMutableTreeNode("Solutions")
    private var challenge: ChallengeRecord? = null
    private var rows: List<SolutionRecord> = emptyList()
    val treeModel = ListTreeTableModelOnColumns(root, arrayOf<ColumnInfo<*, *>>(TreeColumnInfo("Title"),
        object : ColumnInfo<DefaultMutableTreeNode, Int>("Submissions") {
            override fun valueOf(item: DefaultMutableTreeNode): Int = when (val entry = item.userObject) {
                is NoteEntry.NoteNode -> entry.submissions; is NoteEntry.LanguageNode -> entry.submissionCount; else -> 0
            }
        }))
    val treeRenderer = object : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, focus: Boolean) {
            when (val entry = (value as? DefaultMutableTreeNode)?.userObject) {
                is NoteEntry.NoteNode -> append(entry.title)
                is NoteEntry.LanguageNode -> { icon = entry.language.icon; append(Language.prettyPrint(entry.language, entry.version)) }
                else -> Unit
            }
        }
    }
    private val view = NoteListView(this)
    private data class Data(val challenge: ChallengeRecord, val notes: List<SolutionRecord>, val counts: List<SolutionLanguageCount>)
    private val query = LatestQuery<ChallengeRecord, Data>(scope) { item -> ChallengeRepository.getInstance(project).query {
        Data(item, it.solutions(item.id), it.solutionLanguages(item.id))
    } }
    init {
        project.messageBus.connect(this).subscribe(com.wenjunhuang.codeepiphany.configuration.CodeEpiphanySettings.DATABASE_FOLDER_TOPIC,
            com.wenjunhuang.codeepiphany.configuration.CodeEpiphanySettings.DatabaseFolderNotifier {
                query.cancel(); challenge = null; rows = emptyList(); root.removeAllChildren(); treeModel.nodeStructureChanged(root)
                view.setChallengeName(""); view.setDifficulty("")
            })
        project.messageBus.connect(this).subscribe(ChallengeRepository.CHANGED, ChallengeRepository.Listener { requery() })
        scope.launch { SidebarContext.getInstance(project).challenge.collect { item ->
            if (item != null) onEdt { challenge = item; requery() }
        } }
        scope.launch { query.states.collect { state -> onEdt { when (state) {
            is QueryState.Success -> {
                val data = state.value; rows = data.notes
                view.setChallengeName(data.challenge.title); view.setCodeDojo(data.challenge.dojo)
                view.setDifficulty(ChallengeDifficulty.fromString(data.challenge.difficulty)?.showAsHtml().orEmpty())
                root.removeAllChildren()
                for (note in data.notes) {
                    project.getService(com.wenjunhuang.codeepiphany.presentation.files.FileCache::class.java).notes.values
                        .filter { it.notePath.solutionId == note.id }.forEach { it.updateTitle(data.challenge.title, note.title) }
                    val node = DefaultMutableTreeNode(NoteEntry.NoteNode(note.id, note.title, note.submissions, note.isDefault))
                    data.counts.filter { it.solutionId == note.id }.forEach { node.add(DefaultMutableTreeNode(NoteEntry.LanguageNode(it.language, it.version, it.count))) }
                    root.add(node)
                }
                treeModel.nodeStructureChanged(root)
            }
            is QueryState.Failure -> view.setChallengeName(state.error.message.orEmpty())
            else -> Unit
        } } } }
    }
    fun getView(): JComponent = view.component
    fun isSolutionTitleAvailable(title: String): Boolean = challenge != null && title.isNotBlank() && rows.none { it.title == title }
    fun requery() { challenge?.let(query::submit) }
    private fun perform(block: suspend () -> Unit) { scope.launch {
        try { block() } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { onEdt { Messages.showErrorDialog(project, failure.message.orEmpty(), "Solution") } }
    } }
    fun addNewSolution(title: String) {
        val item = challenge ?: return
        perform { ChallengeRepository.getInstance(project).transaction { it.createSolution(item.id, title) } }
    }
    fun modifySolutionTitle(id: Long, node: DefaultMutableTreeNode, title: String) {
        perform { ChallengeRepository.getInstance(project).transaction { it.renameSolution(id, title) } }
    }
    fun openSolutionRemarkEditor(id: Long) {
        val item = challenge ?: return
        perform {
            val note = ChallengeRepository.getInstance(project).query { it.solution(id) } ?: return@perform
            onEdt { openNote(project, note, item.dojo, item.title) }
        }
    }
    override fun dispose() { query.close(); job.cancel() }
}
