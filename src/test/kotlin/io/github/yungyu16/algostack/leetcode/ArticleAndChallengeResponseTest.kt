package io.github.yungyu16.algostack.leetcode

import io.github.yungyu16.algostack.leetcode.model.LeetCodeQuestionSolutionArticlesOrderBy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class ArticleAndChallengeResponseTest {
    private class Fixture(private val data: String) : AutoCloseable {
        var payload: JsonObject? = null
        private val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = if (request.url.encodedPath == "/api/home/") "{}" else {
                check(request.url.encodedPath == "/graphql/")
                val buffer = Buffer()
                request.body!!.writeTo(buffer)
                payload = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
                """{"data":$data}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = LeetCodeClient("leetcode.cn", client)
        val query: String get() = payload!!.getValue("query").jsonPrimitive.content
        override fun close() {
            client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun articleDetailDecodesABodyOnlyResponseThroughTheClient() = runBlocking {
        Fixture("""{"solutionArticle":{"content":"# 正文\n\nexample"}}""").use { f ->
            assertEquals("# 正文\n\nexample", f.api.getSolutionArticle("fixture-article").content)
            assertEquals("discussTopic", f.payload!!.getValue("operationName").jsonPrimitive.content)
            assertEquals(
                "fixture-article",
                f.payload!!.getValue("variables").jsonObject.getValue("slug").jsonPrimitive.content
            )
            assertEquals("content", f.query.substringAfter("orderBy: DEFAULT) {").substringBefore("}").trim())
            assertFalse(f.query.contains("fragment"))
        }
    }

    @Test
    fun aMissingOrNullArticleBodyIsStillAnInvalidResponse() = runBlocking {
        for (article in listOf("{}", """{"content":null}""", "null")) {
            Fixture("""{"solutionArticle":$article}""").use { f ->
                val failure = runCatching { f.api.getSolutionArticle("fixture-article") }.exceptionOrNull()
                assertTrue("A missing body must not become an empty preview", failure is LeetCodeClient.InvalidResponse)
            }
        }
    }

    @Test
    fun questionDecodesOnlyConsumedFieldsAndKeepsOptionalTranslations() = runBlocking {
        for (translations in listOf(
            "", ", \"translatedTitle\":null, \"translatedContent\":null",
            ", \"translatedTitle\":\"两数之和\", \"translatedContent\":\"<p>中文题面</p>\""
        )) {
            Fixture(
                """{"question":{
                "questionId":"9001","frontendQuestionId":"1","title":"Two Sum","titleSlug":"two-sum",
                "content":"<p>English description</p>","difficulty":"Easy",
                "exampleTestcaseList":["[2,7,11,15]\n9"],
                "codeSnippets":[{"lang":"Java","langSlug":"java","code":"class Solution {}"},
                    {"lang":"Go","langSlug":"golang","code":"func twoSum() {}"}]
                $translations
            }}"""
            ).use { f ->
                val question = f.api.getQuestionData("two-sum")
                assertEquals("9001", question.questionId)
                assertEquals("1", question.frontendQuestionId)
                assertEquals("Two Sum", question.title)
                assertEquals("two-sum", question.titleSlug)
                assertEquals("<p>English description</p>", question.content)
                assertEquals("Easy", question.difficulty)
                assertEquals(listOf("[2,7,11,15]\n9"), question.exampleTestcaseList)
                assertEquals(listOf("java", "golang"), question.codeSnippets.map { it.langSlug })
                assertEquals("class Solution {}", question.codeSnippets.first().code)
                if (translations.contains("两数之和")) {
                    assertEquals("两数之和", question.translatedTitle)
                    assertEquals("<p>中文题面</p>", question.translatedContent)
                } else {
                    assertNull(question.translatedTitle)
                    assertNull(question.translatedContent)
                }
                assertEquals("questionData", f.payload!!.getValue("operationName").jsonPrimitive.content)
                assertEquals(
                    "two-sum",
                    f.payload!!.getValue("variables").jsonObject.getValue("titleSlug").jsonPrimitive.content
                )
                assertTrue(f.query.contains("frontendQuestionId: questionFrontendId"))
                val fields = Regex("[A-Za-z][A-Za-z0-9]*").findAll(f.query).map { it.value }.toSet()
                for (removed in listOf(
                    "isPaidOnly",
                    "likes",
                    "dislikes",
                    "isLiked",
                    "similarQuestions",
                    "topicTags",
                    "hints",
                    "status",
                    "testCase",
                    "sampleTestCase",
                    "metaData",
                    "solution",
                    "judgerAvailable",
                    "judgeType",
                    "mysqlSchemas",
                    "libraryUrl"
                )) {
                    assertFalse("Unexpected unused field: $removed", removed in fields)
                }
            }
        }
    }

    @Test
    fun solutionListRetainsPremiumAndPresentationFields() = runBlocking {
        Fixture(
            """{"questionSolutionArticles":{"totalNum":1,"edges":[{"node":{
            "uuid":"fixture-id","title":"会员题解","slug":"premium-article","hasVideo":true,
            "reactions":[{"count":7,"reactionType":"UPVOTE"}],"tags":[{"name":"Java","slug":"java"}],
            "createdAt":"2026-01-01","author":{"username":"alice","certificationLevel":"none",
                "userAvatar":{"userAvatar":"avatar.png"},"userSlug":{"userSlug":"alice"},"realName":{"realName":"Alice"}},
            "summary":"summary","topic":{"id":1,"commentCount":2},"byLeetcode":true,"isMyFavorite":true,
            "chargeType":"PREMIUM","isEditorsPick":true,"hitCount":42,
            "videosInfo":[{"videoId":"fixture-video","coverUrl":"cover.png","duration":10.0}]
        }}]}}"""
        ).use { f ->
            val list = f.api.searchQuestionSolutionArticles(
                0,
                20,
                "two-sum",
                LeetCodeQuestionSolutionArticlesOrderBy.Hot,
                "",
                emptyList()
            )
            val article = list.edges.single().node
            assertEquals(1, list.totalNum)
            assertTrue(article.isPremium)
            assertTrue(article.isMyFavorite)
            assertTrue(article.hasVideo)
            assertTrue(article.byLeetcode)
            assertEquals(true, article.isEditorsPick)
            assertEquals(7, article.upVote)
            assertEquals(42, article.hitCount)
            assertEquals("会员题解", article.title)
            assertEquals("summary", article.summary)
            assertEquals("Alice", article.author.realName)
            assertEquals("java", article.tags.single().slug)
            assertEquals("fixture-video", article.videosInfo.single().videoId)
            assertTrue(f.query.contains("chargeType"))
            assertFalse(article.copy(chargeType = "FREE").isPremium)
        }
    }
}
