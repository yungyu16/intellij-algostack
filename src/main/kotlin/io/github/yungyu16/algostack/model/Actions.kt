package io.github.yungyu16.algostack.model

object Actions {
    // each code dojo in the 'AlgoStack Dojo toolwindow' has its query parameters toolbar
    const val LEETCODE_TOOLBAR_GROUP = "AlgoStack.Dojos.LeetCode.Toolbar"
    const val TOOLBAR_PLACE = "AlgoStack.Dojos.Toolbar.Place"

    // 题库面板迁入底部工具窗口后，站点相关动作（模式切换、登录、账号、捐赠）挂在面板工具栏
    const val LEETCODE_SITE_ACTIONS_GROUP = "AlgoStack.Dojos.LeetCode.SiteActions"

    // popup menu of challenges table
    const val CHALLENGES_TABLE_POPUP_GROUP = "AlgoStack.Dojos.ChallengesTable.Popupmenu"
    const val CHALLENGES_TABLE_POPUP_PLACE = "AlgoStack.Dojos.ChallengesTable.Place"

    // leetcode articles toolbar and popup menu
    const val LEETCODE_ARTICLES_TOOLBAR_GROUP = "AlgoStack.Sidebar.LeetCodeArticles.Toolbar.Group"
    const val LEETCODE_ARTICLES_TOOLBAR_PLACE = "AlgoStack.Sidebar.LeetCodeArticles.Toolbar.Place"

    // challenge editor toolbar
    const val CHALLENGE_EDITOR_TOOLBAR_GROUP = "AlgoStack.ChallengeEditor.Toolbar.Group"
    const val CHALLENGE_EDITOR_TOOLBAR_PLACE = "AlgoStack.ChallengeEditor.Toolbar.Place"
}
