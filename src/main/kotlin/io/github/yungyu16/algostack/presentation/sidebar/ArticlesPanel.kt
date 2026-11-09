package io.github.yungyu16.algostack.presentation.sidebar

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import io.github.yungyu16.algostack.PluginBundle
import io.github.yungyu16.algostack.domain.ChallengeIdentity
import io.github.yungyu16.algostack.domain.CodeDojo
import io.github.yungyu16.algostack.domain.Pagination
import io.github.yungyu16.algostack.leetcode.model.*
import io.github.yungyu16.algostack.presentation.*
import io.github.yungyu16.algostack.presentation.auth.LoginDialog
import io.github.yungyu16.algostack.presentation.browser.WebDocumentView
import io.github.yungyu16.algostack.runtime.AuthService
import io.github.yungyu16.algostack.runtime.HttpService
import io.github.yungyu16.algostack.runtime.onEdt
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class ArticlesPanel(project: Project, private val document: WebDocumentView = WebDocumentView(project, true)) : SidebarPanel(project), UiDataProvider, FilterHost, QueryHost {
    private data class Request(
        val challenge: ChallengeIdentity, val page: Pagination,
        val order: LeetCodeQuestionSolutionArticlesOrderBy, val keyword: String, val tags: List<String>
    )

    private data class Result(
        val request: Request,
        val list: LeetCodeQuestionSolutionArticles,
        val tags: LeetCodeSolutionTags
    )

    private var challenge: ChallengeIdentity? = null
    private var page = Pagination()
    var order = LeetCodeQuestionSolutionArticlesOrderBy.Hot; private set
    private var tags: LeetCodeSolutionTags? = null
    @Volatile
    private var cachedTags: Pair<Pair<CodeDojo, String>, LeetCodeSolutionTags>? = null
    private var selectedTags = emptySet<String>()
    private val search = JBTextField()
    private val status = JBLabel()
    private val model = ArticlesModel()
    private val table = JBTable(model)
    private val avatars = linkedMapOf<String, javax.swing.Icon>()
    private val login = JButton(PluginBundle.message("action.AlgoStack.Actions.Login.text"))
    private val detailQuery =
        LatestQuery<Pair<CodeDojo, String>, Pair<CodeDojo, LeetCodeSolutionArticle>>(scope) { (dojo, slug) ->
            dojo to HttpService.getInstance().api(dojo).getSolutionArticle(slug)
        }
    private val previewSelection = ArticlePreviewSelection(table, detailQuery, {
        status.text = ""
        document.setContent("", challenge?.dojo ?: CodeDojo.LeetCodeCN)
    }) {
        val item = model.rows.getOrNull(table.convertRowIndexToModel(table.selectedRow))
        val dojo = challenge?.dojo
        if (item != null && dojo != null) {
            if (item.isPremium && AuthService.getInstance(project).userInfo(dojo)?.isPremium != true) {
                status.text = "Premium article"
            } else detailQuery.submit(dojo to item.slug)
        }
    }
    private val query = LatestQuery<Request, Result>(scope) { request ->
        coroutineScope {
            val api = HttpService.getInstance().api(request.challenge.dojo)
            val list = async {
                api.searchQuestionSolutionArticles(
                    request.page.offset, request.page.limit, request.challenge.slug,
                    request.order, request.keyword, request.tags
                )
            }
            val key = request.challenge.dojo to request.challenge.slug
            val tags = async {
                cachedTags?.takeIf { it.first == key }?.second ?: api.getSolutionTags(request.challenge.slug)
                    .also { cachedTags = key to it }
            }
            Result(request, list.await(), tags.await())
        }
    }
    override val isBusy: Boolean get() = query.states.value is QueryState.Loading
    private val pager = PageBar { number, size -> page = page.copy(currentPage = number, pageSize = size); refresh() }

    init {
        Disposer.register(this, document)
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.setDefaultRenderer(Any::class.java, object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                owner: JTable,
                value: Any?,
                selected: Boolean,
                focused: Boolean,
                row: Int,
                column: Int
            ): java.awt.Component {
                val component = super.getTableCellRendererComponent(owner, value, selected, focused, row, column)
                val item = model.rows.getOrNull(row)
                val modelColumn = owner.convertColumnIndexToModel(column)
                icon = if (modelColumn == 1 && item != null) avatars.getOrPut(item.author.userAvatar) {
                    AvatarIcon(
                        scope,
                        item.author.userAvatar,
                        16
                    )
                } else null
                toolTipText = if (modelColumn == 4) item?.summary else null
                if (!selected) foreground = if (item?.isPremium == true && challenge?.let {
                        AuthService.getInstance(project).userInfo(it.dojo)?.isPremium
                    } != true)
                    UIManager.getColor("Label.disabledForeground") else owner.foreground
                return component
            }
        })
        val toolbar = ActionManager.getInstance().createActionToolbar(
            "AlgoStack.Articles",
            ActionManager.getInstance().getAction("AlgoStack.Sidebar.LeetCodeArticles.Toolbar.Group") as ActionGroup,
            true
        )
        toolbar.targetComponent = this
        val top = JPanel(BorderLayout()).apply {
            add(toolbar.component, BorderLayout.WEST); add(
            search,
            BorderLayout.CENTER
        ); add(login, BorderLayout.EAST)
        }
        val left = JPanel(BorderLayout()).apply {
            add(top, BorderLayout.NORTH); add(
            JBScrollPane(table),
            BorderLayout.CENTER
        ); add(pager, BorderLayout.SOUTH)
        }
        add(JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, document).apply { resizeWeight = 0.45 }, BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)
        search.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                page = page.resetToFirstPage(); refresh()
            }
        })
        login.addActionListener {
            challenge?.let { item ->
                scope.launch {
                    if (LoginDialog.login(
                            project,
                            item.dojo
                        )
                    ) onEdt { refresh() }
                }
            }
        }
        scope.launch {
            SidebarContext.getInstance(project).challenge.collect { item ->
                onEdt {
                    challenge = item; page = Pagination(); selectedTags = emptySet(); tags = null
                    if (order !in orders()) order = LeetCodeQuestionSolutionArticlesOrderBy.Hot
                    previewSelection.clear()
                    model.rows = emptyList(); model.fireTableDataChanged(); refresh()
                }
            }
        }
        scope.launch { AuthService.getInstance(project).states.collect { onEdt { refresh() } } }
        scope.launch {
            query.states.collect { state ->
                onEdt {
                    if (!query.isCurrent(state)) return@onEdt
                    when (state) {
                        is QueryState.Success -> {
                            val value = state.value
                            if (challenge == value.request.challenge) {
                                previewSelection.clear()
                                model.rows =
                                    value.list.edges.map { it.node }; avatars.keys.retainAll(model.rows.map { it.author.userAvatar }
                                    .toSet()); model.fireTableDataChanged(); tags = value.tags
                                page =
                                    value.request.page.copy(totalSize = value.list.totalNum); pager.update(page); status.text =
                                    ""
                            }
                        }

                        is QueryState.Failure -> status.text = state.error.message.orEmpty()
                        else -> Unit
                    }
                }
            }
        }
        scope.launch {
            detailQuery.states.collect { state ->
                onEdt {
                    if (!detailQuery.isCurrent(state)) return@onEdt
                    when (state) {
                        is QueryState.Success -> {
                            val (dojo, article) = state.value
                            document.setContent(article.content, dojo)
                        }

                        is QueryState.Failure -> status.text = state.error.message.orEmpty()
                        else -> Unit
                    }
                }
            }
        }
    }

    override fun refresh() {
        // 翻页、筛选、排序和认证变化都先使旧详情失效，不等待新列表返回。
        previewSelection.clear()
        val item = challenge
        val authenticated = item != null && AuthService.getInstance(project).isLoggedIn(item.dojo)
        login.isVisible = item != null && !authenticated
        if (!authenticated) {
            query.cancel(); model.rows = emptyList(); model.fireTableDataChanged()
            status.text = item?.let { PluginBundle.message("unauthenticatedView.message", it.dojo.show) }.orEmpty()
            return
        }
        query.submit(Request(item, page, order, search.text, selectedTags.toList()))
    }

    fun setOrder(value: LeetCodeQuestionSolutionArticlesOrderBy) {
        order = value; page = page.resetToFirstPage(); refresh()
    }

    fun orders(): List<LeetCodeQuestionSolutionArticlesOrderBy> = LeetCodeQuestionSolutionArticlesOrderBy.entries
    override fun items(kind: FilterKind): List<FilterItem> {
        if (kind != FilterKind.Tags) return emptyList()
        val tags = tags ?: return emptyList()
        return tags.languageTags.map { FilterItem(it.slug, it.nameTranslated ?: it.name, group = "Language") } +
            tags.knowledgeTags.map { FilterItem(it.slug, it.nameTranslated ?: it.name, group = "Topic") }
    }

    override fun selected(kind: FilterKind): Set<String> = if (kind == FilterKind.Tags) selectedTags else emptySet()
    override fun toggle(kind: FilterKind, id: String) {
        if (kind == FilterKind.Tags) {
            selectedTags = if (id in selectedTags) selectedTags - id else selectedTags + id; page =
                page.resetToFirstPage(); refresh()
        }
    }

    override fun uiDataSnapshot(sink: DataSink) {
        sink[ActionContext.FILTERS] = this; sink[ActionContext.QUERY] = this; sink[KEY] = this
    }

    override fun dispose() {
        query.close(); detailQuery.close(); super.dispose()
    }

    private class ArticlesModel : AbstractTableModel() {
        var rows: List<LeetCodeQuestionSolutionArticle> = emptyList()
        private val columns = arrayOf("Title", "Author", "Status", "Type", "Summary", "Views", "Upvotes", "Created")
        override fun getRowCount() = rows.size
        override fun getColumnCount() = columns.size
        override fun getColumnName(column: Int) = columns[column]
        override fun getValueAt(row: Int, column: Int): Any {
            val item = rows[row]; return when (column) {
                0 -> (if (item.isPremium) "🔒 " else "") + item.title; 1 -> item.author.realName.ifBlank { item.author.username }
                2 -> listOfNotNull(
                    if (item.isPremium) "Premium" else "Free",
                    if (item.isMyFavorite) "Favorite" else null,
                    if (item.hasVideo) "Video" else null
                ).joinToString(" · ")

                3 -> when {
                    item.byLeetcode -> "Official Solution"; item.isEditorsPick == true -> "Editor's Choice"; else -> ""
                }

                4 -> item.summary; 5 -> item.hitCount; 6 -> item.upVote ?: 0; else -> item.createdAt
            }
        }
    }

    companion object {
        val KEY = DataKey.create<ArticlesPanel>("AlgoStack.Articles")
    }
}

/** 选择与列表刷新共用同一失效路径；所有调用都在 EDT 上执行。 */
internal class ArticlePreviewSelection(
    private val table: JTable,
    private val detailQuery: LatestQuery<*, *>,
    private val clearPreview: () -> Unit,
    onSelected: () -> Unit
) {
    init {
        table.selectionModel.addListSelectionListener { event ->
            invalidate()
            if (!event.valueIsAdjusting && table.selectedRow >= 0) onSelected()
        }
    }

    fun clear() {
        table.clearSelection()
        // 空选择也可能有已排队的详情，不能只依赖 selection 事件。
        invalidate()
    }

    private fun invalidate() {
        detailQuery.cancel()
        clearPreview()
    }
}
