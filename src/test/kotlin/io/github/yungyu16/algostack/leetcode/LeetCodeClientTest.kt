package io.github.yungyu16.algostack.leetcode

import io.github.yungyu16.algostack.leetcode.model.LeetCodeSubmitAnswerResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LeetCodeClientTest {
    private class Fixture(val handler: (Interceptor.Chain) -> Response) : AutoCloseable {
        val requests = CopyOnWriteArrayList<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            if (chain.request().url.encodedPath == "/api/home/") response(
                chain.request(),
                "{}",
                "csrftoken=fixture-token; Path=/"
            )
            else handler(chain)
        }.build()

        fun api(domain: String) = LeetCodeClient(domain, client, pollIntervalMillis = 0)
        override fun close() {
            client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
        }
    }

    companion object {
        private fun response(request: Request, json: String, cookie: String? = null): Response = Response.Builder()
            .request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(json.toResponseBody("application/json".toMediaType()))
            .apply { cookie?.let { header("Set-Cookie", it) } }.build()

        private fun payload(request: Request): JsonObject {
            val buffer = Buffer(); request.body!!.writeTo(buffer)
            return Json.parseToJsonElement(buffer.readUtf8()).jsonObject
        }
    }

    @Test
    fun chinaSearchPreservesClassicFiltersAndCsrfHeaders() = runBlocking {
        Fixture { chain ->
            response(
                chain.request(),
                """{"data":{"problemsetQuestionList":{"questions":[],"total":0}}}"""
            )
        }.use { f ->
            f.api("leetcode.cn").searchChallenges(
                40, 20, LeetCodeClient.Filters(
                    favoriteId = "favorite-1", difficulty = "EASY", status = "AC", tags = listOf("array"),
                    order = LeetCodeClient.Sort("FRONTEND_ID", "ASCENDING"), keyword = "two sum"
                )
            )
            val request = f.requests.last()
            assertEquals("fixture-token", request.header("x-csrftoken"))
            assertEquals("https://leetcode.cn/", request.header("Referer"))
            val body = payload(request)
            assertEquals("problemsetQuestionList", body.getValue("operationName").jsonPrimitive.content)
            val variables = body.getValue("variables").jsonObject
            assertEquals(40, variables.getValue("skip").jsonPrimitive.int)
            val filters = variables.getValue("filters").jsonObject
            assertEquals("two sum", filters.getValue("searchKeywords").jsonPrimitive.content)
            assertEquals("EASY", filters.getValue("difficulty").jsonPrimitive.content)
            assertEquals("array", filters.getValue("tags").jsonArray.single().jsonPrimitive.content)
            assertEquals("favorite-1", filters.getValue("listId").jsonPrimitive.content)
            assertEquals("AC", filters.getValue("status").jsonPrimitive.content)
            assertEquals("ASCENDING", filters.getValue("sortOrder").jsonPrimitive.content)
        }
    }

    @Test
    fun companySearchSendsKeywordAndAllSelectedFiltersTogether() = runBlocking {
        Fixture { chain ->
            response(
                chain.request(),
                """{"data":{"favoriteQuestionList":{"questions":[],"total":0}}}"""
            )
        }.use { f ->
            f.api("leetcode.cn").searchCompanyChallenges(
                40,
                20,
                "all",
                listOf("company-a", "company-b"),
                listOf("backend"),
                LeetCodeClient.Filters(difficulty = "MEDIUM", status = "AC", tags = listOf("array"), keyword = "sum")
            )
            val body = payload(f.requests.last())
            assertEquals("favoriteQuestionList", body.getValue("operationName").jsonPrimitive.content)
            val variables = body.getValue("variables").jsonObject
            assertEquals("company-a-all", variables.getValue("favoriteSlug").jsonPrimitive.content)
            assertEquals(40, variables.getValue("skip").jsonPrimitive.int)
            assertEquals("sum", variables.getValue("searchKeyword").jsonPrimitive.content)
            val filters = variables.getValue("filtersV2").jsonObject
            for ((kind, field, value) in listOf(
                Triple("companyFilter", "companySlugs", "company-b"),
                Triple("positionFilter", "positionSlugs", "backend"),
                Triple("difficultyFilter", "difficulties", "MEDIUM"),
                Triple("statusFilter", "questionStatuses", "SOLVED"),
                Triple("topicFilter", "topicSlugs", "array")
            )) {
                assertEquals(
                    value,
                    filters.getValue(kind).jsonObject.getValue(field).jsonArray.single().jsonPrimitive.content
                )
            }
        }
    }

    @Test
    fun internationalSiteIsRejectedBeforeAnyHttpRequest() {
        Fixture { error("No HTTP request is allowed") }.use { f ->
            val failure = assertThrows(IllegalArgumentException::class.java) { f.api("leetcode.com") }
            assertTrue(failure.message!!.contains("only LeetCodeCN"))
            assertTrue(f.requests.isEmpty())
        }
    }

    @Test
    fun submissionPollingEmitsPendingAndTerminalResultsExactlyOnce() = runBlocking {
        var polls = 0
        Fixture { chain ->
            val request = chain.request()
            when (request.url.encodedPath) {
                "/problems/two-sum/submit/" -> response(request, """{"submission_id":42}""")
                "/submissions/detail/42/check/" -> {
                    polls++
                    response(
                        request, if (polls == 1) """{"state":"PENDING"}""" else """{
                        "state":"SUCCESS","status_code":10,"lang":"java","run_success":true,
                        "status_runtime":"1 ms","memory":100,"question_id":"1","task_finish_time":0,
                        "task_name":"judger","finished":true,"status_msg":"Accepted","submission_id":"42",
                        "status_memory":"100 KB","pretty_lang":"Java"
                    }"""
                    )
                }

                else -> error("Unexpected request: ${request.url}")
            }
        }.use { f ->
            val results = f.api("leetcode.cn").submitAnswer("1", "two-sum", "java", "class Solution {}").toList()
            assertEquals(3, results.size)
            assertEquals("42", (results.first() as LeetCodeSubmitAnswerResult.Accepted).submissionId)
            assertEquals("42", (results[1] as LeetCodeSubmitAnswerResult.Pending).leetCodeSubmissionId)
            assertTrue(results.last() is LeetCodeSubmitAnswerResult.Success)
            assertEquals(2, polls)
        }
    }

    @Test
    fun acceptanceIsDeliveredBeforePollingEvenWhenTrackingStopsImmediately() = runBlocking {
        withTimeout(2_000) {
            Fixture { chain ->
                check(chain.request().url.encodedPath == "/problems/two-sum/submit/")
                response(chain.request(), """{"submission_id":42}""")
            }.use { f ->
                val first = f.api("leetcode.cn").submitAnswer("1", "two-sum", "java", "class Solution {}").first()
                assertEquals(LeetCodeSubmitAnswerResult.Accepted("42"), first)
                assertFalse(f.requests.any { it.url.encodedPath.endsWith("/check/") })
            }
        }
    }

    @Test
    fun acceptanceSurvivesFirstPollFailureAndPendingThenFailure() = runBlocking {
        withTimeout(2_000) {
            for (pendingFirst in listOf(false, true)) {
                var polls = 0
                Fixture { chain ->
                    if (chain.request().url.encodedPath.endsWith("/submit/")) {
                        response(chain.request(), """{"submission_id":42}""")
                    } else {
                        polls++
                        if (pendingFirst && polls == 1) response(chain.request(), """{"state":"PENDING"}""")
                        else throw IOException("poll failed")
                    }
                }.use { f ->
                    val received = mutableListOf<LeetCodeSubmitAnswerResult>()
                    try {
                        f.api("leetcode.cn").submitAnswer("1", "two-sum", "java", "class Solution {}")
                            .collect { received += it }
                        fail("Polling must fail")
                    } catch (expected: IOException) {
                        assertEquals("poll failed", expected.message)
                    }
                    assertEquals(
                        listOf(LeetCodeSubmitAnswerResult.Accepted("42")),
                        received.filterIsInstance<LeetCodeSubmitAnswerResult.Accepted>()
                    )
                    assertEquals(if (pendingFirst) 2 else 1, received.size)
                }
            }
        }
    }

    @Test
    fun cancellingAQueryCancelsItsHttpCall() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val httpCall = AtomicReference<Call>()
        Fixture { chain ->
            httpCall.set(chain.call()); entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            response(chain.request(), """{"data":{"userStatus":{"isSignedIn":false}}}""")
        }.use { f ->
            try {
                val job = async(Dispatchers.Default) { f.api("leetcode.cn").getUserInfo() }
                assertTrue(withContext(Dispatchers.IO) { entered.await(3, TimeUnit.SECONDS) })
                withTimeout(3_000) { job.cancelAndJoin() }
                assertTrue(httpCall.get().isCanceled())
                assertTrue(job.isCancelled)
            } finally {
                release.countDown()
            }
        }
    }
}
