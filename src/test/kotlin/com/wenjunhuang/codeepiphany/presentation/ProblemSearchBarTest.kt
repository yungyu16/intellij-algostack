package com.wenjunhuang.codeepiphany.presentation

import com.wenjunhuang.codeepiphany.domain.ChallengeDifficulty
import com.wenjunhuang.codeepiphany.domain.OrderDirection
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
