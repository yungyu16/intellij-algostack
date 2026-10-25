package io.github.yungyu16.algostack.presentation

import io.github.yungyu16.algostack.domain.ChallengeDifficulty
import io.github.yungyu16.algostack.domain.OrderDirection
import io.github.yungyu16.algostack.presentation.problems.CompanyCriteria
import io.github.yungyu16.algostack.presentation.problems.ProblemCriteria
import io.github.yungyu16.algostack.presentation.problems.QueryMode
import io.github.yungyu16.algostack.presentation.problems.QueryOrder
import org.junit.Assert.*
import org.junit.Test
import java.awt.Point
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel

class FilterPresentationTest {
    @Test
    fun reorderedColumnsKeepTheirOwnHeaderAndSortField() {
        SwingUtilities.invokeAndWait {
            val model = object : DefaultTableModel(arrayOf("Title", "Difficulty"), 1) {
                override fun getColumnName(column: Int) = if (column == 0) "Title ↑" else "Difficulty"
            }
            val table = JTable(model)
            table.moveColumn(0, 1)
            refreshHeaders(table)
            assertEquals("Difficulty", table.columnModel.getColumn(0).headerValue)
            assertEquals("Title ↑", table.columnModel.getColumn(1).headerValue)
            assertEquals(1, modelColumnAt(table, Point(1, 1)))
            assertEquals(0, modelColumnAt(table, Point(table.columnModel.getColumn(0).width + 1, 1)))
            assertEquals(-1, modelColumnAt(table, Point(-1, 1)))
        }
    }

    @Test
    fun togglingOneSelectionPreservesOtherStaleSelections() {
        val selected = listOf("old-a", "old-b", "current")
        val available = listOf("current", "new")
        assertEquals(listOf("old-b", "current"), toggleMultiple(selected, available, "old-a") { it })
        assertEquals(selected + "new", toggleMultiple(selected, available, "new") { it })
        assertEquals(selected, toggleMultiple(selected, available, "unknown") { it })
        assertNull(toggleSingle("old-a", available, "old-a") { it })
        assertEquals("current", toggleSingle("old-a", available, "current") { it })
        assertEquals("old-a", toggleSingle("old-a", available, "unknown") { it })
    }

    @Test
    fun menusRetainMissingSelectedOptionsSoTheyCanBeRemoved() {
        val choices = filterChoices(listOf(FilterItem("java", "Java")), linkedSetOf("java", "old-language"))
        assertEquals(listOf("java", "old-language"), choices.map { it.id })
        assertEquals("Java", choices.first().text)
        assertFalse(FilterKind.InterviewPeriod.clearable)
        assertTrue(FilterKind.Language.clearable)
        assertTrue(FilterKind.Dojo.clearable)
    }

    @Test
    fun changingSearchRestoresRelevanceButRepaintingDoesNotResetChosenSort() {
        val order = QueryOrder("FRONTEND_ID", OrderDirection.Ascending)
        val original = ProblemCriteria(keyword = "62", orderBy = order)
        assertSame(original, original.withKeyword("62"))
        assertEquals(ProblemCriteria(keyword = "26"), original.withKeyword("26"))
        assertEquals(ProblemCriteria(keyword = ""), original.withKeyword(""))
        assertEquals("Title ↑", sortedHeader("Title", "FRONTEND_ID", order))
        assertEquals("Title ↓", sortedHeader("Title", "FRONTEND_ID", order.copy(direction = OrderDirection.Descending)))
        assertEquals("Difficulty", sortedHeader("Difficulty", "DIFFICULTY", order))
        assertEquals("Title", sortedHeader("Title", "FRONTEND_ID", null))
    }

    @Test
    fun keywordAndFiltersTravelInOneRequest() {
        val criteria = ProblemCriteria(selectedDifficulty = ChallengeDifficulty.Medium, keyword = "two sum")
        val filters = criteria.api()
        assertEquals("two sum", filters.keyword)
        assertEquals("MEDIUM", filters.difficulty)
    }

    @Test
    fun ordinaryQuestionsNeverShowCompanyOnlyFilters() {
        assertEquals(
            setOf(FilterKind.Category, FilterKind.Favorite, FilterKind.Difficulty, FilterKind.Status, FilterKind.Tags),
            QueryMode.QueryParameters.filterKinds
        )
        assertFalse(FilterKind.InterviewPeriod in QueryMode.QueryParameters.filterKinds)
        assertFalse(FilterKind.Company in QueryMode.QueryParameters.filterKinds)
        assertFalse(FilterKind.Position in QueryMode.QueryParameters.filterKinds)
    }

    @Test
    fun companyModeKeepsItsOwnFilters() {
        // 隐藏不等于重置，切回公司模式仍可以恢复原来的范围。
        val criteria = CompanyCriteria()
        assertEquals("thirty-days", criteria.selectedInterviewPeriod.slug)
        assertTrue(FilterKind.InterviewPeriod in QueryMode.CompanyQuery.filterKinds)
        assertFalse(FilterKind.Category in QueryMode.CompanyQuery.filterKinds)
        assertFalse(FilterKind.Favorite in QueryMode.CompanyQuery.filterKinds)
    }

    @Test
    fun mandatoryPeriodHasNoFakeRemoveButtonWhileOptionalFiltersRemainRemovable() {
        SwingUtilities.invokeAndWait {
            var removed = 0
            val period = filterChip(FilterKind.InterviewPeriod, FilterItem("thirty-days", "30 days")) { removed++ }
            assertTrue(period is JLabel)
            assertEquals("30 days", (period as JLabel).text)
            assertEquals(0, removed)
            val optional = filterChip(FilterKind.Difficulty, FilterItem("easy", "Easy")) { removed++ }
            assertTrue(optional is JButton)
            assertEquals("Easy ×", (optional as JButton).text)
            optional.doClick()
            assertEquals(1, removed)
        }
    }
}
