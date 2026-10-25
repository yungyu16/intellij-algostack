package com.wenjunhuang.algostack.utils

import com.intellij.ide.plugins.PluginDetailsService
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginId
import com.wenjunhuang.algostack.PluginBundle

object IdeUtils {
    const val PLUGIN_ID = "com.yungyu.leetcode-editor"

    @JvmStatic fun majorVersion(): Int = ApplicationInfo.getInstance().majorVersion.toInt()
    @JvmStatic fun minorVersion(): Int = ApplicationInfo.getInstance().minorVersion.toInt()
    @JvmStatic fun shortVersion(): String = ApplicationInfo.getInstance().shortVersion
    @JvmStatic fun pluginVersion(): String = PluginDetailsService.getInstance()
        .findDetails(PluginId.getId(PLUGIN_ID))?.version.orEmpty()
    @JvmStatic fun isRunningInCLion(): Boolean = ApplicationInfo.getInstance().versionName?.contains("CLion") == true
    @JvmStatic fun i18nLanguage(): String = PluginBundle.message("locale")
}
