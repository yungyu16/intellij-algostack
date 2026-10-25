package io.github.yungyu16.algostack.execution

import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/** 供源码注释使用的有限 HTML 清理，不是通用 HTML 渲染器或安全过滤器。 */
internal object ChallengeDescriptionText {
    // 只删除已知标签，不能用 <[^>]+> 或整篇 DOM 提取，以免吞掉 List<Node>、比较式等题面内容。
    private val tags = Regex(
        """</?(a|b|blockquote|br|caption|code|col|colgroup|dd|del|div|dl|dt|em|font|h[1-6]|hr|i|img|li|meta|ol|p|pre|s|small|span|strong|sub|sup|table|tbody|td|th|thead|tr|u|ul)(?:\s+(?:[^<>"']|"[^"]*"|'[^']*')*)?\s*/?>""",
        RegexOption.IGNORE_CASE
    )

    fun convert(html: String): String {
        val text = tags.replace(html) { match ->
            val closing = match.value.startsWith("</")
            when {
                closing -> ""
                match.groupValues[1].equals("sup", ignoreCase = true) -> "^"
                match.groupValues[1].equals("sub", ignoreCase = true) -> "_"
                match.groupValues[1].equals("img", ignoreCase = true) -> {
                    // 仅解析图片标签；不访问 URL。支持单双引号、无引号和含空格的属性。
                    val source = Jsoup.parseBodyFragment(match.value).selectFirst("img")?.attr("src").orEmpty()
                    // attr 已解码，重新转义 &，确保下方统一解码仅执行一层。
                    if (source.isEmpty()) "" else "[图片: ${source.replace("&", "&amp;")}]"
                }

                else -> ""
            }
        }
        // 先清理标签再解码，避免把 &lt;Node&gt; 当作标签；最后防止题面结束源码块注释。
        return Parser.unescapeEntities(text, false).replace('\u00a0', ' ').replace("*/", "* /")
    }
}
