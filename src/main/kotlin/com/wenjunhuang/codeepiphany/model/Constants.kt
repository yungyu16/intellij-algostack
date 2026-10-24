package com.wenjunhuang.codeepiphany.model


object Constants {
  const val PROJECT_NAME = "CodeEpiphany"

  const val PROJECT_ID = "com.wenjun.codeepiphany"

  const val ACTION_PREFIX = PROJECT_NAME + ".actions"

  const val SETTINGS_FOLDER = PROJECT_NAME

  const val CHALLENGE_STORAGE_FILE = "challenges.db"

  const val SETTING = PROJECT_ID + ".settings"

  const val APP_SETTING = PROJECT_ID + ".app.settings"

  const val SETTING_FILE = SETTINGS_FOLDER + "/settings.xml"

  const val APP_SETTING_FILE = PROJECT_NAME + "_app.xml"

  const val CHALLENGE_SETTING = PROJECT_ID + ".challenge.settings"
  const val CHALLENGE_SETTING_FILE = SETTINGS_FOLDER + "/challenge.xml"

  const val LEETCODE_CN_SETTING = PROJECT_ID + ".leetcodecn.settings"
  // 模板与语言配置是项目级的稳定资产，放 .leetcode 便于提交到 git。
  const val LEETCODE_CN_SETTING_FILE = ".leetcode/leetcodecn.xml"
  const val LEETCODE_SETTING = PROJECT_ID + ".leetcode.settings"
  const val LEETCODE_SETTING_FILE = ".leetcode/leetcode.xml"

  const val SUBMIT_CODE_REGION_BEGIN = "IMPORTANT!! Submit Code Region Begin(Do not remove this line)"
  const val SUBMIT_CODE_REGION_END = "IMPORTANT!! Submit Code Region End(Do not remove this line)"
}
