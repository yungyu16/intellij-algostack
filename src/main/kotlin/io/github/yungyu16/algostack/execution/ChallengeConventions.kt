package io.github.yungyu16.algostack.execution

import io.github.yungyu16.algostack.domain.CodeDojo
import io.github.yungyu16.algostack.domain.Language
import io.github.yungyu16.algostack.domain.LanguageVersion
import java.nio.file.Path

/** 生成规则仅来自内置资源；不读取旧项目的路径、模板或语言偏好。 */
internal object ChallengeConventions {
    val languages: List<Pair<Language, LanguageVersion>>
        get() = listOf(Language.Java to LanguageVersion.AnyVersion, Language.GO to LanguageVersion.AnyVersion)

    fun generation(root: Path, dojo: CodeDojo, language: Language, version: LanguageVersion = LanguageVersion.AnyVersion): GenerationSettings {
        require(language to version in languages) { "Only Java and Go AnyVersion are supported for new challenges" }
        val folder = if (language == Language.Java) "java/src/main/java" else "go"
        fun template(kind: String): String = requireNotNull(
            javaClass.getResourceAsStream("/templates/${dojo.value}/$kind/${language.fileExt}.ft")
        ).bufferedReader().use { it.readText().trimEnd() }
        return GenerationSettings(root.resolve("code/$folder/${dojo.value}").toString(), template("filename"), template("code"))
    }
}
