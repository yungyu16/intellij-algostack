package com.wenjunhuang.codeepiphany.configuration

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Attribute
import com.wenjunhuang.codeepiphany.domain.*
import com.wenjunhuang.codeepiphany.model.Constants
import java.nio.file.Files
import java.nio.file.Path

open class PlatformSettings : PersistentStateComponent<PlatformSettings.CodeDojoSettingsState> {
    private var settingsState = CodeDojoSettingsState()
    override fun getState(): CodeDojoSettingsState = settingsState
    override fun loadState(state: CodeDojoSettingsState) { settingsState = state }

    fun getSelectedLanguages(): List<Pair<Language, LanguageVersion>> = state.languageSettings.mapNotNull {
        val language = it.language; val version = it.languageVersion
        if (language == null || version == null) null else language to version
    }
    fun getLanguageSetting(language: Language, version: LanguageVersion): LanguageSettingsState? =
        state.languageSettings.find { it.language == language && it.languageVersion == version }

    class CodeDojoSettingsState {
        var languageSettings: MutableList<LanguageSettingsState> = arrayListOf()
        var descriptionCSS: String = ""
        var extras: MutableMap<String, String> = linkedMapOf()
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

        /** 旧配置位于 .idea/CodeEpiphany；首次使用 .leetcode 时由面板创建入口整体搬过去。 */
        @JvmStatic fun migrateLegacyStorage(project: Project) {
            val root = project.basePath ?: return
            for (name in listOf("leetcodecn.xml", "leetcode.xml")) {
                runCatching {
                    val legacy = Path.of(root, ".idea", Constants.PROJECT_NAME, name)
                    val modern = Path.of(root, ".leetcode", name)
                    if (!modern.toFile().isFile() && legacy.toFile().isFile()) {
                        Files.createDirectories(modern.parent)
                        Files.copy(legacy, modern)
                    }
                }
            }
        }
    }
}
@Service(Service.Level.PROJECT)
@State(name = Constants.LEETCODE_SETTING, storages = [Storage(Constants.LEETCODE_SETTING_FILE)])
class LeetCodeSettings : PlatformSettings()

@Service(Service.Level.PROJECT)
@State(name = Constants.LEETCODE_CN_SETTING, storages = [Storage(Constants.LEETCODE_CN_SETTING_FILE)])
class LeetCodeCNSettings : PlatformSettings()
