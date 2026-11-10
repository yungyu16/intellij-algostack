package io.github.yungyu16.algostack.execution

import io.github.yungyu16.algostack.domain.CodeDojo
import io.github.yungyu16.algostack.domain.Language
import java.nio.file.Path

internal data class LanguageProjectLayout(val moduleRoot: Path, val sourceFolder: Path)

/** 生成规则仅来自内置约定；不读取旧项目的路径、模板或语言偏好。 */
internal object ChallengeConventions {
    val languages: List<Language> = listOf(Language.Java, Language.GO)

    fun layout(root: Path, dojo: CodeDojo, language: Language): LanguageProjectLayout {
        val moduleRoot = root.resolve(
            when (language) {
                Language.Java -> "code/java"
                Language.GO -> "code/go"
            }
        )
        val sourceRoot = if (language == Language.Java) moduleRoot.resolve("src/main/java") else moduleRoot
        return LanguageProjectLayout(moduleRoot, sourceRoot.resolve(dojo.value))
    }

    fun generation(root: Path, dojo: CodeDojo, language: Language): GenerationSettings {
        return GenerationSettings(
            layout(root, dojo, language).sourceFolder.toString(),
            fileNameTemplate(language),
            codeTemplate(language)
        )
    }

    private fun fileNameTemplate(language: Language): String = when (language) {
        Language.Java -> "{{fileName}}"
        Language.GO -> "{{directoryName}}/main"
    }

    private fun codeTemplate(language: Language): String = when (language) {
        Language.Java -> """package leetcodecn;

import java.util.*;

/*
{{headingText}}
@leetcode https://leetcode.cn/problems/{{slug}}/

{{descriptionText}}
*/
public class {{fileName}} {

{{code}}

    public static void main(String[] args) {
        // add your test code
    }
}"""
        Language.GO -> """package main

/*
{{headingText}}
@leetcode https://leetcode.cn/problems/{{slug}}/

{{descriptionText}}
*/
{{code}}"""
    }
}
