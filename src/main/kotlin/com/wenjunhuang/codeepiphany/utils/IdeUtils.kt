package com.wenjunhuang.codeepiphany.utils

import com.intellij.ide.plugins.PluginDetailsService
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginId
import com.wenjunhuang.codeepiphany.PluginBundle

object IdeUtils {
    @JvmStatic fun majorVersion(): Int = ApplicationInfo.getInstance().majorVersion.toInt()
    @JvmStatic fun minorVersion(): Int = ApplicationInfo.getInstance().minorVersion.toInt()
    @JvmStatic fun shortVersion(): String = ApplicationInfo.getInstance().shortVersion
    @JvmStatic fun pluginVersion(): String = PluginDetailsService.getInstance()
        .findDetails(PluginId.getId("com.wenjunhuang.codeepiphany"))?.version.orEmpty()
    @JvmStatic fun isRunningInCLion(): Boolean = ApplicationInfo.getInstance().versionName?.contains("CLion") == true
    @JvmStatic fun i18nLanguage(): String = PluginBundle.message("locale")
}
