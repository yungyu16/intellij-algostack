package com.wenjunhuang.codeepiphany.domain

import com.wenjunhuang.codeepiphany.model.Constants
import java.net.URI

/** 平台题号只用于 API 请求，不作为本地身份，也不使用展示题号或哈希替代。 */
data class ChallengeIdentity(val dojo: CodeDojo, val slug: String, val language: Language,
    val version: LanguageVersion = LanguageVersion.AnyVersion)

object ChallengeRecognition {
    private val url = Regex("https://[^\\s<>\\\"'()]+")
    private val slugPath = Regex("^/problems/([a-z0-9]+(?:-[a-z0-9]+)*)(?:/.*)?$")
    private val metadata = Regex("leetcode-editor: language=([a-z]+); version=([^\\s*]*)")

    private val javaIdentifier = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*"
    private val javaPackage = Regex("package[ \\t\\r\\n\\f]+($javaIdentifier(?:[ \\t\\r\\n\\f]*\\.[ \\t\\r\\n\\f]*$javaIdentifier)*)[ \\t\\r\\n\\f]*;")
    private val goPackage = Regex("package[ \\t\\r\\n]+([\\p{L}_][\\p{L}\\p{Nd}_]*)[ \\t]*(?:;|(?=\\r?\\n|//|$))")
    private val javaReserved = ("abstract assert boolean break byte case catch char class const continue default do double else enum extends " +
        "final finally float for goto if implements import instanceof int interface long native new package private protected public " +
        "return short static strictfp super switch synchronized this throw throws transient try void volatile while true false null _").split(' ').toSet()
    private val goReserved = ("break default func interface select case defer go map struct chan else goto package switch const fallthrough " +
        "if range type continue for import return var _").split(' ').toSet()

    /** 只接受头部注释（可跨一个 Java/Go package 声明）中的可信链接及完整提交区域。 */
    fun recognize(text: String, extension: String, fallback: ChallengeIdentity? = null): ChallengeIdentity? {
        val candidates = Language.entries.filter { it.fileExt == extension }
        if (candidates.isEmpty()) return null
        val header = headerComments(text.removePrefix("\uFEFF"), candidates)
        val links = url.findAll(header).mapNotNull { match ->
            val uri = try { URI(match.value.trimEnd('.', ',', ';', '*')) } catch (_: Exception) { return@mapNotNull null }
            if (uri.scheme != "https" || uri.userInfo != null || uri.port != -1) return@mapNotNull null
            val dojo = when (uri.host) {
                "leetcode.com", "www.leetcode.com" -> CodeDojo.LeetCode
                "leetcode.cn", "www.leetcode.cn" -> CodeDojo.LeetCodeCN
                else -> return@mapNotNull null
            }
            val slug = slugPath.matchEntire(uri.path)?.groupValues?.get(1) ?: return@mapNotNull null
            dojo to slug
        }.distinct().toList()
        if (links.size > 1) return null
        val metadataEntries = metadata.findAll(header).toList()
        if (metadataEntries.map { it.value }.distinct().size > 1) return null
        val meta = metadataEntries.firstOrNull()
        val specified = meta?.let { Language.fromString(it.groupValues[1]) ?: return null }
        val language = specified ?: fallback?.language?.takeIf { it in candidates } ?: candidates.singleOrNull() ?: return null
        if (language !in candidates) return null
        val lines = text.lineSequence().map { it.trim() }.toList()
        val begin = lines.indexOfFirst { language.matchRegion(it, Constants.SUBMIT_CODE_REGION_BEGIN) }
        val end = lines.indexOfFirst { language.matchRegion(it, Constants.SUBMIT_CODE_REGION_END) }
        if (begin < 0 || end <= begin) return null
        val link = links.singleOrNull() ?: fallback?.let { it.dojo to it.slug } ?: return null
        val version = meta?.let { LanguageVersion.fromString(it.groupValues[2]) }
            ?: fallback?.takeIf { it.dojo == link.first && it.slug == link.second && it.language == language }?.version
            ?: LanguageVersion.AnyVersion
        return ChallengeIdentity(link.first, link.second, language, version)
    }

    private fun headerComments(text: String, candidates: List<Language>): String {
        val header = StringBuilder()
        var offset = 0
        var packageSeen = false
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
                header.appendLine(text.substring(offset, end))
                offset = end
                continue
            }
            // 仅跳过完整的 package 声明；import、类型/函数/表达式都是头部边界，不向实现内找 URL。
            // 不解析注解、Unicode 转义或 package 名内夹注释等完整语言语法，避免宽松猜测。
            if (packageSeen) break
            val language = candidates.singleOrNull()
            val declaration = when (language) {
                Language.Java -> javaPackage.matchAt(text, offset)
                Language.GO -> goPackage.matchAt(text, offset)
                else -> null
            } ?: break
            val names = declaration.groupValues[1].split('.').map { it.trim() }
            val reserved = if (language == Language.Java) javaReserved else goReserved
            if (names.any { it in reserved }) break
            offset = declaration.range.last + 1
            packageSeen = true
        }
        return header.toString()
    }

    fun header(identity: ChallengeIdentity): String = with(identity) {
        language.createComment(" https://${dojo.domain}/problems/$slug/") + "\n" +
            language.createComment(" leetcode-editor: language=${language.value}; version=${version.version}") + "\n"
    }
}
