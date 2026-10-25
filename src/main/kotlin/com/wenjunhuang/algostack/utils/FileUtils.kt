package com.wenjunhuang.algostack.utils

import com.intellij.openapi.util.io.FileUtil
import java.io.File

object FileUtils {
    @JvmStatic
    fun sanitizeFilename(input: String?): String {
        if (input.isNullOrEmpty()) return "unnamed_file"
        val sanitized = input.replace(Regex("[\\\\/:*?\"<>|]"), "")
            .replace(Regex("\\p{Cntrl}"), "").trim()
        if (sanitized.isEmpty()) return "unnamed_file"
        return sanitized.replace(Regex("^[.]+|[.]+$"), "")
    }

    @JvmStatic
    fun getJavaMainClassName(filePath: String): String {
        val content = FileUtil.loadFile(File(filePath), Charsets.UTF_8)
        val packageName = Regex("package\\s+([\\w.]+);").find(content)?.groupValues?.get(1).orEmpty()
        val className = Regex("public\\s+class\\s+(\\w+)\\s*\\{").find(content)?.groupValues?.get(1) ?: "Main"
        return if (packageName.isEmpty()) className else "$packageName.$className"
    }
}
