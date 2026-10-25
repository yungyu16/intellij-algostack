package io.github.yungyu16.algostack.presentation.problems

import io.github.yungyu16.algostack.PluginBundle
import io.github.yungyu16.algostack.domain.ChallengeDifficulty
import io.github.yungyu16.algostack.domain.ChallengeStatus
import io.github.yungyu16.algostack.domain.OrderDirection
import io.github.yungyu16.algostack.leetcode.LeetCodeClient
import io.github.yungyu16.algostack.leetcode.model.*

data class QueryOrder(val field: String, val direction: OrderDirection) {
    fun api() = LeetCodeClient.Sort(field, direction.apiValue)
}

data class TagSelection(val name: String, val value: String, val groupValue: String, val userObj: LeetCodeTag)
data class ProblemCriteria(
    val selectedCategory: LeetCodeCategoryListItem? = null,
    val selectedFavorite: LeetCodeFavoriteItem? = null,
    val selectedDifficulty: ChallengeDifficulty? = null,
    val selectedStatus: ChallengeStatus? = null,
    val selectedTags: List<TagSelection> = emptyList(),
    val orderBy: QueryOrder? = null,
    val keyword: String = ""
) {
    fun api() = LeetCodeClient.Filters(
        selectedCategory?.slug ?: "all-code-essentials", selectedFavorite?.id,
        selectedDifficulty?.apiValue, selectedStatus?.apiValue, selectedTags.map { it.value }, orderBy?.api(), keyword
    )

    // 新关键词恢复相关度排序，避免旧的题号排序把命中结果挤到后页。
    fun withKeyword(value: String): ProblemCriteria =
        if (value == keyword) this else copy(keyword = value, orderBy = null)
}

enum class InterviewPeriod(val slug: String, private val label: String) {
    ThirtyDays("thirty-days", "30days"), ThreeMonths("three-months", "3Months"),
    SixMonths("six-months", "6Months"), MoreThanSixMonths("more-than-six-months", "moreThan6Months"), All("all", "all");

    val show: String get() = PluginBundle.message("leetcode.interviewPeriod.$label")
}

data class CompanyCriteria(
    val selectedCompanies: Set<LeetCodeQuestionCompanyTag> = emptySet(),
    val selectedPositions: List<LeetCodeProblemsetPositionTag> = emptyList(),
    val selectedDifficulty: ChallengeDifficulty? = null,
    val selectedStatus: ChallengeStatus? = null,
    val selectedInterviewPeriod: InterviewPeriod = InterviewPeriod.ThirtyDays,
    val selectedTags: List<TagSelection> = emptyList(),
    val orderBy: QueryOrder? = null,
    val keyword: String = ""
) {
    fun api() = LeetCodeClient.Filters(
        difficulty = selectedDifficulty?.apiValue,
        status = selectedStatus?.apiValue,
        tags = selectedTags.map { it.value },
        order = orderBy?.api(),
        keyword = keyword
    )

    // 新关键词恢复相关度排序，避免旧的题号排序把命中结果挤到后页。
    fun withKeyword(value: String): CompanyCriteria =
        if (value == keyword) this else copy(keyword = value, orderBy = null)
}

enum class QueryMode(val label: String) {
    QueryParameters("ui.query.parameters"),
    CompanyQuery("ui.query.company");

    val show: String get() = PluginBundle.message(label)
}
