package com.wenjunhuang.codeepiphany.domain

import com.wenjunhuang.codeepiphany.model.Constants

/** Java/Go 共用的轻量词法扫描；偏移始终指向原文，不规范化换行或改写提交代码。 */
internal object SourceRegions {
    data class Comment(val start: Int, val end: Int, val line: Boolean)
    data class Region(val start: Int, val end: Int)

    fun comments(text: String, language: Language): List<Comment> {
        require(language == Language.Java || language == Language.GO)
        val result = mutableListOf<Comment>()
        var offset = 0
        while (offset < text.length) {
            if (text.startsWith("//", offset)) {
                val end = text.indexOfAny(charArrayOf('\r', '\n'), offset).takeIf { it >= 0 } ?: text.length
                result.add(Comment(offset, end, true))
                offset = end
            } else if (text.startsWith("/*", offset)) {
                val end = text.indexOf("*/", offset + 2)
                if (end < 0) break
                result.add(Comment(offset, end + 2, false))
                offset = end + 2
            } else if (text[offset] == '"' || text[offset] == '\'' || language == Language.GO && text[offset] == '`') {
                val quote = text[offset]
                val delimiter = if (language == Language.Java && text.startsWith("\"\"\"", offset)) "\"\"\"" else quote.toString()
                offset += delimiter.length
                while (offset < text.length) {
                    if (text[offset] == '\\' && quote != '`') { offset = (offset + 2).coerceAtMost(text.length); continue }
                    if (text.startsWith(delimiter, offset)) { offset += delimiter.length; break }
                    offset++
                }
            } else offset++
        }
        return result
    }

    /** 必须恰好一对独占行标记；不拼接多段，不从歧义区域中挑选第一段。 */
    fun locate(text: String, language: Language, scannedComments: List<Comment>? = null): Region? {
        val lines = if (language == Language.Java || language == Language.GO) {
            (scannedComments ?: comments(text, language)).filter { it.line }.mapNotNull { comment ->
                val start = text.lastIndexOfAny(charArrayOf('\r', '\n'), comment.start - 1) + 1
                if (!text.substring(start, comment.start).removePrefix("\uFEFF").isBlank()) null
                else Triple(start, comment.end, text.substring(comment.start, comment.end).trim())
            }
        } else {
            // 其他语言沿用整行模板标记契约，不冒充完整的多语言词法解析。
            Regex("[^\\r\\n]+|[\\r\\n]").findAll(text).map {
                Triple(it.range.first, it.range.last + 1, it.value.removePrefix("\uFEFF").trim())
            }.toList()
        }
        val begins = lines.filter { language.matchRegion(it.third, Constants.SUBMIT_CODE_REGION_BEGIN) }
        val ends = lines.filter { language.matchRegion(it.third, Constants.SUBMIT_CODE_REGION_END) }
        val begin = begins.singleOrNull() ?: return null
        val end = ends.singleOrNull() ?: return null
        if (begin.first >= end.first) return null
        var codeStart = begin.second
        if (text.getOrNull(codeStart) == '\r') codeStart++
        if (text.getOrNull(codeStart) == '\n') codeStart++
        return Region(codeStart, end.first)
    }
}
