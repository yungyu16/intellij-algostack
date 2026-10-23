package com.wenjunhuang.codeepiphany.settings.dojo

import com.wenjunhuang.codeepiphany.presentation.*

import com.wenjunhuang.codeepiphany.configuration.ChallengeSettings.TestCase
import com.wenjunhuang.codeepiphany.domain.*
import com.wenjunhuang.codeepiphany.execution.ChallengeTemplate
import com.wenjunhuang.codeepiphany.leetcode.api.LeetCodeJson
import com.wenjunhuang.codeepiphany.leetcode.api.model.LeetCodeChallengeData
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object TemplateSamples {
    private val snippets = requireNotNull(javaClass.getResourceAsStream("/settings/leetcode.json")).bufferedReader().use {
        LeetCodeJson.format.parseToJsonElement(it.readText()).jsonObject
    }
    val languages: List<Pair<Language, LanguageVersion>> = snippets.keys.mapNotNull(::languageFromLeetCode)
        .sortedWith(compareBy({ it.first.show }, { it.second }))
    fun template(dojo: CodeDojo, language: Language, version: LanguageVersion): ChallengeTemplate {
        val input = listOf("[1, 3]\n[2]", "[1, 2]\n[3, 4]")
        val data = LeetCodeChallengeData("4", "Interview 4", "Median of two sorted arrays", "median-of-two-sorted-arrays", "",
            isPaidOnly = false, difficulty = "Hard", likes = 0, dislikes = 0, similarQuestions = "", exampleTestcaseList = input,
            topicTags = emptyList(), codeSnippets = emptyList(), hints = emptyList(), testCase = "", metaData = "")
        return ChallengeTemplate(data.questionId, data.frontendQuestionId, dojo, data.title,
            snippets.getValue(language.value + version.version).jsonPrimitive.content.replace("\\n", "\n"), data.titleSlug,
            data.title, "hard", language, version, data, input.zip(listOf("2.00000", "2.50000")).map { TestCase(it.first, it.second) })
    }
}
