package com.wenjunhuang.algostack.presentation

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.sun.net.httpserver.HttpServer
import com.wenjunhuang.algostack.domain.*
import com.wenjunhuang.algostack.leetcode.api.LeetCodeClient
import com.wenjunhuang.algostack.leetcode.api.model.*
import com.wenjunhuang.algostack.presentation.actions.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel

/** 调用真正的菜单动作和视图使用的 host / 请求加载器，不访问站点、认证服务或凭据。 */
class ProblemFilterPipelineTest {
    companion object {
        @org.junit.BeforeClass @JvmStatic fun initializeActionSystem() {
            com.intellij.testFramework.TestApplicationManager.getInstance()
        }
    }
    private class Fixture : AutoCloseable {
        val bodies = CopyOnWriteArrayList<JsonObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val response = if (exchange.requestURI.path == "/api/home/") "{}" else {
                    val body = Json.parseToJsonElement(exchange.requestBody.bufferedReader().readText()).jsonObject
                    bodies += body
                    val company = body.getValue("operationName").jsonPrimitive.content == "favoriteQuestionList"
                    val field = if (company) "questionFrontendId" else "frontendQuestionId"
                    val root = if (company) "favoriteQuestionList" else "problemsetQuestionList"
                    """{"data":{"$root":{"total":1,"questions":[{"acRate":0.5,"difficulty":"Easy","paidOnly":false,"$field":"1","title":"Fixture","titleSlug":"fixture"}]}}}"""
                }
                exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
                exchange.responseBody.use { it.write(response.toByteArray()) }
                exchange.close()
            }
            start()
        }
        val client = OkHttpClient()
        val api = LeetCodeClient("leetcode.cn", client, "http://127.0.0.1:${server.address.port}/".toHttpUrl())
        override fun close() { server.stop(0); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    }
    private val metadata = ProblemFilterMetadata(
        listOf(LeetCodeCategoryListItem("算法", "/", "algorithms")),
        listOf(LeetCodeFavoriteItem("favorite-fixture", "题单", "1")),
        listOf(LeetCodeTagTypeWithTags("topic", tagRelation = listOf(LeetCodeTagRelation(1, LeetCodeTag(name = "数组", slug = "array"))))),
        listOf(LeetCodeQuestionCompanyTag("公司", "company-a", "1", 1), LeetCodeQuestionCompanyTag("公司B", "company-b", "2", 1)),
        listOf(LeetCodeProblemsetPositionTag("Backend", slug = "backend")))

    private fun choose(host: FilterHost, action: FilterAction, text: String, selected: Boolean, perform: Boolean = false) {
        assertTrue(SwingUtilities.isEventDispatchThread())
        val context = DataContext { key -> if (key == ActionContext.FILTERS.name) host else null }
        fun children(group: ActionGroup): List<AnAction> = group.getChildren(null).flatMap {
            if (it is ActionGroup) children(it) else listOf(it)
        }
        val item = children(action.createPopupActionGroup(JPanel(), context)).single { it.templatePresentation.text == text } as CheckboxAction
        // 弹窗事件没有 FILTERS 时仍应使用创建菜单时捕获的 host。
        val event = AnActionEvent.createFromAnAction(item, null, "test", DataContext.EMPTY_CONTEXT)
        if (perform) item.actionPerformed(event) else item.setSelected(event, selected)
        assertEquals(selected, item.isSelected(event))
    }

    @Test fun ordinaryActionsUpdateRealHostAndSendCombinedAndClearedHttpFilters() = runBlocking {
        Fixture().use { fixture ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val state = ProblemQueryState()
            val query = LatestQuery<ProblemPageRequest, ProblemPage>(scope, 0) { it.load(fixture.api) }
            var job: Job? = null
            var submitted: ProblemPageRequest? = null
            val host = ProblemFilters(state) { _, _ ->
                state.pages[state.mode] = state.pages.getValue(state.mode).resetToFirstPage()
                val snapshot = ProblemPageRequest(state.mode, state.parameters, state.company, state.pages.getValue(state.mode))
                submitted = snapshot
                job = query.submit(snapshot)
            }.apply { this.metadata = this@ProblemFilterPipelineTest.metadata }
            try {
                SwingUtilities.invokeAndWait {
                    state.submitKeyword("committed")
                    state.pages[state.mode] = Pagination(currentPage = 3, totalSize = 100)
                    choose(host, CategoryAction(), "算法", true, perform = true)
                    choose(host, FavoriteAction(), "题单", true)
                    choose(host, DifficultyAction(), ChallengeDifficulty.Easy.show, true)
                    choose(host, StatusAction(), ChallengeStatus.Solved.show, true)
                    choose(host, TagsAction(), "数组", true)
                    val previousJob = job
                    choose(host, TagsAction(), "数组", true)
                    assertSame(previousJob, job)
                    val bar = ProblemSearchBar({}, state::submitKeyword)
                    bar.keywordField.text = "unsubmitted draft"
                    assertEquals("committed", state.parameters.keyword)
                }
                withTimeout(5000) { job!!.join() }
                val success = query.states.first { it is QueryState.Success } as QueryState.Success
                val result = success.value
                SwingUtilities.invokeAndWait {
                    assertEquals(submitted, result.request)
                    val table = DefaultTableModel(arrayOf("Title"), 0)
                    result.rows.forEach { table.addRow(arrayOf(it.title)) }
                    assertEquals("Fixture", table.getValueAt(0, 0))
                    assertEquals(1, result.total)
                    assertEquals(1, state.pages.getValue(state.mode).currentPage)
                    assertEquals("algorithms", state.parameters.selectedCategory!!.slug)
                }
                val variables = fixture.bodies.last().getValue("variables").jsonObject
                assertEquals("algorithms", variables.getValue("categorySlug").jsonPrimitive.content)
                assertEquals(0, variables.getValue("skip").jsonPrimitive.int)
                val filters = variables.getValue("filters").jsonObject
                assertEquals("committed", filters.getValue("searchKeywords").jsonPrimitive.content)
                assertEquals("favorite-fixture", filters.getValue("listId").jsonPrimitive.content)
                assertEquals("EASY", filters.getValue("difficulty").jsonPrimitive.content)
                assertEquals("AC", filters.getValue("status").jsonPrimitive.content)
                assertEquals("array", filters.getValue("tags").jsonArray.single().jsonPrimitive.content)
                SwingUtilities.invokeAndWait {
                    choose(host, CategoryAction(), "算法", false)
                    choose(host, FavoriteAction(), "题单", false)
                    choose(host, TagsAction(), "数组", false, perform = true)
                    choose(host, DifficultyAction(), ChallengeDifficulty.Easy.show, false)
                    choose(host, StatusAction(), ChallengeStatus.Solved.show, false)
                }
                withTimeout(5000) { job!!.join() }
                val cleared = fixture.bodies.last().getValue("variables").jsonObject
                assertEquals("all-code-essentials", cleared.getValue("categorySlug").jsonPrimitive.content)
                assertEquals(setOf("searchKeywords"), cleared.getValue("filters").jsonObject.keys)
                // 已提交的快照不随之后的取消选择而变化。
                assertEquals("EASY", result.request.parameters.api().difficulty)
            } finally { query.close(); scope.cancel() }
        }
    }

    @Test fun companyActionsKeepPeriodAndSendModernFiltersWithoutOrdinaryScopeLeakage() = runBlocking {
        Fixture().use { fixture ->
            val state = ProblemQueryState()
            val host = ProblemFilters(state) { _, _ -> }.apply { metadata = this@ProblemFilterPipelineTest.metadata }
            SwingUtilities.invokeAndWait {
                choose(host, CategoryAction(), "算法", true)
                choose(host, FavoriteAction(), "题单", true)
                state.switchTo(QueryMode.CompanyQuery)
                state.submitKeyword("company keyword")
                choose(host, CompanyAction(), "公司", true)
                choose(host, CompanyAction(), "公司B", true)
                choose(host, PositionAction(), "Backend", true)
                choose(host, InterviewPeriodAction(), InterviewPeriod.All.show, true)
                choose(host, InterviewPeriodAction(), InterviewPeriod.All.show, true, perform = true)
                choose(host, DifficultyAction(), ChallengeDifficulty.Medium.show, true)
                choose(host, StatusAction(), ChallengeStatus.Unsolved.show, true)
                choose(host, TagsAction(), "数组", true)
                assertTrue(host.items(FilterKind.Category).isEmpty())
            }
            val empty = ProblemPageRequest(QueryMode.CompanyQuery, state.parameters, CompanyCriteria(), Pagination()).load(fixture.api)
            assertEquals(0, empty.total)
            assertTrue(fixture.bodies.isEmpty())
            val request = ProblemPageRequest(state.mode, state.parameters, state.company, state.pages.getValue(state.mode))
            val result = request.load(fixture.api)
            assertEquals("Fixture", result.rows.single().title)
            val vars = fixture.bodies.single().getValue("variables").jsonObject
            assertEquals("company-a-all", vars.getValue("favoriteSlug").jsonPrimitive.content)
            assertEquals("company keyword", vars.getValue("searchKeyword").jsonPrimitive.content)
            assertFalse(vars.containsKey("categorySlug"))
            val filters = vars.getValue("filtersV2").jsonObject
            for ((kind, field, value) in listOf(
                Triple("companyFilter", "companySlugs", "company-b"), Triple("positionFilter", "positionSlugs", "backend"),
                Triple("difficultyFilter", "difficulties", "MEDIUM"), Triple("statusFilter", "questionStatuses", "TO_DO"),
                Triple("topicFilter", "topicSlugs", "array"))) {
                assertEquals(value, filters.getValue(kind).jsonObject.getValue(field).jsonArray.single().jsonPrimitive.content)
            }
            SwingUtilities.invokeAndWait {
                state.switchTo(QueryMode.QueryParameters)
                assertEquals(setOf("algorithms"), host.selected(FilterKind.Category))
                assertEquals(setOf("favorite-fixture"), host.selected(FilterKind.Favorite))
                assertTrue(host.selected(FilterKind.Tags).isEmpty())
            }
        }
    }
}
