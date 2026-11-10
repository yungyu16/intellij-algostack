package io.github.yungyu16.algostack.domain

/** 与力扣平台的语言标识互转；仅覆盖 Java 与 Go。 */
fun Language.toLeetCode(): String = when (this) {
    Language.Java -> "java"
    Language.GO -> "golang"
}

fun languageFromLeetCode(value: String): Language? = when (value) {
    "java" -> Language.Java
    "golang" -> Language.GO
    else -> null
}
