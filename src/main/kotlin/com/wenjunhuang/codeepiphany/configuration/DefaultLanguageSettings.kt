package com.wenjunhuang.codeepiphany.configuration

import com.wenjunhuang.codeepiphany.domain.CodeDojo
import com.wenjunhuang.codeepiphany.domain.Language
import com.wenjunhuang.codeepiphany.domain.LanguageVersion

/** 只创建内存配置，不创建目录或题目；模板资源与设置页的“使用默认模板”共用。 */
internal fun defaultLanguageSettings(dojo: CodeDojo): MutableList<PlatformSettings.LanguageSettingsState> =
    listOf(Language.Java, Language.GO).map { language ->
        PlatformSettings.LanguageSettingsState().apply {
            this.language = language
            languageVersion = LanguageVersion.AnyVersion
            val folder = if (language == Language.Java) "java/src/main/java" else "go"
            sourceFolder = "\$PROJECT_DIR\$/code/$folder/${dojo.value}"
            fun template(kind: String): String = requireNotNull(
                PlatformSettings::class.java.getResourceAsStream("/templates/${dojo.value}/$kind/${language.fileExt}.ft")
            ).bufferedReader().use { it.readText().trimEnd() }
            fileNameTemplate = template("filename")
            codeTemplate = template("code")
        }
    }.toMutableList()
