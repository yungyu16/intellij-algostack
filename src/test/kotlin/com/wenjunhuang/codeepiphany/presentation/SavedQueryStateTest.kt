package com.wenjunhuang.codeepiphany.presentation

import com.wenjunhuang.codeepiphany.domain.*
import com.wenjunhuang.codeepiphany.leetcode.api.LeetCodeJson
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SavedQueryStateTest {
    @Test fun removingAnOptionalFilterIsPersistedWithoutChangingOtherModes() {
        val store = mutableMapOf<String, String>()
        val company = CompanyCriteria(selectedInterviewPeriod = InterviewPeriod.All)
        SavedQueryState.save(store, QueryMode.CompanyQuery, company, Pagination())
        SavedQueryState.save(store, QueryMode.QueryParameters, ProblemCriteria(selectedDifficulty = ChallengeDifficulty.Easy), Pagination())
        // 保存发生在发出请求前；即使后续网络失败，移除后的选择也应可恢复。
        SavedQueryState.save(store, QueryMode.QueryParameters, ProblemCriteria(), Pagination())
        assertNull(SavedQueryState.load(store, QueryMode.QueryParameters, ProblemCriteria(selectedDifficulty = ChallengeDifficulty.Hard)).selectedDifficulty)
        assertEquals(InterviewPeriod.All, SavedQueryState.load(store, QueryMode.CompanyQuery, CompanyCriteria()).selectedInterviewPeriod)
    }

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
    @Test fun legacyKeywordStateFallsBackToTheUnifiedSearch() {
        val store = mutableMapOf("LeetCodeChallengesView-lastUI" to "SearchByKeyword",
            "LeetCodeKeywordQueryPresenter-pageSize" to "50",
            "LeetCodeKeywordQueryPresenter-criteria" to """{"keyword":"two sum","orderBy":null}""")
        assertEquals(QueryMode.QueryParameters, SavedQueryState.loadMode(store))
        val criteria = SavedQueryState.migrateLegacyKeyword(store, ProblemCriteria())
        assertEquals("two sum", criteria.keyword)
        SavedQueryState.save(store, QueryMode.QueryParameters, criteria, Pagination(pageSize = PageSize.OneHundred))
        assertEquals("100", store["LeetCodeParametersQueryPresenter-pageSize"])
    }

    @Test fun companyModeRecordStillSelectsCompanyQuery() {
        val store = mutableMapOf("LeetCodeChallengesView-lastUI" to "CompanyQuery")
        assertEquals(QueryMode.CompanyQuery, SavedQueryState.loadMode(store))
    }
}
