package io.github.yungyu16.algostack.leetcode

import io.github.yungyu16.algostack.leetcode.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.HttpCookie
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The protocol boundary accepts wire values; IDE/project state belongs to its callers. */
class LeetCodeClient(
    val domain: String,
    private val httpClient: OkHttpClient,
    private val baseUrl: HttpUrl = "https://$domain/".toHttpUrl(),
    private val pollIntervalMillis: Long = 1_000
) {
    init {
        require(domain == "leetcode.cn") { "Unsupported site: $domain; only LeetCodeCN is supported" }
    }

    private val json get() = LeetCodeJson.format
    private val empty = JsonObject(emptyMap())

    data class Sort(val field: String, val direction: String)
    data class Filters(
        val categorySlug: String = "all-code-essentials",
        val favoriteId: String? = null,
        val difficulty: String? = null,
        val status: String? = null,
        val tags: List<String> = emptyList(),
        val order: Sort? = null,
        val keyword: String = ""
    )

    class HttpException(val status: Int, val domain: String) : IOException("$domain returned HTTP $status")
    class InvalidResponse(message: String) : IOException(message)
    private data class WireResponse(val status: Int, val headers: Headers, val text: String)

    private suspend fun execute(request: Request): WireResponse = suspendCancellableCoroutine { continuation ->
        val call = httpClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val result = WireResponse(it.code, it.headers, it.body.string())
                        if (continuation.isActive) continuation.resume(result)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            }
        })
    }

    suspend fun getCSRFToken(): String {
        val response = execute(Request.Builder().url(baseUrl.resolve("api/home/")!!).get().build())
        return response.headers.values("Set-Cookie").flatMap { header ->
            try {
                HttpCookie.parse(header)
            } catch (_: IllegalArgumentException) {
                emptyList()
            }
        }.firstOrNull { it.name == "csrftoken" }?.value.orEmpty()
    }

    private suspend fun request(path: String, body: JsonElement? = null): JsonElement {
        val csrfToken = getCSRFToken()
        val builder = Request.Builder().url(baseUrl.resolve(path)!!)
            .header("x-csrftoken", csrfToken).header("Referer", baseUrl.toString())
        if (body != null) builder.post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        val response = execute(builder.build())
        if (response.status !in 200..299) throw HttpException(response.status, domain)
        return try {
            json.parseToJsonElement(response.text)
        } catch (e: SerializationException) {
            throw InvalidResponse("$domain returned invalid JSON: ${e.message}")
        }
    }

    private fun query(operation: String): String {
        val prefix = "/graphql/"
        val china = javaClass.getResourceAsStream("$prefix${operation}_cn.graphql")
        return (china ?: javaClass.getResourceAsStream("$prefix$operation.graphql")
        ?: throw InvalidResponse("Missing bundled GraphQL operation: $operation"))
            .bufferedReader().use { it.readLines().joinToString("\r") }
    }

    private suspend fun graphQL(operation: String, variables: JsonObject = empty): JsonObject {
        val payload = json.encodeToJsonElement(LeetCodeGraphQLRequest(operation, query(operation), variables))
        return request("graphql/", payload).jsonObject["data"] as? JsonObject
            ?: throw InvalidResponse("$domain response is missing data for $operation")
    }

    private fun JsonObject.required(name: String): JsonElement = get(name)?.takeUnless { it is JsonNull }
        ?: throw InvalidResponse("$domain response is missing $name")

    private inline fun <reified T> decode(value: JsonElement): T = try {
        json.decodeFromJsonElement<T>(value)
    } catch (e: SerializationException) {
        throw InvalidResponse("Invalid $domain response: ${e.message}")
    }

    private fun strings(values: List<String>) = JsonArray(values.map(::JsonPrimitive))
    private fun sort(order: Sort?, field: String, fallback: JsonObject = empty) = order?.let {
        buildJsonObject { put(field, it.field); put("sortOrder", it.direction) }
    } ?: fallback

    private val defaultSort get() = buildJsonObject { put("sortField", "CUSTOM"); put("sortOrder", "ASCENDING") }

    suspend fun getUserInfo(): LeetCodeUserInfo = decode(graphQL("globalData").required("userStatus"))
    suspend fun getFavoriteList(): List<LeetCodeFavoriteItem> = decode(request("problems/api/favorites/"))
    suspend fun getCategoryList(): List<LeetCodeCategoryListItem> =
        decode(request("problems/api/card-info/").jsonObject.required("categories").jsonObject.required("0"))

    suspend fun getPositionTags(): List<LeetCodeProblemsetPositionTag> =
        decode(graphQL("problemsetPositionTags").required("problemsetPositionTags"))

    suspend fun getQuestionCompanyTags(): List<LeetCodeQuestionCompanyTag> {
        val data = graphQL("questionCompanyTagsV2")
        return decode(
            (data["companyTagsV2"] as? JsonObject)?.get("companyTagInfoNodes") ?: data.required("companyTags")
        )
    }

    suspend fun getTagTypeWithTags(): List<LeetCodeTagTypeWithTags> =
        decode(graphQL("questionTagTypeWithTags").required("questionTagTypeWithTags"))

    /** 关键词与各项筛选可以同时生效，接口对两种查询使用同一个 operation。 */
    private fun classicFilters(filters: Filters) = buildJsonObject {
        if (filters.keyword.isNotEmpty()) put("searchKeywords", filters.keyword)
        filters.favoriteId?.let { put("listId", it) }
        filters.difficulty?.let { put("difficulty", it) }
        filters.status?.let { put("status", it) }
        if (filters.tags.isNotEmpty()) put("tags", strings(filters.tags))
        sort(filters.order, "orderBy").forEach { (key, value) -> put(key, value) }
    }

    private fun modernFilters(
        filters: Filters,
        companies: List<String> = emptyList(),
        positions: List<String> = emptyList()
    ) = buildJsonObject {
        fun filter(name: String, valuesName: String, values: List<String>) = put(name, buildJsonObject {
            put(valuesName, strings(values)); put("operator", "IS")
        })
        filter("companyFilter", "companySlugs", companies)
        filter("positionFilter", "positionSlugs", positions)
        put("acceptanceFilter", empty); put("frequencyFilter", empty)
        filter("languageFilter", "languageSlugs", emptyList())
        filter("difficultyFilter", "difficulties", listOfNotNull(filters.difficulty))
        filter("premiumFilter", "premiumStatus", emptyList())
        val status = when (filters.status) {
            "NOT_STARTED" -> "TO_DO"; "AC" -> "SOLVED"; "TRIED" -> "ATTEMPTED"; else -> filters.status
        }
        filter("statusFilter", "questionStatuses", listOfNotNull(status))
        filter("topicFilter", "topicSlugs", filters.tags)
        put("filterCombineType", "ALL")
    }

    suspend fun searchChallenges(offset: Int, limit: Int, filters: Filters = Filters()): LeetCodeChallengeList {
        val variables = buildJsonObject {
            put("skip", offset); put("limit", limit); put("categorySlug", filters.categorySlug)
            put("filters", classicFilters(filters))
        }
        return decode(graphQL("problemsetQuestionList", variables).required("problemsetQuestionList"))
    }

    suspend fun searchCompanyChallenges(
        offset: Int,
        limit: Int,
        interviewPeriod: String,
        companies: List<String>,
        positions: List<String>,
        filters: Filters
    ): LeetCodeCompanyChallengeList {
        require(companies.isNotEmpty()) { "At least one company is required" }
        val favorite = companies.first()
        val variables = buildJsonObject {
            put("skip", offset); put("limit", limit)
            put("favoriteSlug", "$favorite-$interviewPeriod")
            put("filtersV2", modernFilters(filters, companies.drop(1), positions))
            put("sortBy", sort(filters.order, "sortField", defaultSort)); put("searchKeyword", filters.keyword)
        }
        return decode(graphQL("favoriteQuestionList", variables).required("favoriteQuestionList"))
    }

    suspend fun getQuestionData(slug: String): LeetCodeChallengeData =
        decode(graphQL("questionData", buildJsonObject { put("titleSlug", slug) }).required("question"))

    fun runAnswer(id: String, slug: String, testCase: String, language: String, code: String): Flow<LeetCodeRunResult> =
        flow {
            val response = decode<LeetCodeRunResponse>(
                request(
                    "problems/$slug/interpret_solution/",
                    json.encodeToJsonElement(LeetCodeRunRequest(language, testCase, id, code))
                )
            )
            while (true) {
                delay(pollIntervalMillis)
                val result = decode<LeetCodeRunResult>(request("submissions/detail/${response.interpretId}/check/"))
                emit(result)
                if (result is LeetCodeRunResult.Success) break
            }
        }

    fun submitAnswer(id: String, slug: String, language: String, code: String): Flow<LeetCodeSubmitAnswerResult> =
        submit("problems/$slug/submit/", id, language, code)

    private fun submit(path: String, id: String, language: String, code: String): Flow<LeetCodeSubmitAnswerResult> =
        flow {
            val response = decode<LeetCodeSubmitAnswerResponse>(
                request(
                    path,
                    json.encodeToJsonElement(LeetCodeSubmitAnswerRequest(language, id, code))
                )
            )
            // 在任何轮询等待之前交付 ID；后续取消或失败不丢失平台核对入口。
            emit(LeetCodeSubmitAnswerResult.Accepted(response.submissionId.toString()))
            while (true) {
                delay(pollIntervalMillis)
                val result = when (val result =
                    decode<LeetCodeSubmitAnswerResult>(request("submissions/detail/${response.submissionId}/check/"))) {
                    is LeetCodeSubmitAnswerResult.Started -> result.copy(leetCodeSubmissionId = response.submissionId.toString())
                    is LeetCodeSubmitAnswerResult.Pending -> result.copy(leetCodeSubmissionId = response.submissionId.toString())
                    is LeetCodeSubmitAnswerResult.Success -> result
                    is LeetCodeSubmitAnswerResult.Accepted -> error("Accepted is a local event, not a polling response")
                }
                emit(result)
                if (result is LeetCodeSubmitAnswerResult.Success) break
            }
        }

    suspend fun getSolutionTags(slug: String): LeetCodeSolutionTags {
        val operation = "solutionTags"
        return decode(graphQL(operation, buildJsonObject { put("questionSlug", slug) }).required(operation))
    }

    suspend fun searchQuestionSolutionArticles(
        offset: Int,
        limit: Int,
        slug: String,
        order: LeetCodeQuestionSolutionArticlesOrderBy,
        userInput: String?,
        tags: List<String>
    ): LeetCodeQuestionSolutionArticles {
        val operation = "questionTopicsList"
        val variables = buildJsonObject {
            put("skip", offset); put("first", limit); put("questionSlug", slug)
            put("orderBy", order.leetCodeCN.let(::JsonPrimitive))
            put("userInput", userInput.orEmpty()); put("tagSlugs", strings(tags))
        }
        return decode(graphQL(operation, variables).required("questionSolutionArticles"))
    }

    suspend fun getSolutionArticle(slug: String): LeetCodeSolutionArticle {
        val operation = "discussTopic"
        return decode(graphQL(operation, buildJsonObject { put("slug", slug) }).required("solutionArticle"))
    }
}
