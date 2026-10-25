package io.github.yungyu16.algostack.presentation.problems

import io.github.yungyu16.algostack.domain.Pagination
import io.github.yungyu16.algostack.leetcode.LeetCodeClient
import io.github.yungyu16.algostack.leetcode.model.LeetCodeChallengeList
import io.github.yungyu16.algostack.leetcode.model.LeetCodeChallengeListItem

/** 不可变请求快照；后台加载不能读取视图正在变更的条件。 */
internal data class ProblemPageRequest(
    val mode: QueryMode,
    val parameters: ProblemCriteria,
    val company: CompanyCriteria,
    val page: Pagination
) {
    suspend fun load(api: LeetCodeClient): ProblemPage {
        val result = when (mode) {
            QueryMode.QueryParameters -> api.searchChallenges(page.offset, page.limit, parameters.api())
            QueryMode.CompanyQuery -> if (company.selectedCompanies.isEmpty()) LeetCodeChallengeList(
                emptyList(),
                0
            ) else {
                val criteria = company
                val result = api.searchCompanyChallenges(
                    page.offset,
                    page.limit,
                    criteria.selectedInterviewPeriod.slug,
                    criteria.selectedCompanies.map { it.slug },
                    criteria.selectedPositions.map { it.slug },
                    criteria.api()
                )
                LeetCodeChallengeList(result.questions.map {
                    LeetCodeChallengeListItem(
                        it.acRate, it.difficulty, it.freqBar,
                        it.paidOnly, null, it.status, it.questionFrontendId, it.title, it.titleCn, it.titleSlug
                    )
                }, result.total)
            }
        }
        return ProblemPage(this, result.questions, result.total)
    }
}

internal data class ProblemPage(
    val request: ProblemPageRequest,
    val rows: List<LeetCodeChallengeListItem>,
    val total: Int
)
