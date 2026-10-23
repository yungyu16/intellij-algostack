package com.wenjunhuang.codeepiphany.presentation

import com.wenjunhuang.codeepiphany.PluginBundle
import com.wenjunhuang.codeepiphany.domain.*
import com.wenjunhuang.codeepiphany.leetcode.api.LeetCodeClient
import com.wenjunhuang.codeepiphany.leetcode.api.LeetCodeJson
import com.wenjunhuang.codeepiphany.leetcode.api.model.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable(with = QueryOrderSerializer::class)
data class QueryOrder(val field: String, val direction: OrderDirection) {
    fun api() = LeetCodeClient.Sort(field, direction.apiValue)
}
object QueryOrderSerializer : KSerializer<QueryOrder> {
    override val descriptor = buildClassSerialDescriptor("QueryOrder")
    override fun serialize(encoder: Encoder, value: QueryOrder) {
        (encoder as JsonEncoder).encodeJsonElement(buildJsonArray { add(value.field); add(value.direction.name) })
    }
    override fun deserialize(decoder: Decoder): QueryOrder {
        val values = (decoder as JsonDecoder).decodeJsonElement().jsonArray
        if (values.size != 2) throw SerializationException("Invalid saved orderBy tuple")
        return QueryOrder(values[0].jsonPrimitive.content, OrderDirection.fromString(values[1].jsonPrimitive.content)
            ?: throw SerializationException("Invalid order direction"))
    }
}
@Serializable
data class TagSelection(val name: String, val value: String, val groupValue: String, val userObj: LeetCodeTag)
@Serializable
data class ProblemCriteria(
    val selectedCategory: LeetCodeCategoryListItem? = null,
    val selectedFavorite: LeetCodeFavoriteItem? = null,
    val selectedDifficulty: ChallengeDifficulty? = null,
    val selectedStatus: ChallengeStatus? = null,
    @SerialName("tags") val selectedTags: List<TagSelection> = emptyList(),
    val orderBy: QueryOrder? = null
) {
    fun api() = LeetCodeClient.Filters(selectedCategory?.slug ?: "all-code-essentials", selectedFavorite?.id,
        selectedDifficulty?.apiValue, selectedStatus?.apiValue, selectedTags.map { it.value }, orderBy?.api())
}
@Serializable
data class KeywordCriteria(val keyword: String = "", val orderBy: QueryOrder? = null)
@Serializable(with = InterviewPeriodSerializer::class)
enum class InterviewPeriod(val slug: String, private val label: String) {
    ThirtyDays("thirty-days", "30days"), ThreeMonths("three-months", "3Months"),
    SixMonths("six-months", "6Months"), MoreThanSixMonths("more-than-six-months", "moreThan6Months"), All("all", "all");
    val show: String get() = PluginBundle.message("leetcode.interviewPeriod.$label")
}
object InterviewPeriodSerializer : StableValueSerializer<InterviewPeriod>("InterviewPeriod", InterviewPeriod.entries, { it.slug })
@Serializable
data class CompanyCriteria(
    val selectedCompanies: Set<LeetCodeQuestionCompanyTag> = emptySet(),
    val selectedPositions: List<LeetCodeProblemsetPositionTag> = emptyList(),
    val selectedDifficulty: ChallengeDifficulty? = null,
    val selectedStatus: ChallengeStatus? = null,
    val selectedInterviewPeriod: InterviewPeriod = InterviewPeriod.ThirtyDays,
    val selectedTags: List<TagSelection> = emptyList(),
    val orderBy: QueryOrder? = null
) {
    fun api() = LeetCodeClient.Filters(difficulty = selectedDifficulty?.apiValue,
        status = selectedStatus?.apiValue, tags = selectedTags.map { it.value }, order = orderBy?.api())
}
enum class QueryMode(val legacyPresenter: String, val label: String) {
    QueryParameters("LeetCodeParametersQueryPresenter", "ui.query.parameters"),
    SearchByKeyword("LeetCodeKeywordQueryPresenter", "ui.query.keyword"),
    CompanyQuery("LeetCodeCompanyQueryPresenter", "ui.query.company");
    val show: String get() = PluginBundle.message(label)
}

/** Explicit legacy keys and JSON shapes survive changes to implementation class names. */
object SavedQueryState {
    private val json get() = LeetCodeJson.format
    const val LAST_MODE_KEY = "LeetCodeChallengesView-lastUI"
    fun loadMode(values: Map<String, String>): QueryMode = QueryMode.entries.find {
        it.name.equals(values[LAST_MODE_KEY], true)
    } ?: QueryMode.QueryParameters
    inline fun <reified T> load(values: Map<String, String>, mode: QueryMode, fallback: T): T =
        values["${mode.legacyPresenter}-criteria"]?.let {
            runCatching { LeetCodeJson.format.decodeFromString<T>(it) }.getOrNull()
        } ?: fallback
    inline fun <reified T> save(values: MutableMap<String, String>, mode: QueryMode, criteria: T, page: Pagination) {
        values["${mode.legacyPresenter}-criteria"] = LeetCodeJson.format.encodeToString(criteria)
        values["${mode.legacyPresenter}-pageSize"] = page.pageSize.value.toString()
    }
    fun page(values: Map<String, String>, mode: QueryMode): Pagination = Pagination(pageSize =
        PageSize.entries.find { it.value.toString() == values["${mode.legacyPresenter}-pageSize"] } ?: PageSize.Twenty)
}
