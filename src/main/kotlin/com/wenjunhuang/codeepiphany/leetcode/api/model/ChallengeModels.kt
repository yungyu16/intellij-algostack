package com.wenjunhuang.codeepiphany.leetcode.api.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

@Serializable
data class LeetCodeTag(
    val id: String? = null,
    val name: String,
    val nameTranslated: String? = null,
    val slug: String,
    val questions: List<Int> = emptyList()
)

@Serializable
data class LeetCodeTagTypeWithTags(
    val name: String,
    val transName: String? = null,
    val tagRelation: List<LeetCodeTagRelation>
)

@Serializable
data class LeetCodeTagRelation(
    val questionNum: Int,
    val tag: LeetCodeTag
)

@Serializable
data class LeetCodeProblemsetCompanyTag(
    val name: String,
    val slug: String
)

@Serializable
data class LeetCodeQuestionCompanyTag(
    val name: String,
    val slug: String,
    val id: String,
    val questionCount: Int
)

@Serializable
data class LeetCodeCompanyChallengeList(
    val questions: List<LeetCodeCompanyChallengeListItem>,
    val total: Int
)

@Serializable
data class LeetCodeCompanyChallengeListItem(
    val acRate: Double,
    val difficulty: String,
    val freqBar: Double? = null,
    val paidOnly: Boolean,
    val status: String? = null,
    val questionFrontendId: String,
    val title: String,
    val translatedTitle: String? = null,
    val titleSlug: String
) {
    val frontendQuestionId: String get() = questionFrontendId
    val titleCn: String? get() = translatedTitle
}

@Serializable
data class LeetCodeFavoriteItem(
    val id: String,
    val name: String,
    val `type`: String
)

@Serializable
data class LeetCodeCategoryListItem(
    val title: String,
    val url: String,
    val slug: String
)

@Serializable
data class LeetCodeProblemsetPositionTag(
    val name: String,
    val nameTranslated: String? = null,
    val slug: String
)

@Serializable
data class LeetCodeGraphQLRequest(
    val operationName: String,
    val query: String,
    val variables: JsonObject
)

@Serializable
data class LeetCodeSolutionTag(
    val name: String,
    val nameTranslated: String? = null,
    val slug: String
)

@Serializable
data class LeetCodeSolutionTags(
    val languageTags: List<LeetCodeSolutionTag>,
    val knowledgeTags: List<LeetCodeSolutionTag>
)

@Serializable
data class LeetCodeUserInfo(
    val isSignedIn: Boolean,
    val isPremium: Boolean? = null,
    val username: String? = null,
    val realName: String? = null,
    val avatar: String? = null,
    val userSlug: String? = null
) {
    companion object { val EMPTY_USERINFO = LeetCodeUserInfo(isSignedIn = false) }
}

@Serializable
data class LeetCodeChallengeData(
    val questionId: String,
    val frontendQuestionId: String,
    val title: String,
    val titleSlug: String,
    val content: String,
    val translatedTitle: String? = null,
    val translatedContent: String? = null,
    val isPaidOnly: Boolean,
    val difficulty: String,
    val likes: Int,
    val dislikes: Int,
    val isLiked: Boolean? = null,
    val similarQuestions: String,
    val exampleTestcaseList: List<String>,
    val topicTags: List<LeetCodeQuestionTopicTag>,
    val codeSnippets: List<LeetCodeQuestionCodeSnippet>,
    val hints: List<String>,
    val status: String? = null,
    val testCase: String,
    val metaData: String
)

@Serializable
data class LeetCodeQuestionSimilarQuestion(
    val title: String,
    val titleSlug: String,
    val translatedTitle: String? = null
)

@Serializable
data class LeetCodeQuestionTopicTag(
    val name: String,
    val slug: String,
    val translatedName: String? = null
)

@Serializable
data class LeetCodeQuestionCodeSnippet(
    val lang: String,
    val langSlug: String,
    val code: String
)

@Serializable
data class LeetCodeQuestionCodeMetaData(
    val name: String,
    val params: List<LeetCodeQuestionCodeParam>,
    val `return`: LeetCodeQuestionCodeReturn,
    val manual: Boolean
)

@Serializable
data class LeetCodeQuestionCodeParam(
    val name: String,
    val `type`: String
)

@Serializable
data class LeetCodeQuestionCodeReturn(
    val `type`: String,
    val size: String
)

@Serializable
data class LeetCodeChallengeList(
    val questions: List<LeetCodeChallengeListItem>,
    val total: Int
)

@Serializable
data class LeetCodeChallengeListItem(
    val acRate: Double,
    val difficulty: String,
    val freqBar: Double? = null,
    val paidOnly: Boolean,
    val solutionNum: Int? = null,
    val status: String? = null,
    val frontendQuestionId: String,
    val title: String,
    val titleCn: String? = null,
    val titleSlug: String
)

@Serializable
data class LeetCodeChallengeListV2(
    val questions: List<LeetCodeChallengeListItemV2>,
    val totalLength: Int
) {
    fun toV1() = LeetCodeChallengeList(questions.map { it.toV1() }, totalLength)
}

@Serializable
data class LeetCodeChallengeListItemV2(
    val acRate: Double,
    val difficulty: String,
    val frequency: Double? = null,
    val paidOnly: Boolean,
    val status: String? = null,
    val questionFrontendId: String,
    val title: String,
    val titleSlug: String
) {
    fun toV1() = LeetCodeChallengeListItem(acRate, difficulty, frequency, paidOnly, null, status,
        questionFrontendId, title, null, titleSlug)
}
