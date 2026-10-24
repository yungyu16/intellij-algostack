package com.wenjunhuang.codeepiphany.presentation.dojo

import com.wenjunhuang.codeepiphany.presentation.auth.LoginDialog

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.wenjunhuang.codeepiphany.PluginBundle
import com.wenjunhuang.codeepiphany.configuration.PlatformSettings
import com.wenjunhuang.codeepiphany.domain.*
import com.wenjunhuang.codeepiphany.execution.openChallenge
import com.wenjunhuang.codeepiphany.leetcode.api.LeetCodeClient
import com.wenjunhuang.codeepiphany.leetcode.api.model.*
import com.wenjunhuang.codeepiphany.model.Actions
import com.wenjunhuang.codeepiphany.presentation.*
import com.wenjunhuang.codeepiphany.presentation.browser.openWebPreview
import com.wenjunhuang.codeepiphany.runtime.*
import com.wenjunhuang.codeepiphany.utils.ColorUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class ChallengesView(private val project: Project, override val dojo: CodeDojo) :
    JPanel(BorderLayout()), UiDataProvider, Disposable, SiteHost, ChallengeHost, FilterHost, QueryHost {
    private data class Metadata(val categories: List<LeetCodeCategoryListItem>, val favorites: List<LeetCodeFavoriteItem>,
        val tags: List<LeetCodeTagTypeWithTags>, val companies: List<LeetCodeQuestionCompanyTag>, val positions: List<LeetCodeProblemsetPositionTag>)
    private data class Request(val mode: QueryMode, val parameters: ProblemCriteria,
        val company: CompanyCriteria, val page: Pagination)
    private data class Page(val request: Request, val rows: List<LeetCodeChallengeListItem>, val total: Int)
    private val logger = Logger.getInstance(ChallengesView::class.java)
    private val childJob = SupervisorJob(ProjectTasks.getInstance(project).scope.coroutineContext[Job])
    private val scope = CoroutineScope(ProjectTasks.getInstance(project).scope.coroutineContext + childJob)
    private val settings get() = PlatformSettings.getInstance(project, dojo).state
    private val auth get() = AuthService.getInstance(project)
    private val api get() = HttpService.getInstance().api(dojo)
    override var mode = QueryMode.QueryParameters; private set
    private var parameters = ProblemCriteria()
    private var company = CompanyCriteria()
    private val pages = QueryMode.entries.associateWith { Pagination() }.toMutableMap()
    private var metadata: Metadata? = null
    private var ready = false
    private var readyRevision = -1L
    private var closed = false
    override var loggingIn = false; private set
    override val loggedIn: Boolean get() = ready && auth.isLoggedIn(dojo)
    override val userInfo: LeetCodeUserInfo? get() = auth.userInfo(dojo)
    private var avatarUrl: String? = null
    private var avatarIcon: Icon? = null
    override val avatar: Icon? get() {
        val url = userInfo?.avatar
        if (url != avatarUrl) { avatarUrl = url; avatarIcon = url?.let { AvatarIcon(scope, it, 16) } }
        return avatarIcon
    }
    private val query = LatestQuery<Request, Page>(scope) { request -> loadPage(request) }
    override val isBusy: Boolean get() = loggingIn || query.states.value is QueryState.Loading
    private val model = ProblemTable()
    private val table = JBTable(model).apply { setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION); autoCreateRowSorter = false }
    private val search = SearchTextField(true).apply {
        textEditor.emptyText.appendText(PluginBundle.message("ui.query.searchHint"),
            SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, ColorUtils.LABEL_GRAY_COLOR))
    }
    private var updatingControls = false
    private val tagsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2))
    private val message = JBLabel(PluginBundle.message("unauthenticatedView.message", dojo.show), SwingConstants.CENTER)
    private val loginButton = JButton(PluginBundle.message("action.CodeEpiphany.Actions.Login.text")).apply { addActionListener { login() } }
    private val body = JPanel(CardLayout())
    private val content = JPanel(BorderLayout())
    private val pageLabel = JBLabel()
    private val pageSpinner = JSpinner(SpinnerNumberModel(1, 1, 1, 1))
    private val pageSize = JComboBox(PageSize.entries.map { it.value }.toTypedArray())

    init {
        // 筛选动作与站点动作（模式切换、登录、账号、捐赠）合并到面板工具栏。
        val toolbarGroup = DefaultActionGroup().apply {
            add(ActionManager.getInstance().getAction(Actions.LEETCODE_TOOLBAR_GROUP) as ActionGroup)
            addSeparator()
            add(ActionManager.getInstance().getAction(Actions.LEETCODE_SITE_ACTIONS_GROUP) as ActionGroup)
        }
        val toolbar = ActionManager.getInstance().createActionToolbar(Actions.TOOLBAR_PLACE, toolbarGroup, true)
        toolbar.targetComponent = this
        val north = JPanel(BorderLayout()).apply {
            add(toolbar.component, BorderLayout.NORTH); add(search, BorderLayout.CENTER); add(tagsPanel, BorderLayout.SOUTH)
        }
        content.add(north, BorderLayout.NORTH); content.add(JBScrollPane(table), BorderLayout.CENTER)
        val footer = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 2))
        fun button(text: String, action: () -> Unit) = JButton(text).apply { addActionListener { action() } }
        footer.add(button("«") { setPage(1) }); footer.add(button("‹") { setPage((pages.getValue(mode).currentPage - 1).coerceAtLeast(1)) })
        footer.add(pageSpinner); footer.add(pageLabel)
        footer.add(button("›") { setPage((pages.getValue(mode).currentPage + 1).coerceAtMost(pages.getValue(mode).totalPages)) })
        footer.add(button("»") { setPage(pages.getValue(mode).totalPages) }); footer.add(pageSize)
        content.add(footer, BorderLayout.SOUTH)
        val prompt = JPanel(BorderLayout()).apply { add(message, BorderLayout.CENTER); add(loginButton, BorderLayout.SOUTH); border = JBUI.Borders.empty(12) }
        body.add(prompt, "prompt"); body.add(content, "query"); add(body, BorderLayout.CENTER)
        search.textEditor.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                if (!updatingControls) {
                    when (mode) {
                        QueryMode.QueryParameters -> parameters = parameters.withKeyword(search.text)
                        QueryMode.CompanyQuery -> company = company.withKeyword(search.text)
                    }
                    request(true)
                }
            }
        })
        pageSpinner.addChangeListener { if (!updatingControls) setPage((pageSpinner.value as Number).toInt()) }
        pageSize.addActionListener { if (!updatingControls) {
            pages[mode] = pages.getValue(mode).copy(currentPage = 1, pageSize = PageSize.entries.first { it.value == pageSize.selectedItem }); request()
        } }
        table.setDefaultRenderer(Any::class.java, object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(t: JTable, value: Any?, selected: Boolean, focused: Boolean, row: Int, column: Int): Component {
                val component = super.getTableCellRendererComponent(t, value, selected, focused, row, column)
                icon = value as? Icon
                if (value is Icon) text = ""
                if (!selected) foreground = if (model.rows.getOrNull(row)?.paidOnly == true && userInfo?.isPremium != true) UIManager.getColor("Label.disabledForeground") else t.foreground
                return component
            }
        })
        table.tableHeader.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                val field = model.fields.getOrNull(modelColumnAt(table, event.point)) ?: return
                val old = order()
                val next = if (old?.field != field) QueryOrder(field, OrderDirection.Ascending)
                    else if (old.direction == OrderDirection.Ascending) old.copy(direction = OrderDirection.Descending) else null
                when (mode) {
                    QueryMode.QueryParameters -> parameters = parameters.copy(orderBy = next)
                    QueryMode.CompanyQuery -> company = company.copy(orderBy = next)
                }; request(true)
            }
        })
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    val languages = PlatformSettings.getInstance(project, dojo).getSelectedLanguages()
                    if (languages.size == 1) openSelection(languages.single().first, languages.single().second)
                    else ActionManager.getInstance().createActionPopupMenu(Actions.CHALLENGES_TABLE_POPUP_PLACE,
                        ActionManager.getInstance().getAction("CodeEpiphany.Dojos.Actions.OpenChallengeGroup") as ActionGroup).component.show(table, event.x, event.y)
                }
            }
            override fun mousePressed(event: MouseEvent) { popup(event) }
            override fun mouseReleased(event: MouseEvent) { popup(event) }
            private fun popup(event: MouseEvent) {
                if (event.isPopupTrigger) ActionManager.getInstance().createActionPopupMenu(Actions.CHALLENGES_TABLE_POPUP_PLACE,
                    ActionManager.getInstance().getAction(Actions.CHALLENGES_TABLE_POPUP_GROUP) as ActionGroup).component.show(table, event.x, event.y)
            }
        })
        scope.launch { query.states.collectLatest { state -> onEdt {
            if (closed) return@onEdt
            when (state) {
                is QueryState.Success -> if (ready && state.value.request == currentRequest()) {
                    model.rows = state.value.rows; pages[mode] = state.value.request.page.copy(totalSize = state.value.total)
                    model.fireTableDataChanged(); updateFooter()
                }
                is QueryState.Failure -> {
                    pageLabel.text = PluginBundle.message("query.error", state.error.message.orEmpty())
                    logger.warn("Question query failed", state.error)
                    scope.launch { LogConsole.getInstance(project).error(dojo, pageLabel.text) }
                }
                else -> Unit
            }
        } } }
        switchTo(mode)
        authenticate(false)
        scope.launch { auth.states.collectLatest { profiles -> onEdt {
            if (!closed) {
                if (dojo !in profiles || readyRevision != auth.sessionRevision(dojo)) {
                    ready = false; metadata = null
                    query.cancel()
                    model.rows = emptyList(); model.fireTableDataChanged()
                    showBody()
                }
                if (dojo in profiles && !ready && !loggingIn) authenticate(false)
            }
        } } }
    }
    override fun uiDataSnapshot(sink: DataSink) {
        sink.set(ActionContext.SITE, this); sink.set(ActionContext.CHALLENGES, this)
        sink.set(ActionContext.FILTERS, this); sink.set(ActionContext.QUERY, this)
    }
    override fun login() = authenticate(true)
    private fun authenticate(interactive: Boolean) {
        if (loggingIn || closed) return
        loggingIn = true; loginButton.isEnabled = false
        scope.launch {
            var loadingRevision: Long? = null
            try {
                val login: (suspend () -> Boolean)? = if (interactive) ({ LoginDialog.login(project, dojo) }) else null
                if (!auth.ensureAuthenticated(dojo, login)) return@launch
                val revision = auth.sessionRevision(dojo)
                loadingRevision = revision
                val data = coroutineScope {
                    val categories = async { retry { api.getCategoryList() } }; val favorites = async { retry { api.getFavoriteList() } }
                    val tags = async { retry { api.getTagTypeWithTags() } }; val companies = async { retry { api.getQuestionCompanyTags() } }
                    val positions = async { retry { api.getPositionTags() } }
                    Metadata(categories.await(), favorites.await(), tags.await(), companies.await().sortedByDescending { it.questionCount }, positions.await())
                }
                onEdt {
                    if (!closed && auth.isLoggedIn(dojo) && auth.sessionRevision(dojo) == revision) {
                        metadata = data; readyRevision = revision; ready = true
                        switchTo(mode)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { onEdt { if (!closed) message.text = PluginBundle.message("console.loginFailed", dojo.show, failure.message.orEmpty()) } }
            finally { onEdt { if (!closed) {
                loggingIn = false; loginButton.isEnabled = true
                if (loadingRevision != null && loadingRevision != auth.sessionRevision(dojo) && auth.isLoggedIn(dojo)) authenticate(false)
            } } }
        }
    }
    private suspend fun <T> retry(block: suspend () -> T): T {
        repeat(2) { attempt -> try { return block() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { delay(500L shl attempt) } }
        return block()
    }
    override fun logout() { scope.launch { auth.logout(dojo); onEdt { ready = false; showBody() } } }
    override fun showProfile() { userInfo?.userSlug?.let { openWebPreview(project, dojo, "https://${dojo.domain}/u/$it", userInfo?.username ?: dojo.show) } }
    override fun switchTo(mode: QueryMode) {
        this.mode = mode
        model.rows = emptyList(); model.fireTableStructureChanged()
        updatingControls = true
        search.text = if (mode == QueryMode.CompanyQuery) company.keyword else parameters.keyword
        search.isVisible = true
        updatingControls = false
        updateTags(); updateFooter(); showBody()
        if (ready) request()
    }
    private fun showBody() = (body.layout as CardLayout).show(body, if (ready) "query" else "prompt")
    override fun refresh() { if (ready) request() else login() }
    private fun setPage(page: Int) { if (page in 1..pages.getValue(mode).totalPages) { pages[mode] = pages.getValue(mode).copy(currentPage = page); request() } }
    private fun order(): QueryOrder? = when (mode) { QueryMode.QueryParameters -> parameters.orderBy; QueryMode.CompanyQuery -> company.orderBy }
    private val activeFilterKinds get() = mode.activeFilters(dojo, parameters.selectedFavorite != null)
    private fun currentRequest() = Request(mode, parameters, company, pages.getValue(mode))
    private fun request(reset: Boolean = false) {
        if (!ready || closed) return
        if (reset) pages[mode] = pages.getValue(mode).resetToFirstPage()
        updateTags()
        refreshHeaders(table)
        query.submit(currentRequest())
    }
    private suspend fun loadPage(request: Request): Page {
        val page = request.page
        val result = when (request.mode) {
            QueryMode.QueryParameters -> api.searchChallenges(page.offset, page.limit, request.parameters.api())
            QueryMode.CompanyQuery -> if (request.company.selectedCompanies.isEmpty()) LeetCodeChallengeList(emptyList(), 0) else {
                val criteria = request.company
                val result = api.searchCompanyChallenges(page.offset, page.limit, criteria.selectedInterviewPeriod.slug,
                    criteria.selectedCompanies.map { it.slug }, criteria.selectedPositions.map { it.slug }, criteria.api())
                LeetCodeChallengeList(result.questions.map { LeetCodeChallengeListItem(it.acRate, it.difficulty, it.freqBar,
                    it.paidOnly, null, it.status, it.questionFrontendId, it.title, it.titleCn, it.titleSlug) }, result.total)
            }
        }
        return Page(request, result.questions, result.total)
    }
    private fun updateFooter() {
        updatingControls = true
        val page = pages.getValue(mode); val spinner = pageSpinner.model as SpinnerNumberModel
        spinner.maximum = maxOf(page.currentPage, page.totalPages); spinner.value = page.currentPage
        pageSize.selectedItem = page.pageSize.value; pageLabel.text = "/ ${page.totalPages} (${page.totalSize})"
        updatingControls = false
    }
    private fun tagChoices(): List<TagSelection> = metadata?.tags.orEmpty().flatMap { group -> group.tagRelation.map {
        TagSelection(it.tag.nameTranslated?.takeIf(String::isNotEmpty) ?: it.tag.name, it.tag.slug, group.name, it.tag)
    } }
    override fun items(kind: FilterKind): List<FilterItem> {
        if (kind !in activeFilterKinds) return emptyList()
        val data = metadata ?: return emptyList()
        return when (kind) {
            FilterKind.Category -> if (mode == QueryMode.QueryParameters) data.categories.map { FilterItem(it.slug, it.title) } else emptyList()
            FilterKind.Favorite -> if (mode == QueryMode.QueryParameters) data.favorites.map { FilterItem(it.id, it.name) } else emptyList()
            FilterKind.Difficulty -> listOf(ChallengeDifficulty.Easy, ChallengeDifficulty.Medium, ChallengeDifficulty.Hard).map { FilterItem(it.value, it.show) }
            FilterKind.Status -> ChallengeStatus.entries.map { FilterItem(it.value, it.show) }
            FilterKind.Tags -> tagChoices().map { FilterItem(it.value, it.name, group = it.groupValue) }
            FilterKind.Company -> if (mode == QueryMode.CompanyQuery) data.companies.map { FilterItem(it.slug, it.name) } else emptyList()
            FilterKind.Position -> if (mode == QueryMode.CompanyQuery) data.positions.map { FilterItem(it.slug, it.nameTranslated ?: it.name) } else emptyList()
            FilterKind.InterviewPeriod -> if (mode == QueryMode.CompanyQuery) InterviewPeriod.entries.map { FilterItem(it.slug, it.show) } else emptyList()
            else -> emptyList()
        }
    }
    override fun selected(kind: FilterKind): Set<String> {
        if (kind !in activeFilterKinds) return emptySet()
        return when (kind) {
            FilterKind.Category -> setOfNotNull(parameters.selectedCategory?.slug)
            FilterKind.Favorite -> setOfNotNull(parameters.selectedFavorite?.id)
            FilterKind.Company -> company.selectedCompanies.map { it.slug }.toSet()
            FilterKind.Position -> company.selectedPositions.map { it.slug }.toSet()
            FilterKind.InterviewPeriod -> setOf(company.selectedInterviewPeriod.slug)
            FilterKind.Difficulty -> setOfNotNull((if (mode == QueryMode.CompanyQuery) company.selectedDifficulty else parameters.selectedDifficulty)?.value)
            FilterKind.Status -> setOfNotNull((if (mode == QueryMode.CompanyQuery) company.selectedStatus else parameters.selectedStatus)?.value)
            FilterKind.Tags -> (if (mode == QueryMode.CompanyQuery) company.selectedTags else parameters.selectedTags).map { it.value }.toSet()
            else -> emptySet()
        }
    }
    override fun toggle(kind: FilterKind, id: String) {
        if (kind !in activeFilterKinds) return
        val data = metadata ?: return
        when (kind) {
            FilterKind.Category -> parameters = parameters.copy(selectedCategory = toggleSingle(parameters.selectedCategory, data.categories, id) { it.slug })
            FilterKind.Favorite -> parameters = parameters.copy(selectedFavorite = toggleSingle(parameters.selectedFavorite, data.favorites, id) { it.id })
            FilterKind.Company -> company = company.copy(selectedCompanies = toggleMultiple(company.selectedCompanies.toList(), data.companies, id) { it.slug }.toSet())
            FilterKind.Position -> company = company.copy(selectedPositions = toggleMultiple(company.selectedPositions, data.positions, id) { it.slug })
            FilterKind.InterviewPeriod -> InterviewPeriod.entries.find { it.slug == id }?.let { company = company.copy(selectedInterviewPeriod = it) }
            FilterKind.Difficulty -> {
                val choices = listOf(ChallengeDifficulty.Easy, ChallengeDifficulty.Medium, ChallengeDifficulty.Hard)
                if (mode == QueryMode.CompanyQuery) company = company.copy(selectedDifficulty = toggleSingle(company.selectedDifficulty, choices, id) { it.value })
                else parameters = parameters.copy(selectedDifficulty = toggleSingle(parameters.selectedDifficulty, choices, id) { it.value })
            }
            FilterKind.Status -> if (mode == QueryMode.CompanyQuery) company = company.copy(selectedStatus = toggleSingle(company.selectedStatus, ChallengeStatus.entries, id) { it.value })
                else parameters = parameters.copy(selectedStatus = toggleSingle(parameters.selectedStatus, ChallengeStatus.entries, id) { it.value })
            FilterKind.Tags -> if (mode == QueryMode.CompanyQuery) company = company.copy(selectedTags = toggleMultiple(company.selectedTags, tagChoices(), id) { it.value })
                else parameters = parameters.copy(selectedTags = toggleMultiple(parameters.selectedTags, tagChoices(), id) { it.value })
            else -> return
        }
        request(true)
    }
    private fun updateTags() {
        tagsPanel.removeAll()
        activeFilterKinds.forEach { kind -> val choices = items(kind).associateBy { it.id }
            selected(kind).map { choices[it] ?: FilterItem(it, it) }.forEach { item ->
            tagsPanel.add(filterChip(kind, item) { toggle(kind, item.id) })
        } }; tagsPanel.revalidate(); tagsPanel.repaint()
    }
    private fun selectedRows(): List<LeetCodeChallengeListItem> = table.selectedRows.asSequence().mapNotNull { model.rows.getOrNull(it) }.toList()
    override val canOpenSelection: Boolean get() = selectedRows().any { !it.paidOnly || userInfo?.isPremium == true }
    override fun openSelection(language: Language, version: LanguageVersion) {
        selectedRows().filter { !it.paidOnly || userInfo?.isPremium == true }.forEach { item -> scope.launch {
            try { openChallenge(project, dojo, item.titleSlug, language, version) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { LogConsole.getInstance(project).error(dojo, failure.message.orEmpty()) }
        } }
    }
    private inner class ProblemTable : AbstractTableModel() {
        var rows = emptyList<LeetCodeChallengeListItem>()
        private val premium get() = userInfo?.isPremium == true
        val fields: List<String?> get() = listOf(null, "FRONTEND_ID", "DIFFICULTY") +
            (if (dojo == CodeDojo.LeetCodeCN && mode != QueryMode.CompanyQuery) listOf("SOLUTION_NUM") else emptyList()) +
            (if (premium) listOf("FREQUENCY") else emptyList()) + "AC_RATE"
        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = fields.size
        override fun getColumnName(column: Int): String {
            val field = fields[column]
            val title = when (field) {
                null -> ""; "FRONTEND_ID" -> "Title"; "DIFFICULTY" -> "Difficulty"; "SOLUTION_NUM" -> "Solutions"; "FREQUENCY" -> "Frequency"; else -> "Acceptance"
            }
            return sortedHeader(title, field, order())
        }
        override fun getValueAt(row: Int, column: Int): Any? {
            val item = rows[row]
            return when (fields[column]) {
                null -> if (item.paidOnly && !premium) AllIcons.Diff.Lock else when (ChallengeStatus.fromApi(item.status)) {
                    ChallengeStatus.Solved -> AllIcons.General.InspectionsOK; ChallengeStatus.Tried -> AllIcons.General.Modified; else -> null
                }
                "FRONTEND_ID" -> "[${item.frontendQuestionId}]${item.titleCn?.takeIf { it.isNotEmpty() } ?: item.title}"
                "DIFFICULTY" -> ChallengeDifficulty.fromApi(item.difficulty).showAsHtml()
                "SOLUTION_NUM" -> item.solutionNum ?: 0
                "FREQUENCY" -> item.freqBar ?: 0.0
                else -> "%.2f%%".format(item.acRate * if (dojo == CodeDojo.LeetCodeCN) 100 else 1)
            }
        }
    }
    override fun dispose() { closed = true; query.close(); childJob.cancel() }
}
