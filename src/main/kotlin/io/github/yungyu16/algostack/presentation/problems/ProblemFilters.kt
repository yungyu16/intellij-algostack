package io.github.yungyu16.algostack.presentation.problems

import io.github.yungyu16.algostack.domain.ChallengeDifficulty
import io.github.yungyu16.algostack.domain.ChallengeStatus
import io.github.yungyu16.algostack.leetcode.model.*
import io.github.yungyu16.algostack.presentation.*

internal data class ProblemFilterMetadata(
    val categories: List<LeetCodeCategoryListItem>,
    val favorites: List<LeetCodeFavoriteItem>,
    val tags: List<LeetCodeTagTypeWithTags>,
    val companies: List<LeetCodeQuestionCompanyTag>,
    val positions: List<LeetCodeProblemsetPositionTag>
)

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
        set(value) {
            criteria.parameters = value
        }
    private var company
        get() = criteria.company
        set(value) {
            criteria.company = value
        }

    /** Difficulty/Status/Tags 为两套条件共有维度；按当前模式读写对应侧。 */
    private val shared: SharedFilterDimensions
        get() = if (mode == QueryMode.CompanyQuery) company else parameters

    private fun tagChoices(): List<TagSelection> = metadata?.tags.orEmpty().flatMap { group ->
        group.tagRelation.map {
            TagSelection(
                it.tag.nameTranslated?.takeIf(String::isNotEmpty) ?: it.tag.name,
                it.tag.slug,
                group.name,
                it.tag
            )
        }
    }

    override fun items(kind: FilterKind): List<FilterItem> {
        if (kind !in activeFilterKinds) return emptyList()
        val data = metadata ?: return emptyList()
        return when (kind) {
            FilterKind.Category -> data.categories.map {
                FilterItem(
                    it.slug,
                    it.title
                )
            }

            FilterKind.Favorite -> data.favorites.map {
                FilterItem(
                    it.id,
                    it.name
                )
            }

            FilterKind.Difficulty -> listOf(
                ChallengeDifficulty.Easy,
                ChallengeDifficulty.Medium,
                ChallengeDifficulty.Hard
            ).map { FilterItem(it.value, it.show) }

            FilterKind.Status -> ChallengeStatus.entries.map { FilterItem(it.value, it.show) }
            FilterKind.Tags -> tagChoices().map { FilterItem(it.value, it.name, group = it.groupValue) }
            FilterKind.Company -> data.companies.map {
                FilterItem(
                    it.slug,
                    it.name
                )
            }

            FilterKind.Position -> data.positions.map {
                FilterItem(
                    it.slug,
                    it.nameTranslated ?: it.name
                )
            }

            FilterKind.InterviewPeriod -> InterviewPeriod.entries.map {
                FilterItem(
                    it.slug,
                    it.show
                )
            }
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
            FilterKind.Difficulty -> setOfNotNull(shared.selectedDifficulty?.value)
            FilterKind.Status -> setOfNotNull(shared.selectedStatus?.value)
            FilterKind.Tags -> shared.selectedTags.map { it.value }
                .toSet()
        }
    }

    override fun toggle(kind: FilterKind, id: String) {
        if (kind !in activeFilterKinds) return
        val data = metadata ?: return
        when (kind) {
            FilterKind.Category -> parameters = parameters.copy(
                selectedCategory = toggleSingle(
                    parameters.selectedCategory,
                    data.categories,
                    id
                ) { it.slug })

            FilterKind.Favorite -> parameters = parameters.copy(
                selectedFavorite = toggleSingle(
                    parameters.selectedFavorite,
                    data.favorites,
                    id
                ) { it.id })

            FilterKind.Company -> company = company.copy(
                selectedCompanies = toggleMultiple(
                    company.selectedCompanies.toList(),
                    data.companies,
                    id
                ) { it.slug }.toSet()
            )

            FilterKind.Position -> company = company.copy(
                selectedPositions = toggleMultiple(
                    company.selectedPositions,
                    data.positions,
                    id
                ) { it.slug })

            FilterKind.InterviewPeriod -> InterviewPeriod.entries.find { it.slug == id }
                ?.let { company = company.copy(selectedInterviewPeriod = it) }

            FilterKind.Difficulty -> {
                val choices = listOf(ChallengeDifficulty.Easy, ChallengeDifficulty.Medium, ChallengeDifficulty.Hard)
                val next = toggleSingle(shared.selectedDifficulty, choices, id) { it.value }
                if (mode == QueryMode.CompanyQuery) company = company.copy(selectedDifficulty = next)
                else parameters = parameters.copy(selectedDifficulty = next)
            }

            FilterKind.Status -> {
                val next = toggleSingle(shared.selectedStatus, ChallengeStatus.entries, id) { it.value }
                if (mode == QueryMode.CompanyQuery) company = company.copy(selectedStatus = next)
                else parameters = parameters.copy(selectedStatus = next)
            }

            FilterKind.Tags -> {
                val next = toggleMultiple(shared.selectedTags, tagChoices(), id) { it.value }
                if (mode == QueryMode.CompanyQuery) company = company.copy(selectedTags = next)
                else parameters = parameters.copy(selectedTags = next)
            }
        }
        changed(kind, id)
    }

}
