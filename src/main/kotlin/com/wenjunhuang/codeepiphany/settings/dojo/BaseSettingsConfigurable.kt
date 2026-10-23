package com.wenjunhuang.codeepiphany.settings.dojo

import com.intellij.openapi.options.ConfigurableBase
import com.intellij.openapi.project.Project
import com.wenjunhuang.codeepiphany.PluginBundle
import com.wenjunhuang.codeepiphany.configuration.PlatformSettings
import com.wenjunhuang.codeepiphany.domain.CodeDojo

abstract class BaseSettingsConfigurable(private val project: Project, private val dojo: CodeDojo, id: String) :
    ConfigurableBase<SettingsTabPanel, PlatformSettings.CodeDojoSettingsState>(id,
        PluginBundle.message("${dojo.value}.settings.displayName"), "$id.HelpTopic") {
    override fun getSettings() = PlatformSettings.getInstance(project, dojo).state
    override fun createUi() = SettingsTabPanel(project, dojo)
}
