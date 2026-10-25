package com.wenjunhuang.codeepiphany.configuration

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Attribute
import com.wenjunhuang.codeepiphany.domain.*
import com.wenjunhuang.codeepiphany.model.Constants

open class PlatformSettings(private val dojo: CodeDojo = CodeDojo.LeetCodeCN) : PersistentStateComponent<PlatformSettings.CodeDojoSettingsState> {
    private var settingsState = CodeDojoSettingsState().apply {
        languageSettings = defaultLanguageSettings(dojo)
        languageDefaultsInitialized = true
    }
    override fun getState(): CodeDojoSettingsState = settingsState
    override fun loadState(state: CodeDojoSettingsState) {
        // 旧空配置初始化一次；显式清空并保存后不再恢复默认语言。
        if (!state.languageDefaultsInitialized && state.languageSettings.isEmpty()) {
            state.languageSettings = defaultLanguageSettings(dojo)
        }
        state.languageDefaultsInitialized = true
        settingsState = state
    }

    fun getSelectedLanguages(): List<Pair<Language, LanguageVersion>> = state.languageSettings.mapNotNull {
        val language = it.language; val version = it.languageVersion
        if (language == null || version == null) null else language to version
    }
    fun getLanguageSetting(language: Language, version: LanguageVersion): LanguageSettingsState? =
        state.languageSettings.find { it.language == language && it.languageVersion == version }

    class CodeDojoSettingsState {
        var languageSettings: MutableList<LanguageSettingsState> = arrayListOf()
        var descriptionCSS: String = ""
        var languageDefaultsInitialized: Boolean = false
    }
    class LanguageSettingsState {
        @field:Attribute(converter = ConfigConverters.StringConverter::class)
        var sourceFolder: String? = null
        @field:Attribute(converter = ConfigConverters.LanguageConverter::class)
        var language: Language? = null
        @field:Attribute(converter = ConfigConverters.LanguageVersionConverter::class)
        var languageVersion: LanguageVersion? = null
        @field:Attribute(converter = ConfigConverters.StringConverter::class)
        var fileNameTemplate: String? = null
        @field:Attribute(converter = ConfigConverters.StringConverter::class)
        var codeTemplate: String? = null
    }
    companion object {
        @JvmStatic fun getInstance(project: Project, dojo: CodeDojo): PlatformSettings = when (dojo) {
            CodeDojo.LeetCode -> project.getService(LeetCodeSettings::class.java)
            CodeDojo.LeetCodeCN -> project.getService(LeetCodeCNSettings::class.java)
        }

    }
}
@Service(Service.Level.PROJECT)
@State(name = Constants.LEETCODE_SETTING, storages = [Storage(Constants.LEETCODE_SETTING_FILE)])
class LeetCodeSettings : PlatformSettings(CodeDojo.LeetCode)

@Service(Service.Level.PROJECT)
@State(name = Constants.LEETCODE_CN_SETTING, storages = [Storage(Constants.LEETCODE_CN_SETTING_FILE)])
class LeetCodeCNSettings : PlatformSettings(CodeDojo.LeetCodeCN)
