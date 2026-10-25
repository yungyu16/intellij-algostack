package io.github.yungyu16.algostack.presentation

import com.intellij.openapi.actionSystem.DataKey
import io.github.yungyu16.algostack.domain.CodeDojo
import io.github.yungyu16.algostack.domain.Language
import io.github.yungyu16.algostack.domain.LanguageVersion
import io.github.yungyu16.algostack.leetcode.model.LeetCodeUserInfo
import io.github.yungyu16.algostack.presentation.problems.QueryMode
import javax.swing.Icon

enum class FilterKind { Category, Favorite, Difficulty, Status, Tags, Company, Position, InterviewPeriod, Language, Dojo }
data class FilterItem(val id: String, val text: String, val icon: Icon? = null, val group: String? = null)
interface FilterHost {
    fun items(kind: FilterKind): List<FilterItem>
    fun selected(kind: FilterKind): Set<String>
    fun toggle(kind: FilterKind, id: String)
    fun multiple(kind: FilterKind): Boolean =
        kind in setOf(FilterKind.Tags, FilterKind.Company, FilterKind.Position, FilterKind.Language, FilterKind.Dojo)
}

interface QueryHost {
    fun refresh();
    val isBusy: Boolean
}

interface ChallengeHost {
    val dojo: CodeDojo
    val canOpenSelection: Boolean
    fun openSelection(language: Language, version: LanguageVersion)
}

interface SiteHost {
    val dojo: CodeDojo
    val mode: QueryMode
    val loggedIn: Boolean
    val loggingIn: Boolean
    val avatar: Icon?
    val userInfo: LeetCodeUserInfo?
    fun login()
    fun logout()
    fun switchTo(mode: QueryMode)
    fun showProfile()
}

object ActionContext {
    @JvmField
    val FILTERS = DataKey.create<FilterHost>("AlgoStack.Filters")
    @JvmField
    val QUERY = DataKey.create<QueryHost>("AlgoStack.Query")
    @JvmField
    val CHALLENGES = DataKey.create<ChallengeHost>("AlgoStack.Challenges")
    @JvmField
    val SITE = DataKey.create<SiteHost>("AlgoStack.Site")
}
