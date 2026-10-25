package io.github.yungyu16.algostack.domain

import org.junit.Assert.*
import org.junit.Test

class QueryDomainModelsTest {
    @Test
    fun difficultyOnlyAcceptsTheThreeLeetCodeLevels() {
        assertEquals(
            listOf(ChallengeDifficulty.Easy, ChallengeDifficulty.Medium, ChallengeDifficulty.Hard),
            ChallengeDifficulty.entries
        )
        val expected = listOf("easy" to "EASY", "medium" to "MEDIUM", "hard" to "HARD")
        for ((difficulty, values) in ChallengeDifficulty.entries.zip(expected)) {
            assertEquals(values.first, difficulty.value)
            assertEquals(values.second, difficulty.apiValue)
            assertEquals(difficulty, ChallengeDifficulty.fromString(values.first))
            assertEquals(difficulty, ChallengeDifficulty.fromApi(values.first))
            assertEquals(difficulty, ChallengeDifficulty.fromApi(values.second))
        }
        assertNull(ChallengeDifficulty.fromString(null))
        for (unsupported in listOf("", "advanced", "expert", "leetcodecn#easy", "UNKNOWN")) {
            assertNull(ChallengeDifficulty.fromString(unsupported))
            assertThrows(IllegalArgumentException::class.java) { ChallengeDifficulty.fromApi(unsupported) }
        }
    }

    @Test
    fun sortAndStatusKeepTheirApiValuesWithoutPersistence() {
        assertEquals("ASCENDING", OrderDirection.Ascending.apiValue)
        assertEquals("DESCENDING", OrderDirection.Descending.apiValue)
        assertEquals("AC", ChallengeStatus.Solved.apiValue)
        assertEquals("NOT_STARTED", ChallengeStatus.Unsolved.apiValue)
        assertEquals("TRIED", ChallengeStatus.Tried.apiValue)
        assertEquals(ChallengeStatus.Solved, ChallengeStatus.fromApi("SOLVED"))
        assertEquals(ChallengeStatus.Tried, ChallengeStatus.fromApi("ATTEMPTED"))
        assertEquals(ChallengeStatus.Unsolved, ChallengeStatus.fromApi(null))
    }

    @Test
    fun paginationKeepsItsInMemoryArithmeticAndReset() {
        assertEquals(listOf(20, 50, 100), PageSize.entries.map { it.value })
        assertEquals(Pagination(1, PageSize.Twenty, 0), Pagination())
        assertEquals(1, Pagination().totalPages)
        for (size in PageSize.entries) {
            val page = Pagination(currentPage = 3, pageSize = size, totalSize = size.value * 3 + 1)
            assertEquals(size.value * 2, page.offset)
            assertEquals(size.value, page.limit)
            assertEquals(4, page.totalPages)
            assertEquals(page.copy(currentPage = 1), page.resetToFirstPage())
            assertEquals(3, page.currentPage)
        }
        assertThrows(IllegalArgumentException::class.java) { Pagination(currentPage = 0) }
    }
}
