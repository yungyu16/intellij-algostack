package io.github.yungyu16.algostack.domain

typealias FileNameTemplate = String
typealias CodeTemplate = String

fun Language.toLeetCode(version: LanguageVersion): String = when (this) {
    Language.GO -> "golang${version.version}"
    Language.Pandas -> "pythondata"
    else -> value + version.version
}

fun languageFromLeetCode(value: String): Pair<Language, LanguageVersion>? {
    val match = Regex("^([a-zA-Z]*)(\\d*)$").matchEntire(value) ?: return null
    val name = when (match.groupValues[1]) { "golang" -> "go"; "pythondata" -> "pandas"; else -> match.groupValues[1] }
    return Language.fromString(name)?.let { it to LanguageVersion.fromString(match.groupValues[2]) }
}
