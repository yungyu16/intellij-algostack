package com.wenjunhuang.algostack.presentation

import com.wenjunhuang.algostack.domain.ChallengeDifficulty
import com.wenjunhuang.algostack.domain.OrderDirection
import org.junit.Assert.*
import org.junit.Test
import java.awt.BorderLayout
import javax.swing.SwingUtilities

class ProblemSearchBarTest {
    @Test fun draftsNeverQueryAndEnterAndButtonSubmitTheSameCombinedConditions() {
        SwingUtilities.invokeAndWait {
            val state = ProblemQueryState()
            var requests = 0
            val bar = ProblemSearchBar({ state.switchTo(it) }) {
                state.submitKeyword(it)
                requests++
            }
            state.parameters = ProblemCriteria(selectedDifficulty = ChallengeDifficulty.Medium,
                keyword = "old", orderBy = QueryOrder("FRONTEND_ID", OrderDirection.Descending))
            state.pages[state.mode] = state.pages.getValue(state.mode).copy(currentPage = 3, totalSize = 100)
            bar.keywordField.text = "two sum"
            assertEquals(0, requests)
            assertEquals("old", state.parameters.api().keyword)
            assertEquals(3, state.pages.getValue(state.mode).currentPage)
            // 筛选与分页读取已提交条件，不能顺带提交正在编辑的文本。
            state.parameters = state.parameters.copy(selectedDifficulty = ChallengeDifficulty.Easy)
            assertEquals("old", state.parameters.api().keyword)
            state.pages[state.mode] = state.pages.getValue(state.mode).copy(currentPage = 2)
            assertEquals("old", state.parameters.api().keyword)
            bar.keywordField.postActionEvent()
            assertEquals(1, requests)
            assertEquals("two sum", state.parameters.api().keyword)
            assertEquals("EASY", state.parameters.api().difficulty)
            assertNull(state.parameters.orderBy)
            assertEquals(1, state.pages.getValue(state.mode).currentPage)
            state.pages[state.mode] = state.pages.getValue(state.mode).copy(currentPage = 3)
            bar.searchButton.doClick()
            assertEquals(2, requests)
            assertEquals("two sum", state.parameters.keyword)
            assertEquals(1, state.pages.getValue(state.mode).currentPage)
            bar.keywordField.text = ""
            assertEquals("two sum", state.parameters.keyword)
            bar.searchButton.doClick()
            assertEquals("", state.parameters.keyword)
            assertEquals(3, requests)
        }
    }

    @Test fun scopeSwitchRetainsIndependentDraftsAndFiltersButResetsPageAndSort() {
        SwingUtilities.invokeAndWait {
            val state = ProblemQueryState()
            lateinit var bar: ProblemSearchBar
            var searches = 0
            bar = ProblemSearchBar({ next -> state.switchTo(next); bar.showScope(next) }) {
                state.submitKeyword(it); searches++
            }
            val order = QueryOrder("FRONTEND_ID", OrderDirection.Ascending)
            state.parameters = ProblemCriteria(keyword = "committed", selectedDifficulty = ChallengeDifficulty.Easy, orderBy = order)
            state.company = CompanyCriteria(keyword = "company committed", selectedDifficulty = ChallengeDifficulty.Hard, orderBy = order)
            for (mode in QueryMode.entries) state.pages[mode] = state.pages.getValue(mode).copy(currentPage = 4, totalSize = 100)
            bar.keywordField.text = "all draft"
            bar.scopeSelector.selectedItem = QueryMode.CompanyQuery
            assertEquals(QueryMode.CompanyQuery, state.mode)
            assertEquals(1, state.pages.getValue(state.mode).currentPage)
            assertNull(state.company.orderBy)
            assertEquals(ChallengeDifficulty.Hard, state.company.selectedDifficulty)
            assertEquals("company committed", state.company.keyword)
            bar.keywordField.text = "company draft"
            bar.scopeSelector.selectedItem = QueryMode.QueryParameters
            assertEquals("all draft", bar.keywordField.text)
            assertEquals("committed", state.parameters.keyword)
            assertEquals(ChallengeDifficulty.Easy, state.parameters.selectedDifficulty)
            assertNull(state.parameters.orderBy)
            assertEquals(1, state.pages.getValue(state.mode).currentPage)
            bar.scopeSelector.selectedItem = QueryMode.CompanyQuery
            assertEquals("company draft", bar.keywordField.text)
            assertEquals(0, searches)
            bar.keywordField.postActionEvent()
            assertEquals("company draft", state.company.keyword)
            assertEquals("committed", state.parameters.keyword)
        }
    }

