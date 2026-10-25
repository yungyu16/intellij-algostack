package com.wenjunhuang.codeepiphany.execution

import com.wenjunhuang.codeepiphany.presentation.*

import com.vladsch.flexmark.ext.attributes.AttributesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import com.wenjunhuang.codeepiphany.PluginBundle
import com.wenjunhuang.codeepiphany.configuration.ChallengeSettings.TestCase
import com.wenjunhuang.codeepiphany.domain.*
import com.wenjunhuang.codeepiphany.leetcode.api.model.LeetCodeChallengeData
import com.wenjunhuang.codeepiphany.model.Constants
import com.wenjunhuang.codeepiphany.utils.template.VelocityTool
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.Velocity
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import java.io.StringWriter

/** The JavaBean getters are the public contract of users' existing Velocity templates. */
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
    val content: LeetCodeChallengeData,
    val cases: List<TestCase>
) {
    fun getDescriptionText(): String = ChallengeDescriptionText.convert(description)
    fun getCode(): String = language.encloseCodeInRegion(rawCode)
    fun getCodeDojo(): String = dojo.value
    fun getLanguage(): String = language.value
    fun getLanguageVersion(): String = languageVersion.version
    fun getTestCases(): String = cases.mapIndexed { index, case ->
        "\n${PluginBundle.message("testcases.title", index + 1)}:\n-----------------------------------------------------\ninput:\n${case.input}\n-----------------------------------------------------\nexpected output:\n${case.expectedOutput}"
    }.joinToString("\n")
    override fun toString(): String = ""
}

fun renderTemplate(template: String, language: Language, challenge: Any): String {
    val context = VelocityContext().apply {
        put("challenge", challenge); put("velocity", VelocityTool)
        put("beginRegion", language.createComment(Constants.SUBMIT_CODE_REGION_BEGIN))
        put("endRegion", language.createComment(Constants.SUBMIT_CODE_REGION_END))
    }
    return StringWriter().use { writer -> Velocity.evaluate(context, writer, Constants.PROJECT_NAME, template); writer.toString() }
}

/** 题面 API 可能混合 HTML 与 Markdown，模板和实时侧栏使用同一渲染规则。 */
fun challengeDescriptionHtml(data: LeetCodeChallengeData): String {
    val options = MutableDataSet().apply { set(Parser.EXTENSIONS, listOf(AttributesExtension.create())) }
    return HtmlRenderer.builder(options).build().render(Parser.builder(options).build()
        .parse(data.translatedContent?.takeIf { it.isNotEmpty() } ?: data.content))
}

fun challengeTemplate(data: LeetCodeChallengeData, dojo: CodeDojo, language: Language, version: LanguageVersion): ChallengeTemplate {
    val snippet = data.codeSnippets.find { languageFromLeetCode(it.langSlug) == (language to version) }
        ?: throw IllegalArgumentException(PluginBundle.message("error.notSupportLanguage", language.show, version.version))
    val html = challengeDescriptionHtml(data)
    val document = Jsoup.parse(html)
    val output = if (dojo == CodeDojo.LeetCodeCN) "输出" else "Output"
    val strong = document.select("pre strong:containsOwn($output)")
    val expected = if (strong.isNotEmpty()) strong.map {
        when (val sibling = it.nextSibling()) { is TextNode -> sibling.text().trim(); else -> sibling?.toString().orEmpty() }
    } else document.select("p strong:containsOwn($output) + span.example-io").map { it.text() }
        .ifEmpty { if (dojo == CodeDojo.LeetCodeCN) document.select("blockquote p:containsOwn(输出) code").map { it.text().trim() } else emptyList() }
    return ChallengeTemplate(data.questionId, data.frontendQuestionId, dojo,
        data.translatedTitle?.takeIf { it.isNotEmpty() } ?: data.title, snippet.code, data.titleSlug, html,
        ChallengeDifficulty.fromApi(data.difficulty).value, language, version, data,
        data.exampleTestcaseList.mapIndexed { index, input -> TestCase(input, expected.getOrElse(index) { "" }) })
}
