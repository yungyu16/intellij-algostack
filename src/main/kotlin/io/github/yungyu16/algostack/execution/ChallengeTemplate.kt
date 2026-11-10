package io.github.yungyu16.algostack.execution

import com.vladsch.flexmark.ext.attributes.AttributesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import io.github.yungyu16.algostack.PluginBundle
import io.github.yungyu16.algostack.domain.*
import io.github.yungyu16.algostack.leetcode.model.LeetCodeChallengeData
import io.github.yungyu16.algostack.model.Constants

/** 内置代码模板通过 getter 读取题目字段，renderTemplate 只做占位符替换。 */
class ChallengeTemplate(
    val questionId: String,
    val frontendQuestionId: String,
    private val dojo: CodeDojo,
    val name: String,
    private val rawCode: String,
    val slug: String,
    val description: String,
    val difficulty: String,
    private val language: Language,
    val content: LeetCodeChallengeData
) {
    fun getDescriptionText(): String = ChallengeDescriptionText.convert(description)
    fun getHeadingText(): String = "$frontendQuestionId. $name".replace("*/", "* /").replace('\r', ' ').replace('\n', ' ')
    fun getFileName(): String = ChallengeNames.challengeFileName(frontendQuestionId, slug)
    fun getDirectoryName(): String = ChallengeNames.challengeDirectoryName(frontendQuestionId, slug)
    fun getCode(): String {
        val code = when (language) {
            Language.Java -> javaTemplateStub(rawCode)
            Language.GO -> goTemplateStub(rawCode)
        }
        // 新骨架不继承编辑器包围动作的前导换行，模板负责外围空行。
        // Java 两条标记预置为外层类成员缩进，避免 IDEA 保留其首列位置。
        val markerIndent = if (language == Language.Java) "    " else ""
        return listOf(
            markerIndent + language.createComment(" ${Constants.SUBMIT_CODE_REGION_BEGIN}"),
            code.trimEnd(),
            markerIndent + language.createComment(" ${Constants.SUBMIT_CODE_REGION_END}")
        ).joinToString("\n")
    }

    fun getCodeDojo(): String = dojo.value
    fun getLanguage(): String = language.value
}

/** 模板只保留 {{xxx}} 占位符，逐个替换；题面内容若包含其他占位符字面量，会被后续替换步骤展开。 */
fun renderTemplate(template: String, challenge: ChallengeTemplate): String = template
    .replace("{{questionId}}", challenge.questionId)
    .replace("{{frontendQuestionId}}", challenge.frontendQuestionId)
    .replace("{{headingText}}", challenge.getHeadingText())
    .replace("{{slug}}", challenge.slug)
    .replace("{{descriptionText}}", challenge.getDescriptionText())
    .replace("{{fileName}}", challenge.getFileName())
    .replace("{{directoryName}}", challenge.getDirectoryName())
    .replace("{{code}}", challenge.getCode())

/** 题面 API 可能混合 HTML 与 Markdown，模板和实时侧栏使用同一渲染规则。 */
fun challengeDescriptionHtml(data: LeetCodeChallengeData): String {
    val options = MutableDataSet().apply { set(Parser.EXTENSIONS, listOf(AttributesExtension.create())) }
    return HtmlRenderer.builder(options).build().render(
        Parser.builder(options).build()
        .parse(data.translatedContent?.takeIf { it.isNotEmpty() } ?: data.content))
}

fun challengeTemplate(
    data: LeetCodeChallengeData,
    dojo: CodeDojo,
    language: Language
): ChallengeTemplate {
    val snippet = data.codeSnippets.find { languageFromLeetCode(it.langSlug) == language }
        ?: throw IllegalArgumentException(
            PluginBundle.message("error.notSupportLanguage", language.show)
        )
    val html = challengeDescriptionHtml(data)
    return ChallengeTemplate(
        data.questionId, data.frontendQuestionId, dojo,
        data.translatedTitle?.takeIf { it.isNotEmpty() } ?: data.title, snippet.code, data.titleSlug, html,
        ChallengeDifficulty.fromApi(data.difficulty).value, language, data)
}
