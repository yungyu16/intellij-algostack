package io.github.yungyu16.algostack.presentation

import io.github.yungyu16.algostack.domain.*
import io.github.yungyu16.algostack.leetcode.api.model.*

internal data class ProblemFilterMetadata(val categories: List<LeetCodeCategoryListItem>, val favorites: List<LeetCodeFavoriteItem>,
        val tags: List<LeetCodeTagTypeWithTags>, val companies: List<LeetCodeQuestionCompanyTag>, val positions: List<LeetCodeProblemsetPositionTag>)

/** 真实筛选 host：与视图共用唯一条件状态，动作修改后由视图提交查询。 */
internal class ProblemFilters(
    private val criteria: ProblemQueryState,
    private val changed: (FilterKind, String) -> Unit
) : FilterHost {
    var metadata: ProblemFilterMetadata? = null
    private val mode get() = criteria.mode
    private val activeFilterKinds get() = mode.filterKinds
    private var parameters
        get() = criteria.parameters
        set(value) { criteria.parameters = value }
    private var company
        get() = criteria.company
        set(value) { criteria.company = value }
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
        changed(kind, id)
    }

}
