package io.github.yungyu16.algostack.model


object Constants {
    // 持久化锚点：磁盘上的设置文件名与凭据服务名。1.0.22 起与插件 ID 统一为
    // io.github.yungyu16.algostack；旧 CodeEpiphany 锚点下的文件与凭据不读取、不迁移、不删除，
    // 升级后首次使用需重新登录。
    const val PROJECT_NAME = "AlgoStack"

    const val PROJECT_ID = "io.github.yungyu16.algostack"

    const val APP_SETTING = PROJECT_ID + ".app.settings"

    const val APP_SETTING_FILE = PROJECT_NAME + "_app.xml"

    const val SUBMIT_CODE_REGION_BEGIN = "IMPORTANT!! Submit Code Region Begin(Do not remove this line)"
    const val SUBMIT_CODE_REGION_END = "IMPORTANT!! Submit Code Region End(Do not remove this line)"
}