    @Test fun chineseControlsKeepPlatformIconsSelectionAndAccessibleNames() {
        SwingUtilities.invokeAndWait {
            val bar = ProblemSearchBar({}, {})
            assertEquals("搜索", bar.searchButton.text)
            assertSame(com.intellij.icons.AllIcons.Actions.Find, bar.searchButton.icon)
            assertEquals("搜索", bar.searchButton.accessibleContext.accessibleName)
            assertEquals("查询范围", bar.scopeSelector.accessibleContext.accessibleName)
            val list = javax.swing.JList<QueryMode>()
            list.selectionBackground = java.awt.Color.BLUE
            list.selectionForeground = java.awt.Color.WHITE
            val renderer = bar.scopeSelector.renderer
            val selected = renderer.getListCellRendererComponent(list, QueryMode.CompanyQuery, 1, true, true) as javax.swing.JLabel
            assertEquals("公司题单", selected.text)
            assertSame(com.intellij.icons.AllIcons.Nodes.ModuleGroup, selected.icon)
            assertEquals(list.selectionBackground, selected.background)
            assertEquals(list.selectionForeground, selected.foreground)
            assertEquals("公司题单", selected.accessibleContext.accessibleName)
            val ordinary = renderer.getListCellRendererComponent(list, QueryMode.QueryParameters, -1, false, false) as javax.swing.JLabel
            assertEquals("全部题目", ordinary.text)
            assertSame(com.intellij.icons.AllIcons.Actions.ListFiles, ordinary.icon)
            assertEquals(list.background, ordinary.background)
        }
    }

    @Test fun defaultAndChineseBundlesUseChineseOnlyForRequestedQueryLabels() {
        val expected = mapOf(
            "LeetCode.Category" to "分类", "LeetCode.Favorite" to "题单", "Difficulty" to "难度",
            "Status" to "状态", "Tags" to "标签", "LeetCode.Company" to "公司",
            "LeetCode.Position" to "职位", "LeetCode.InterviewPeriod" to "时间范围")
        for (suffix in listOf("", "_zh")) {
            val properties = java.util.Properties()
            javaClass.getResourceAsStream("/messages/PluginBundle$suffix.properties")!!.reader(Charsets.UTF_8).use { properties.load(it) }
            for ((key, text) in expected) assertEquals(text, properties.getProperty("action.AlgoStack.Actions.$key.text"))
            assertEquals("全部题目", properties.getProperty("ui.query.parameters"))
            assertEquals("公司题单", properties.getProperty("ui.query.company"))
            assertEquals("搜索", properties.getProperty("ui.query.search"))
        }
        val actions = listOf(
            com.wenjunhuang.algostack.presentation.actions.CategoryAction(), com.wenjunhuang.algostack.presentation.actions.FavoriteAction(),
            com.wenjunhuang.algostack.presentation.actions.DifficultyAction(), com.wenjunhuang.algostack.presentation.actions.StatusAction(),
            com.wenjunhuang.algostack.presentation.actions.TagsAction(), com.wenjunhuang.algostack.presentation.actions.CompanyAction(),
            com.wenjunhuang.algostack.presentation.actions.PositionAction(), com.wenjunhuang.algostack.presentation.actions.InterviewPeriodAction())
        actions.forEach { assertNotNull(it.templatePresentation.icon) }
    }

    @Test fun singleRowFitsShortWindowWithoutGrowingVertically() {
        SwingUtilities.invokeAndWait {
            val bar = ProblemSearchBar({}, {})
            bar.setSize(420, bar.preferredSize.height)
            bar.doLayout()
            assertTrue(bar.layout is BorderLayout)
            assertTrue(bar.keywordField.width > 40)
            assertTrue(bar.searchButton.bounds.maxX <= bar.width)
            assertTrue(bar.scopeSelector.bounds.maxX <= bar.keywordField.x)
            assertTrue(bar.keywordField.bounds.maxX <= bar.searchButton.x)
            assertTrue(bar.height < 60)
        }
    }
}
