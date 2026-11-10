package io.github.yungyu16.algostack.domain

import io.github.yungyu16.algostack.model.Constants

/** 新题仅支持 Java 与 Go，两者都使用 `//` 行注释，不再保留多语言注释语法抽象。 */
enum class Language(val value: String, val fileExt: String, val show: String) {
    Java("java", "java", "Java"),
    GO("go", "go", "Go");

    fun createComment(comment: String): String = "//" + comment

    fun encloseCodeInRegion(code: String): String =
        "\n${createComment(Constants.SUBMIT_CODE_REGION_BEGIN)}\n$code\n${createComment(Constants.SUBMIT_CODE_REGION_END)}"

    fun matchRegion(comment: String, region: String): Boolean =
        Regex("^//\\s*${Regex.escape(region)}\\s*$").matches(comment)

    fun extractCodeFromRegion(code: String): String {
        val region = SourceRegions.locate(code, this) ?: return ""
        return code.substring(region.start, region.end)
    }

    companion object {
        @JvmStatic
        fun fromString(value: String?): Language? = entries.find { it.value.equals(value, true) }
    }
}
