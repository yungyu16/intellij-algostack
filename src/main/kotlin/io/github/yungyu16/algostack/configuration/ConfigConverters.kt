package io.github.yungyu16.algostack.configuration

import com.intellij.util.xmlb.Converter

object ConfigConverters {
    class StringConverter : Converter<String>() {
        override fun fromString(value: String): String = value
        override fun toString(value: String): String = value
    }
}
