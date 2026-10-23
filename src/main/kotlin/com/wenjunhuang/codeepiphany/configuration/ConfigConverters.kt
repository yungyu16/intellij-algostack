package com.wenjunhuang.codeepiphany.configuration

import com.intellij.util.xmlb.Converter
import com.wenjunhuang.codeepiphany.domain.CodeDojo
import com.wenjunhuang.codeepiphany.domain.Language
import com.wenjunhuang.codeepiphany.domain.LanguageVersion

object ConfigConverters {
    class CodeDojoConverter : Converter<CodeDojo>() {
        override fun fromString(value: String): CodeDojo? = CodeDojo.fromString(value)
        override fun toString(value: CodeDojo): String = value.value
    }
    class LanguageConverter : Converter<Language>() {
        override fun fromString(value: String): Language? = Language.fromString(value)
        override fun toString(value: Language): String = value.value
    }
    class LanguageVersionConverter : Converter<LanguageVersion>() {
        override fun fromString(value: String): LanguageVersion = LanguageVersion.fromString(value)
        override fun toString(value: LanguageVersion): String = value.version
    }
    class StringConverter : Converter<String>() {
        override fun fromString(value: String): String = value
        override fun toString(value: String): String = value
    }
}
