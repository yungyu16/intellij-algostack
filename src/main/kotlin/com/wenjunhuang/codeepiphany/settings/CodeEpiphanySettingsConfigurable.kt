package com.wenjunhuang.codeepiphany.settings

import com.intellij.openapi.options.ConfigurableBase
import com.intellij.openapi.project.Project
import com.wenjunhuang.codeepiphany.PluginBundle
import com.wenjunhuang.codeepiphany.configuration.CodeEpiphanySettings

class CodeEpiphanySettingsConfigurable(private val project: Project) :
    ConfigurableBase<CodeEpiphanySettingsPanel, CodeEpiphanySettings.CodeEpiphanySettingsState>(
        "CodeEpiphany.Settings", PluginBundle.message("settings.displayName"), "CodeEpiphany.Settings.HelpTopic") {
    override fun getSettings() = CodeEpiphanySettings.getInstance(project).state
    override fun createUi() = CodeEpiphanySettingsPanel(project)
}
