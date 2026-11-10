package io.github.yungyu16.algostack.utils

import com.intellij.ide.plugins.PluginDetailsService
import com.intellij.openapi.extensions.PluginId

object IdeUtils {
    const val PLUGIN_ID = "com.yungyu.leetcode-editor"

    @JvmStatic
    fun pluginVersion(): String = PluginDetailsService.getInstance()
        .findDetails(PluginId.getId(PLUGIN_ID))?.version.orEmpty()
}
