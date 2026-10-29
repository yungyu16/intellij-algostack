package io.github.yungyu16.algostack.execution

import com.vladsch.flexmark.ext.attributes.AttributesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import io.github.yungyu16.algostack.PluginBundle
import io.github.yungyu16.algostack.domain.*
import io.github.yungyu16.algostack.leetcode.model.LeetCodeChallengeData
import io.github.yungyu16.algostack.model.Constants
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.Velocity
import java.io.StringWriter

/** 内置 Velocity 模板通过 JavaBean getter 读取题目字段。 */
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
    private val languageVersion: LanguageVersion,
    val content: LeetCodeChallengeData
) {
    fun getDescriptionText(): String = ChallengeDescriptionText.convert(description)
    fun getHeadingText(): String = "$frontendQuestionId. $name".replace("*/", "* /").replace('\r', ' ').replace('\n', ' ')
    fun getCode(): String {
        val code = when (language) {
            Language.Java -> javaTemplateStub(rawCode)
            Language.GO -> goTemplateStub(rawCode)
            else -> rawCode
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
    fun getLanguageVersion(): String = languageVersion.version
    override fun toString(): String = ""
}

fun renderTemplate(template: String, challenge: Any): String {
    val context = VelocityContext().apply {
        put("challenge", challenge); put("velocity", VelocityTool)
    }
    return StringWriter().use { writer ->
        Velocity.evaluate(
            context,
            writer,
            Constants.PROJECT_NAME,
            template
        ); writer.toString()
    }
}

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
    language: Language,
    version: LanguageVersion
): ChallengeTemplate {
    val snippet = data.codeSnippets.find { languageFromLeetCode(it.langSlug) == (language to version) }
        ?: throw IllegalArgumentException(
            PluginBundle.message(
                "error.notSupportLanguage",
                language.show,
                version.version
            )
        )
    val html = challengeDescriptionHtml(data)
    return ChallengeTemplate(
        data.questionId, data.frontendQuestionId, dojo,
        data.translatedTitle?.takeIf { it.isNotEmpty() } ?: data.title, snippet.code, data.titleSlug, html,
        ChallengeDifficulty.fromApi(data.difficulty).value, language, version, data)
}
