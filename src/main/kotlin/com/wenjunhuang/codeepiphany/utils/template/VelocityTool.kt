package com.wenjunhuang.codeepiphany.utils.template

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

object VelocityTool {
    @JvmStatic fun camelCaseName(value: String?): String =
        if (value == null) "" else Pattern.compile("[\\s-_]").split(value).joinToString("") {
            if (it.isEmpty()) "" else it[0].uppercaseChar() + it.substring(1)
        }

    @JvmStatic fun smallCamelCaseName(value: String?): String = camelCaseName(value).let {
        if (it.isEmpty()) "" else it[0].lowercaseChar() + it.substring(1)
    }

    @JvmStatic fun snakeCaseName(value: String?): String =
        if (value == null) "" else Pattern.compile("(?=[A-Z])").split(value)
            .flatMap { Pattern.compile("[\\s_-]").split(it).asList() }.joinToString("_") { it.lowercase() }

    @JvmStatic fun slugify(value: String?): String =
        if (value == null) "" else Pattern.compile("[\\s_-]").split(value).joinToString("-") { it.lowercase() }

    @JvmStatic fun dateTime(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    override fun toString(): String = ""
}
