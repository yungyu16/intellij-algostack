package com.wenjunhuang.algostack.domain

import java.net.URI

/** 平台题号只用于 API 请求，不作为本地身份，也不使用展示题号或哈希替代。 */
data class ChallengeIdentity(val dojo: CodeDojo, val slug: String, val language: Language,
    val version: LanguageVersion = LanguageVersion.AnyVersion)

object ChallengeRecognition {
    private val url = Regex("(?<![\\p{L}\\p{N}_:/@.-])https://[^\\s<>\\\"'()\\[\\]]+")
    private val slugPath = Regex("^/problems/([a-z0-9]+(?:-[a-z0-9]+)*)(?:/.*)?$")

    /** Java/Go 全文件真实注释候选，其他语言保留头部注释契约；所有语言共用唯一提交区域定位。 */
    fun recognize(text: String, extension: String): ChallengeIdentity? {
        val candidates = Language.entries.filter { it.fileExt == extension }
        if (candidates.isEmpty()) return null
        val sourceLanguage = candidates.singleOrNull()
        val comments = if (sourceLanguage == Language.Java || sourceLanguage == Language.GO)
            SourceRegions.comments(text, sourceLanguage) else null
        val header = comments?.joinToString("\n") {
            text.substring(it.start + 2, if (it.line) it.end else it.end - 2)
        }
            ?: headerComments(text.removePrefix("\uFEFF"), candidates)
        // 显式国际站链接不转换为中国站身份。
        if (url.findAll(header).any { match ->
            runCatching { URI(match.value.trimEnd('.', ',', ';', '*', '，', '。', '；', '！', '!', '）')).host in setOf("leetcode.com", "www.leetcode.com") }.getOrDefault(false)
        }) return null
        val links = url.findAll(header).mapNotNull { match ->
            val uri = try { URI(match.value.trimEnd('.', ',', ';', '*', '，', '。', '；', '！', '!', '）')) } catch (_: Exception) { return@mapNotNull null }
            if (uri.scheme != "https" || uri.userInfo != null || uri.port != -1) return@mapNotNull null
            val dojo = when (uri.host) {
                "leetcode.cn", "www.leetcode.cn" -> CodeDojo.LeetCodeCN
                else -> return@mapNotNull null
            }
            val slug = slugPath.matchEntire(uri.path)?.groupValues?.get(1) ?: return@mapNotNull null
            dojo to slug
        }.distinct().toList()
        if (links.size > 1) return null
        val language = sourceLanguage ?: return null
        if (SourceRegions.locate(text, language, comments) == null) return null
        val link = links.singleOrNull() ?: return null
        return ChallengeIdentity(link.first, link.second, language)
    }

    private fun headerComments(text: String, candidates: List<Language>): String {
        val header = StringBuilder()
        var offset = 0
        while (offset < text.length) {
            if (text[offset].isWhitespace()) { offset++; continue }
            if (text.startsWith("/*", offset)) {
                val end = text.indexOf("*/", offset + 2)
                if (end < 0) break
                header.appendLine(text.substring(offset + 2, end))
                offset = end + 2
                continue
            }
            if (candidates.any { text.startsWith(it.createComment("").removeSuffix("*)"), offset) }) {
                val end = text.indexOfAny(charArrayOf('\r', '\n'), offset).takeIf { it >= 0 } ?: text.length
                val prefix = candidates.first { text.startsWith(it.createComment("").removeSuffix("*)"), offset) }
                    .createComment("").removeSuffix("*)")
                header.appendLine(text.substring(offset + prefix.length, end))
                offset = end
                continue
            }
            break
        }
        return header.toString()
    }

}
