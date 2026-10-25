package io.github.yungyu16.algostack.utils

object FileUtils {
    @JvmStatic
    fun sanitizeFilename(input: String?): String {
        if (input.isNullOrEmpty()) return "unnamed_file"
        val sanitized = input.replace(Regex("[\\\\/:*?\"<>|]"), "")
            .replace(Regex("\\p{Cntrl}"), "").trim()
        if (sanitized.isEmpty()) return "unnamed_file"
        return sanitized.replace(Regex("^[.]+|[.]+$"), "")
    }
}
