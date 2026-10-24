package com.wenjunhuang.codeepiphany.model

object Actions {
  // each code dojo in the 'CodeEpiphany Dojo toolwindow' has its query parameters toolbar
  const val LEETCODE_TOOLBAR_GROUP   = "CodeEpiphany.Dojos.LeetCode.Toolbar"
  const val TOOLBAR_PLACE            = "CodeEpiphany.Dojos.Toolbar.Place"

  // 题库面板迁入底部工具窗口后，站点相关动作（模式切换、登录、账号、捐赠）挂在面板工具栏
  const val LEETCODE_SITE_ACTIONS_GROUP = "CodeEpiphany.Dojos.LeetCode.SiteActions"

  // title toolbar is located at the top of CodeEpiphany Dojo toolwindow
  const val TITLE_TOOLBAR_GROUP = "CodeEpiphany.Dojos.TitleToolbar"
  const val TITLE_TOOLBAR_PLACE = "CodeEpiphany.Dojos.TitleToolbar.Place"

  // popup menu of challenges table
  const val CHALLENGES_TABLE_POPUP_GROUP = "CodeEpiphany.Dojos.ChallengesTable.Popupmenu"
  const val CHALLENGES_TABLE_POPUP_PLACE = "CodeEpiphany.Dojos.ChallengesTable.Place"

  // submission log toolbar and popup menu
  const val SUBMISSIONS_TOOLBAR_GROUP     = "CodeEpiphany.Sidebar.SubmissionLog.Toolbar.Group"
  const val SUBMISSIONS_TOOLBAR_PLACE     = "CodeEpiphany.Sidebar.SubmissionLog.Toolbar.Place"
  const val SUBMISSIONS_TABLE_POPUP_GROUP = "CodeEpiphany.Sidebar.SubmissionsTable.Popupmenu"
  const val SUBMISSIONS_TABLE_POPUP_PLACE = "CodeEpiphany.Sidebar.SubmissionsTable.Place"

  // leetcode articles toolbar and popup menu
  const val LEETCODE_ARTICLES_TOOLBAR_GROUP     = "CodeEpiphany.Sidebar.LeetCodeArticles.Toolbar.Group"
  const val LEETCODE_ARTICLES_TOOLBAR_PLACE     = "CodeEpiphany.Sidebar.LeetCodeArticles.Toolbar.Place"

  // challenge editor toolbar
  const val CHALLENGE_EDITOR_TOOLBAR_GROUP = "CodeEpiphany.ChallengeEditor.Toolbar.Group"
  const val CHALLENGE_EDITOR_TOOLBAR_PLACE = "CodeEpiphany.ChallengeEditor.Toolbar.Place"
}
