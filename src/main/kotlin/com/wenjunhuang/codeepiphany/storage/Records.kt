package com.wenjunhuang.codeepiphany.storage

import com.wenjunhuang.codeepiphany.domain.CodeDojo
import com.wenjunhuang.codeepiphany.domain.Language
import com.wenjunhuang.codeepiphany.domain.LanguageVersion
import java.time.LocalDateTime

data class ChallengeRecord(val id: Long, val dojo: CodeDojo, val dojoId: String, val title: String,
    val slug: String, val difficulty: String, val description: String)
data class ChallengeDraft(val dojo: CodeDojo, val dojoId: String, val title: String, val slug: String,
    val difficulty: String, val description: String, val frontendId: String, val testCases: String,
    val language: Language, val version: LanguageVersion, val template: String)
data class StoredChallenge(val challengeId: Long, val languageId: Long)
data class ExecutionRequest(val questionId: String, val slug: String, val defaultCases: String,
    val language: Language, val version: LanguageVersion)
data class SolutionRecord(val id: Long, val challengeId: Long, val title: String, val remark: String,
    val createdAt: LocalDateTime, val isDefault: Boolean, val submissions: Int)
data class SolutionLanguageCount(val solutionId: Long, val language: Language, val version: LanguageVersion, val count: Int)
data class SubmissionRecord(val id: Long, val solutionId: Long, val challengeLanguageId: Long,
    val dojoSubmissionId: String?, val localCode: String, val submitCode: String, val result: String,
    val message: String?, val submitDateTime: LocalDateTime, val resultDateTime: LocalDateTime?,
    val challengeId: Long, val dojo: CodeDojo, val challengeTitle: String, val slug: String,
    val difficulty: String, val solutionTitle: String, val language: Language, val languageVersion: LanguageVersion)
data class SubmissionMetrics(val memory: Int? = null, val totalCorrect: Int? = null, val totalTestcases: Int? = null,
    val statusMemory: String? = null, val runtimePercentile: Float? = null, val memoryPercentile: Float? = null,
    val lastTestcase: String? = null, val inputFormatted: String? = null, val expectedOutput: String? = null,
    val codeOutput: String? = null, val stdOutput: String? = null, val statusRuntime: String? = null)
enum class SubmissionSort(val column: String) {
    Time("ss.submitDateTime"), Title("c.title"), Solution("s.title"), Language("cl.language"),
    Result("ss.result"), Difficulty("c.difficulty"), Site("c.dojo")
}
data class SubmissionFilter(val dojos: Set<CodeDojo> = emptySet(), val languages: Set<Language> = emptySet(),
    val sort: SubmissionSort = SubmissionSort.Time, val ascending: Boolean = false)
data class SubmissionPage(val total: Int, val rows: List<SubmissionRecord>)
