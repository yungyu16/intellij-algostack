package io.github.yungyu16.algostack.leetcode.model

import kotlinx.serialization.Serializable
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
    companion object {
        val EMPTY_USERINFO = LeetCodeUserInfo(isSignedIn = false)
    }
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
    val difficulty: String,
    val exampleTestcaseList: List<String>,
    val codeSnippets: List<LeetCodeQuestionCodeSnippet>
)

@Serializable
data class LeetCodeQuestionCodeSnippet(
    val lang: String,
    val langSlug: String,
    val code: String
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
