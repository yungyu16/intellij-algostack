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

    /** 按分隔符拆词并整体小写，用于 Go 题目目录的 snake_case 命名。 */
    @JvmStatic
    fun snakeCaseName(value: String?): String =
        if (value == null) "" else Pattern.compile("[\\s-_]").split(value).joinToString("_") {
            if (it.isEmpty()) "" else it.lowercase()
        }

    /** Go 题目目录沿用小写下划线（p0001_two_sum），与 Java 类名（P0001_TwoSum）风格区分。 */
    @JvmStatic
    fun challengeDirectoryName(frontendQuestionId: String, slug: String): String =
        if (frontendQuestionId.matches(Regex("[0-9]+"))) "p${
            frontendQuestionId.padStart(
                4,
                '0'
            )
        }_${snakeCaseName(slug)}"
        else snakeCaseName(slug)

    override fun toString(): String = ""
}
