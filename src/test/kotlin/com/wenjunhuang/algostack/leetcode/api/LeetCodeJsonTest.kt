package com.wenjunhuang.algostack.leetcode.api

import com.wenjunhuang.algostack.leetcode.api.model.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LeetCodeJsonTest {
    private val json = LeetCodeJson.format

    @Test fun runRequestPreservesSnakeCaseWireFields() {
        val payload = json.encodeToJsonElement(LeetCodeRunRequest("java", "[1,2]", "1", "class Solution {}")).jsonObject
        assertEquals(setOf("lang", "data_input", "question_id", "typed_code"), payload.keys)
        assertEquals("[1,2]", payload.getValue("data_input").jsonPrimitive.content)
    }

    @Test fun submitPollingStatesKeepTheExistingDiscriminator() {
        assertTrue(json.decodeFromString<LeetCodeSubmitAnswerResult>("""{"state":"STARTED"}""") is LeetCodeSubmitAnswerResult.Started)
        assertTrue(json.decodeFromString<LeetCodeSubmitAnswerResult>("""{"state":"PENDING"}""") is LeetCodeSubmitAnswerResult.Pending)
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<LeetCodeSubmitAnswerResult>("""{"state":"UNRECOGNIZED"}""")
        }
    }

    @Test fun articleAuthorAcceptsTheTwoSiteShapes() {
        val flat = """{"username":"alice","certificationLevel":"none","userAvatar":"avatar.png","userSlug":"alice","realName":"Alice"}"""
        val wrapped = """{"username":"alice","certificationLevel":"none","userAvatar":{"userAvatar":"avatar.png"},"userSlug":{"userSlug":"alice"},"realName":{"realName":"Alice"},"extra":1}"""
        assertEquals(json.decodeFromString<LeetCodeQuestionSolutionArticleAuthor>(flat), json.decodeFromString<LeetCodeQuestionSolutionArticleAuthor>(wrapped))
    }

    @Test fun optionalResponseFieldsRemainOptionalAndUnknownFieldsAreIgnored() {
        val info = json.decodeFromString<LeetCodeUserInfo>("""{"isSignedIn":false,"newServerField":1}""")
        assertFalse(info.isSignedIn)
        assertNull(info.username)
        assertThrows(SerializationException::class.java) { json.decodeFromString<LeetCodeUserInfo>("{}") }
    }

    @Test fun nullNonNullableFieldsUseExistingDefaults() {
        val tag = json.decodeFromString<LeetCodeTag>("""{"name":"Array","slug":"array","questions":null}""")
        assertEquals(emptyList<Int>(), tag.questions)
    }

    @Test fun chinaQuestionListKeepsTheFrontendDisplayId() {
        val list = json.decodeFromString<LeetCodeChallengeList>("""{"questions":[{"acRate":0.5,"difficulty":"EASY","paidOnly":false,"frontendQuestionId":"LCR 001","title":"test","titleSlug":"test"}],"total":1}""")
        assertEquals("LCR 001", list.questions.single().frontendQuestionId)
        assertEquals(1, list.total)
    }
}
