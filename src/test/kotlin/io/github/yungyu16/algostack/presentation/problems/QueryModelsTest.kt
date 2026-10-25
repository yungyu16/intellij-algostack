package io.github.yungyu16.algostack.presentation.problems

import io.github.yungyu16.algostack.domain.ChallengeDifficulty
import io.github.yungyu16.algostack.domain.ChallengeStatus
import io.github.yungyu16.algostack.domain.OrderDirection
import io.github.yungyu16.algostack.leetcode.LeetCodeClient
import io.github.yungyu16.algostack.leetcode.LeetCodeJson
import io.github.yungyu16.algostack.leetcode.model.*
import io.github.yungyu16.algostack.presentation.show
import io.github.yungyu16.algostack.presentation.showAsHtml
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.*
import org.junit.Test

class QueryModelsTest {
    private val tag = TagSelection("数组", "array", "基础", LeetCodeTag(name = "Array", slug = "array"))
    private val order = QueryOrder("FRONTEND_ID", OrderDirection.Descending)

    @Test
    fun problemCriteriaMapToApiFiltersWithoutSavedJson() {
        assertEquals(LeetCodeClient.Filters(), ProblemCriteria().api())
        val criteria = ProblemCriteria(
            selectedCategory = LeetCodeCategoryListItem("算法", "/problemset/algorithms/", "algorithms"),
            selectedFavorite = LeetCodeFavoriteItem("favorite-id", "收藏", "NORMAL"),
            selectedDifficulty = ChallengeDifficulty.Hard,
            selectedStatus = ChallengeStatus.Tried,
            selectedTags = listOf(tag),
            orderBy = order,
            keyword = "two sum"
        )
        assertEquals(
            LeetCodeClient.Filters(
                "algorithms", "favorite-id", "HARD", "TRIED", listOf("array"),
                LeetCodeClient.Sort("FRONTEND_ID", "DESCENDING"), "two sum"
            ), criteria.api()
        )
        assertSame(criteria, criteria.withKeyword("two sum"))
        assertEquals(criteria.copy(keyword = "new", orderBy = null), criteria.withKeyword("new"))
    }

    @Test
    fun companyCriteriaKeepScopeAndMapSharedFilters() {
        val criteria = CompanyCriteria(
            selectedCompanies = setOf(LeetCodeQuestionCompanyTag("公司", "company", "1", 10)),
            selectedPositions = listOf(LeetCodeProblemsetPositionTag("Backend", "后端", "backend")),
            selectedDifficulty = ChallengeDifficulty.Medium,
            selectedStatus = ChallengeStatus.Solved,
            selectedInterviewPeriod = InterviewPeriod.All,
            selectedTags = listOf(tag),
            orderBy = order,
            keyword = "sum"
        )
        assertEquals(
            LeetCodeClient.Filters(
                difficulty = "MEDIUM", status = "AC", tags = listOf("array"),
                order = LeetCodeClient.Sort("FRONTEND_ID", "DESCENDING"), keyword = "sum"
            ), criteria.api()
        )
        assertSame(criteria, criteria.withKeyword("sum"))
        assertEquals(criteria.copy(keyword = "", orderBy = null), criteria.withKeyword(""))
        assertEquals(LeetCodeClient.Filters(), CompanyCriteria().api())
        assertEquals(InterviewPeriod.ThirtyDays, CompanyCriteria().selectedInterviewPeriod)
        assertEquals(
            listOf("thirty-days", "three-months", "six-months", "more-than-six-months", "all"),
            InterviewPeriod.entries.map { it.slug })
        assertEquals(listOf("ui.query.parameters", "ui.query.company"), QueryMode.entries.map { it.label })
    }

    @Test
    fun queryMetadataRemainsSerializableAsRealApiDtos() {
        val json = LeetCodeJson.format
        val category = LeetCodeCategoryListItem("算法", "/problemset/algorithms/", "algorithms")
        val favorite = LeetCodeFavoriteItem("id", "收藏", "NORMAL")
        val company = LeetCodeQuestionCompanyTag("公司", "company", "1", 10)
        val position = LeetCodeProblemsetPositionTag("Backend", "后端", "backend")
        assertEquals(tag.userObj, json.decodeFromJsonElement<LeetCodeTag>(json.encodeToJsonElement(tag.userObj)))
        assertEquals(category, json.decodeFromJsonElement<LeetCodeCategoryListItem>(json.encodeToJsonElement(category)))
        assertEquals(favorite, json.decodeFromJsonElement<LeetCodeFavoriteItem>(json.encodeToJsonElement(favorite)))
        assertEquals(company, json.decodeFromJsonElement<LeetCodeQuestionCompanyTag>(json.encodeToJsonElement(company)))
        assertEquals(
            position,
            json.decodeFromJsonElement<LeetCodeProblemsetPositionTag>(json.encodeToJsonElement(position))
        )
    }

    @Test
    fun supportedDifficultiesKeepTheirPresentationColors() {
        val colors = mapOf(
            ChallengeDifficulty.Easy to "#4CAF50",
            ChallengeDifficulty.Medium to "#FFC107",
            ChallengeDifficulty.Hard to "#F44336"
        )
        for ((difficulty, color) in colors) {
            assertTrue(difficulty.show.isNotBlank())
            assertEquals("<html><font color='$color'>${difficulty.show}</font></html>", difficulty.showAsHtml())
        }
    }
}
