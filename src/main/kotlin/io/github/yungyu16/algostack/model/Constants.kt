package io.github.yungyu16.algostack.model


object Constants {
  // 持久化锚点：磁盘上的配置文件名与凭据服务名，历史用户数据依赖这些字面量，不随插件改名。
  const val PROJECT_NAME = "CodeEpiphany"

  const val PROJECT_ID = "com.wenjun.codeepiphany"

  const val APP_SETTING = PROJECT_ID + ".app.settings"

  const val APP_SETTING_FILE = PROJECT_NAME + "_app.xml"

  const val SUBMIT_CODE_REGION_BEGIN = "IMPORTANT!! Submit Code Region Begin(Do not remove this line)"
  const val SUBMIT_CODE_REGION_END = "IMPORTANT!! Submit Code Region End(Do not remove this line)"
}
