package com.wenjunhuang.algostack.leetcode.api.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

@Serializable
data class LeetCodeQuestionSolution(
    val id: String,
    val canSeeDetail: Boolean
)

@Serializable
data class LeetCodeQuestionSolutionArticleTag(
    val name: String,
    val nameTranslated: String? = null,
    val slug: String,
    val tagType: String? = null
)

@Serializable
data class LeetCodeQuestionSolutionArticleReaction(
    val count: Int,
    val reactionType: String
)

@Serializable(with = ArticleAuthorSerializer::class)
data class LeetCodeQuestionSolutionArticleAuthor(
    val username: String,
    val certificationLevel: String,
    val userAvatar: String,
    val userSlug: String,
    val realName: String
)

@Serializable
data class LeetCodeQuestionSolutionArticleTopic(
    val id: Int,
    val commentCount: Int
)

@Serializable
data class LeetCodeQuestionSolutionArticleVideoInfo(
    val videoId: String,
    val coverUrl: String,
    val duration: Double
)

@Serializable
data class LeetCodeQuestionSolutionArticle(
    val uuid: String,
    val title: String,
    val slug: String,
    val hasVideo: Boolean,
    val reactions: List<LeetCodeQuestionSolutionArticleReaction>,
    val tags: List<LeetCodeQuestionSolutionArticleTag>,
    val createdAt: String,
    val author: LeetCodeQuestionSolutionArticleAuthor,
    val summary: String,
    val topic: LeetCodeQuestionSolutionArticleTopic,
    val byLeetcode: Boolean,
    val isMyFavorite: Boolean,
    val chargeType: String,
    val isEditorsPick: Boolean? = null,
    val hitCount: Int,
    val videosInfo: List<LeetCodeQuestionSolutionArticleVideoInfo> = emptyList()
) {
    val upVote: Int? get() = reactions.find { it.reactionType.equals("UPVOTE", true) }?.count
    val isPremium: Boolean get() = chargeType.equals("PREMIUM", true)
}

@Serializable
data class LeetCodeQuestionSolutionArticleEdge(
    val node: LeetCodeQuestionSolutionArticle
)

@Serializable
data class LeetCodeQuestionSolutionArticles(
    val edges: List<LeetCodeQuestionSolutionArticleEdge>,
    val totalNum: Int
)

@Serializable
data class LeetCodeSolutionArticle(
    val uuid: String,
    val title: String,
    val content: String,
    val slug: String,
    val chargeType: String,
    val reactions: List<LeetCodeQuestionSolutionArticleReaction>,
    val tags: List<LeetCodeQuestionSolutionArticleTag>,
    val createdAt: String,
    val author: LeetCodeQuestionSolutionArticleAuthor,
    val topic: LeetCodeQuestionSolutionArticleTopic,
    val hitCount: Int,
    val videosInfo: List<LeetCodeQuestionSolutionArticleVideoInfo> = emptyList()
)

enum class LeetCodeQuestionSolutionArticlesOrderBy(val leetCodeCN: String, val show: String) {
    MostUpvote("MOST_UPVOTE", "Most Upvoted"),
    Hot("HOT", "Hot"),
    Newest("NEWEST_TO_OLDEST", "Newest"),
    Oldest("OLDEST_TO_NEWEST", "Oldest")
}
