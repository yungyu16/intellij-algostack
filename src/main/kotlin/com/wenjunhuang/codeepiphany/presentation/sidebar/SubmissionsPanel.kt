package com.wenjunhuang.codeepiphany.presentation.sidebar

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.wenjunhuang.codeepiphany.domain.*
import com.wenjunhuang.codeepiphany.presentation.*
import com.wenjunhuang.codeepiphany.presentation.files.*
import com.wenjunhuang.codeepiphany.runtime.onEdt
import com.wenjunhuang.codeepiphany.storage.*
import com.wenjunhuang.codeepiphany.toolwindows.sidebar.submissionlog.leetcode.LeetCodeSubmissionResultForm
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.table.AbstractTableModel

class SubmissionsPanel(project: Project) : SidebarPanel(project), UiDataProvider, FilterHost, QueryHost {
    private data class Request(val filter: SubmissionFilter, val page: Pagination)
    private data class Result(val request: Request, val data: SubmissionPage, val languages: List<Language>)
    private var filter = SubmissionFilter()
    private var page = Pagination()
    private var languages = emptyList<Language>()
    private val model = SubmissionsModel()
    private val table = JBTable(model).apply { setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION) }
    private val detail = JPanel(BorderLayout())
    private val status = JBLabel()
    private val query = LatestQuery<Request, Result>(scope) { request -> ChallengeRepository.getInstance(project).query {
        Result(request, it.submissions(request.filter, request.page.offset, request.page.limit), it.languages())
    } }
    private val detailQuery = LatestQuery<SubmissionRecord, Pair<SubmissionRecord, SubmissionMetrics>>(scope, 100) { row ->
        row to (ChallengeRepository.getInstance(project).query { it.submissionMetrics(row.id) } ?: SubmissionMetrics())
    }
    override val isBusy: Boolean get() = query.states.value is QueryState.Loading
    private val pager = PageBar { number, size -> page = page.copy(currentPage = number, pageSize = size); refresh() }
    init {
        val toolbar = ActionManager.getInstance().createActionToolbar("CodeEpiphany.Submissions",
            ActionManager.getInstance().getAction("CodeEpiphany.Sidebar.SubmissionLog.Toolbar.Group") as ActionGroup, true)
        toolbar.targetComponent = this
        val list = JPanel(BorderLayout()).apply { add(toolbar.component, BorderLayout.NORTH); add(JBScrollPane(table), BorderLayout.CENTER); add(pager, BorderLayout.SOUTH) }
        add(JSplitPane(JSplitPane.HORIZONTAL_SPLIT, list, JBScrollPane(detail)).apply { resizeWeight = 0.5 }, BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)
        table.selectionModel.addListSelectionListener { event -> if (!event.valueIsAdjusting) {
            val row = model.rows.getOrNull(table.selectedRow)
            if (row != null) detailQuery.submit(row) else { detailQuery.cancel(); detail.removeAll(); detail.revalidate(); detail.repaint() }
        } }
        table.tableHeader.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                val sort = model.sorts.getOrNull(modelColumnAt(table, event.point)) ?: return
                filter = filter.copy(sort = sort, ascending = if (filter.sort == sort) !filter.ascending else true)
                refreshHeaders(table)
                page = page.resetToFirstPage(); refresh()
            }
        })
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) { if (event.clickCount == 2) selected().firstOrNull()?.let { openCode(project, it, CodeType.Submission) } }
            override fun mousePressed(event: MouseEvent) = popup(event)
            override fun mouseReleased(event: MouseEvent) = popup(event)
            private fun popup(event: MouseEvent) {
                if (event.isPopupTrigger) ActionManager.getInstance().createActionPopupMenu("CodeEpiphany.Submissions",
                    ActionManager.getInstance().getAction("CodeEpiphany.Sidebar.SubmissionsTable.Popupmenu") as ActionGroup).component.show(table, event.x, event.y)
            }
        })
        project.messageBus.connect(this).subscribe(ChallengeRepository.CHANGED, ChallengeRepository.Listener { refresh() })
        scope.launch { query.states.collect { state -> onEdt { when (state) {
            is QueryState.Success -> {
                val selectedId = selected().firstOrNull()?.id
                val value = state.value; model.rows = value.data.rows; model.fireTableDataChanged()
                languages = value.languages; page = value.request.page.copy(totalSize = value.data.total); pager.update(page); status.text = ""
                value.data.rows.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 }?.let { table.setRowSelectionInterval(it, it) }
            }
            is QueryState.Failure -> status.text = state.error.message.orEmpty()
            else -> Unit
        } } } }
        scope.launch { detailQuery.states.collect { state -> onEdt { when (state) {
            is QueryState.Success -> {
                val (row, metrics) = state.value
                detail.removeAll(); detail.add(LeetCodeSubmissionResultForm(project, row.language, row.languageVersion, row.slug,
                    row.dojo, row, metrics).component, BorderLayout.CENTER)
                detail.revalidate(); detail.repaint()
            }
            is QueryState.Failure -> status.text = state.error.message.orEmpty()
            else -> Unit
        } } } }
        refresh()
    }
    fun selected(): List<SubmissionRecord> = table.selectedRows.toList().mapNotNull(model.rows::getOrNull)
    override fun refresh() { query.submit(Request(filter, page)) }
    override fun items(kind: FilterKind): List<FilterItem> = when (kind) {
        FilterKind.Dojo -> CodeDojo.entries.map { FilterItem(it.value, it.show, it.icon) }
        FilterKind.Language -> languages.map { FilterItem(it.value, it.show, it.icon) }
        else -> emptyList()
    }
    override fun selected(kind: FilterKind): Set<String> = when (kind) {
        FilterKind.Dojo -> filter.dojos.map { it.value }.toSet()
        FilterKind.Language -> filter.languages.map { it.value }.toSet()
        else -> emptySet()
    }
    override fun toggle(kind: FilterKind, id: String) {
        when (kind) {
            FilterKind.Dojo -> CodeDojo.fromString(id)?.let { filter = filter.copy(dojos = if (it in filter.dojos) filter.dojos - it else filter.dojos + it) }
            FilterKind.Language -> Language.fromString(id)?.let { filter = filter.copy(languages = if (it in filter.languages) filter.languages - it else filter.languages + it) }
            else -> return
        }
        page = page.resetToFirstPage(); refresh()
    }
    override fun uiDataSnapshot(sink: DataSink) {
        sink[ActionContext.FILTERS] = this; sink[ActionContext.QUERY] = this; sink[KEY] = this
        val files = selected().map { codeFile(project, it, CodeType.Submission) }
        sink[CommonDataKeys.VIRTUAL_FILE_ARRAY] = files.toTypedArray()
        if (files.size == 1) sink[CommonDataKeys.VIRTUAL_FILE] = files.single()
    }
    override fun dispose() { query.close(); detailQuery.close(); super.dispose() }
    private inner class SubmissionsModel : AbstractTableModel() {
        var rows: List<SubmissionRecord> = emptyList()
        val sorts = listOf(SubmissionSort.Site, SubmissionSort.Language, SubmissionSort.Title, SubmissionSort.Difficulty, SubmissionSort.Solution, SubmissionSort.Result, SubmissionSort.Time)
        private val columns = arrayOf("Site", "Language", "Title", "Difficulty", "Solution", "Result", "Submitted")
        override fun getRowCount() = rows.size
        override fun getColumnCount() = columns.size
        override fun getColumnName(column: Int) = sortedHeader(columns[column], sorts[column].name,
            QueryOrder(filter.sort.name, if (filter.ascending) OrderDirection.Ascending else OrderDirection.Descending))
        override fun getValueAt(row: Int, column: Int): Any { val item = rows[row]; return when (column) {
            0 -> item.dojo.show; 1 -> Language.prettyPrint(item.language, item.languageVersion); 2 -> item.challengeTitle
            3 -> ChallengeDifficulty.fromString(item.difficulty)?.showAsHtml().orEmpty(); 4 -> item.solutionTitle
            5 -> SubmissionResult.fromString(item.result)?.showAsHtml().orEmpty(); else -> item.submitDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        } }
    }
    companion object { val KEY = DataKey.create<SubmissionsPanel>("CodeEpiphany.SubmissionRows") }
}
