package com.wenjunhuang.codeepiphany.presentation

import com.wenjunhuang.codeepiphany.domain.*
import com.wenjunhuang.codeepiphany.leetcode.api.LeetCodeJson
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SavedQueryStateTest {
    @Test fun legacyTupleAndTagObjectsAreDecodedAndWrittenWithoutChangingShape() {
        val raw = """{"selectedCategory":null,"selectedFavorite":null,"selectedDifficulty":"easy","selectedStatus":"solved","tags":[{"name":"数组","value":"array","groupValue":"Algorithms","userObj":{"id":"1","name":"Array","slug":"array","questions":[]}}],"orderBy":["FRONTEND_ID","Descending"]}"""
        val criteria = LeetCodeJson.format.decodeFromString<ProblemCriteria>(raw)
        assertEquals(ChallengeDifficulty.Easy, criteria.selectedDifficulty)
        assertEquals("array", criteria.selectedTags.single().userObj.slug)
        assertEquals(OrderDirection.Descending, criteria.orderBy!!.direction)
        val output = LeetCodeJson.format.encodeToJsonElement(criteria).jsonObject
        assertTrue(output.getValue("orderBy") is JsonArray)
        assertTrue(output.getValue("tags").jsonArray.single().jsonObject.containsKey("userObj"))
        assertFalse(output.containsKey("selectedTags"))
    }
    @Test fun existingModeAndPageSizeKeysRemainUsable() {
        val store = mutableMapOf("LeetCodeChallengesView-lastUI" to "SearchByKeyword",
            "LeetCodeKeywordQueryPresenter-pageSize" to "50",
            "LeetCodeKeywordQueryPresenter-criteria" to """{"keyword":"two sum","orderBy":null}""")
        assertEquals(QueryMode.SearchByKeyword, SavedQueryState.loadMode(store))
        assertEquals(PageSize.Fifty, SavedQueryState.page(store, QueryMode.SearchByKeyword).pageSize)
        val criteria = SavedQueryState.load(store, QueryMode.SearchByKeyword, KeywordCriteria())
        assertEquals("two sum", criteria.keyword)
        SavedQueryState.save(store, QueryMode.SearchByKeyword, criteria, Pagination(pageSize = PageSize.OneHundred))
        assertEquals("100", store["LeetCodeKeywordQueryPresenter-pageSize"])
    }
}
