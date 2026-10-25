package io.github.yungyu16.algostack.execution

import io.github.yungyu16.algostack.domain.CodeDojo
import io.github.yungyu16.algostack.domain.Language
import io.github.yungyu16.algostack.domain.LanguageVersion
import java.nio.file.Path

internal data class LanguageProjectLayout(val moduleRoot: Path, val sourceFolder: Path)

/** 生成规则仅来自内置资源；不读取旧项目的路径、模板或语言偏好。 */
internal object ChallengeConventions {
    val languages: List<Pair<Language, LanguageVersion>>
        get() = listOf(Language.Java to LanguageVersion.AnyVersion, Language.GO to LanguageVersion.AnyVersion)

    fun layout(root: Path, dojo: CodeDojo, language: Language): LanguageProjectLayout {
        val moduleRoot = root.resolve(
            when (language) {
                Language.Java -> "code/java"
                Language.GO -> "code/go"
                else -> error("Only Java and Go are supported for new challenges")
            }
        )
        val sourceRoot = if (language == Language.Java) moduleRoot.resolve("src/main/java") else moduleRoot
        return LanguageProjectLayout(moduleRoot, sourceRoot.resolve(dojo.value))
    }

    fun generation(
        root: Path,
        dojo: CodeDojo,
        language: Language,
        version: LanguageVersion = LanguageVersion.AnyVersion
    ): GenerationSettings {
        require(language to version in languages) { "Only Java and Go AnyVersion are supported for new challenges" }
        fun template(kind: String): String = requireNotNull(
            javaClass.getResourceAsStream("/templates/${dojo.value}/$kind/${language.fileExt}.ft")
        ).bufferedReader().use { it.readText().trimEnd() }
        return GenerationSettings(
            layout(root, dojo, language).sourceFolder.toString(),
            template("filename"),
            template("code")
        )
    }
}
