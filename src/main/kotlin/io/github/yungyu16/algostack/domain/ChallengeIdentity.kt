package io.github.yungyu16.algostack.domain

import java.net.URI

/** 平台题号只用于 API 请求，不作为本地身份，也不使用展示题号或哈希替代。 */
data class ChallengeIdentity(
    val dojo: CodeDojo, val slug: String, val language: Language,
    val version: LanguageVersion = LanguageVersion.AnyVersion
)

internal enum class RecognitionFailure(val description: String) {
    UNSUPPORTED_LANGUAGE("文件扩展名无法唯一确定语言"),
    INVALID_DECLARATION("@leetcode 声明格式或题目 URL 非法；应独占注释行并使用可信中国站 URL"),
    CONFLICTING_DECLARATIONS("@leetcode 主身份声明重复或冲突；每份文件只能声明一次"),
    INTERNATIONAL_URL("无主身份声明的旧文件包含国际站链接，不能确定中国站身份"),
    MISSING_URL("未找到可信的中国站题目身份 URL"),
    CONFLICTING_URLS("无主身份声明的旧文件包含不同题目链接，无法唯一识别"),
    INVALID_REGION("提交区域缺失、倒序或不唯一；必须保留唯一一对真实行注释标记"),
    IDENTITY_MISMATCH("生成文件恢复出的身份与本次预期题目不一致")
}

internal data class RecognitionResult(val identity: ChallengeIdentity? = null, val failure: RecognitionFailure? = null)
internal class ChallengeSourceException(val reason: RecognitionFailure) : IllegalArgumentException(reason.description)

object ChallengeRecognition {
    private val url = Regex("(?<![\\p{L}\\p{N}_:/@.-])https://[^\\s<>\\\"'()\\[\\]]+")
    private val slugPath = Regex("^/problems/([a-z0-9]+(?:-[a-z0-9]+)*)(?:/.*)?$")
    private val declaration = Regex("@leetcode[ \\t]+([^\\s]+)")

    /** Java/Go 优先使用显式主身份；无声明时保留全文件真实注释的严格兼容规则。 */
    fun recognize(text: String, extension: String): ChallengeIdentity? = inspect(text, extension).identity

    internal fun inspect(text: String, extension: String): RecognitionResult {
        val candidates = Language.entries.filter { it.fileExt == extension }
        val language = candidates.singleOrNull() ?: return rejected(RecognitionFailure.UNSUPPORTED_LANGUAGE)
        val comments = if (language == Language.Java || language == Language.GO) SourceRegions.comments(text, language) else null
        val commentTexts = comments?.map {
            text.substring(it.start + 2, if (it.line) it.end else it.end - 2)
        } ?: listOf(headerComments(text.removePrefix("\uFEFF"), candidates))
        // 只有 Java/Go 的真实注释行可声明身份；正文中的普通链接只是引用。
        val declarations = if (comments != null) commentTexts.flatMap { comment ->
            comment.lineSequence().map { it.trim().removePrefix("*").trimStart() }
                .filter { it.startsWith("@leetcode", ignoreCase = true) }.toList()
        } else emptyList()
        val link = if (declarations.isNotEmpty()) {
            if (declarations.size != 1) return rejected(RecognitionFailure.CONFLICTING_DECLARATIONS)
            val value = declaration.matchEntire(declarations.single())?.groupValues?.get(1)
                ?: return rejected(RecognitionFailure.INVALID_DECLARATION)
            // 有声明就必须验证它；非法声明不能回退到正文 URL 猜身份。
            trustedLink(value) ?: return rejected(RecognitionFailure.INVALID_DECLARATION)
        } else {
            val links = commentTexts.flatMap { comment ->
                url.findAll(comment).map { it.value.trimEnd('.', ',', ';', '*', '，', '。', '；', '！', '!', '）') }.toList()
            }
            if (links.any { parsedUri(it)?.host in setOf("leetcode.com", "www.leetcode.com") })
                return rejected(RecognitionFailure.INTERNATIONAL_URL)
            val trusted = links.mapNotNull(::trustedLink).distinct()
            if (trusted.size > 1) return rejected(RecognitionFailure.CONFLICTING_URLS)
            trusted.singleOrNull() ?: return rejected(RecognitionFailure.MISSING_URL)
        }
        if (SourceRegions.locate(text, language, comments) == null) return rejected(RecognitionFailure.INVALID_REGION)
        return RecognitionResult(identity = ChallengeIdentity(link.first, link.second, language))
    }

    private fun rejected(failure: RecognitionFailure) = RecognitionResult(failure = failure)

    private fun parsedUri(value: String): URI? = runCatching { URI(value) }.getOrNull()

    private fun trustedLink(value: String): Pair<CodeDojo, String>? {
        if (!url.matches(value)) return null
        val uri = parsedUri(value) ?: return null
        if (uri.scheme != "https" || uri.userInfo != null || uri.port != -1
            || uri.host !in setOf("leetcode.cn", "www.leetcode.cn")) return null
        val slug = slugPath.matchEntire(uri.path)?.groupValues?.get(1) ?: return null
        return CodeDojo.LeetCodeCN to slug
    }

    private fun headerComments(text: String, candidates: List<Language>): String {
        val header = StringBuilder()
        var offset = 0
        while (offset < text.length) {
            if (text[offset].isWhitespace()) {
                offset++; continue
            }
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
