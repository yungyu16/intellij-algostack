package io.github.yungyu16.algostack.execution

import java.util.regex.Pattern

object VelocityTool {
    @JvmStatic
    fun camelCaseName(value: String?): String =
        if (value == null) "" else Pattern.compile("[\\s-_]").split(value).joinToString("") {
            if (it.isEmpty()) "" else it[0].uppercaseChar() + it.substring(1)
        }

    /** 数字展示题号至少四位；非数字题号沿用 slug 类名，避免生成非法标识符。 */
    @JvmStatic
    fun challengeFileName(frontendQuestionId: String, slug: String): String =
        if (frontendQuestionId.matches(Regex("[0-9]+"))) "P${
            frontendQuestionId.padStart(
                4,
                '0'
            )
        }_${camelCaseName(slug)}"
        else camelCaseName(slug)

    override fun toString(): String = ""
}
