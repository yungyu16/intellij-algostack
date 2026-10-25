package com.wenjunhuang.codeepiphany.domain

import com.wenjunhuang.codeepiphany.model.Constants
import kotlinx.serialization.Serializable

@Serializable(with = LanguageSerializer::class)
enum class Language(val value: String, val fileExt: String, val show: String) {
    Asm("asm", "asm", "Assembly"),
    C("c", "c", "C"),
    Clojure("clojure", "clj", "Clojure"),
    Cpp("cpp", "cc", "C++"),
    CSharp("csharp", "cs", "C#"),
    Cangjie("cangjie", "cj", "Cangjie"),
    Dart("dart", "dart", "Dart"),
    Delphi("delphi", "pas", "Delphi"),
    Pascal("pascal", "pas", "Pascal"),
    D("d", "d", "D"),
    Erlang("erlang", "erl", "Erlang"),
    Elixir("elixir", "ex", "Elixir"),
    FSharp("fsharp", "fs", "F#"),
    GO("go", "go", "Go"),
    Haskell("haskell", "hs", "Haskell"),
    Java("java", "java", "Java"),
    Javascript("javascript", "js", "Javascript"),
    Julia("julia", "jl", "Julia"),
    Kotlin("kotlin", "kt", "Kotlin"),
    ObjectiveC("objectivec", "objc", "ObjectiveC"),
    OCaml("ocaml", "ml", "OCaml"),
    Perl("perl", "pl", "Perl"),
    PHP("php", "php", "PHP"),
    Pypy("pypy", "py", "Pypy"),
    Python("python", "py", "Python"),
    R("r", "r", "R"),
    Racket("racket", "rkt", "Racket"),
    Ruby("ruby", "rb", "Ruby"),
    Rust("rust", "rs", "Rust"),
    Scala("scala", "scala", "Scala"),
    Swift("swift", "swift", "Swift"),
    Typescript("typescript", "ts", "Typescript"),
    Lua("lua", "lua", "Lua"),
    LLVM("llvm", "ll", "LLVM"),
    Lisp("lisp", "lisp", "Lisp"),
    ReasonML("reasonml", "re", "ReasonML"),
    Octave("octave", "m", "Octave"),
    MySQL("mysql", "sql", "MySQL"),
    PostgreSQL("postgresql", "sql", "PostgreSQL"),
    OracleSQL("oraclesql", "sql", "OracleSQL"),
    MSSQL("mssql", "sql", "MSSQL"),
    Pandas("pandas", "py", "Pandas");

    private fun commentPrefix(): String = when (this) {
        C, Cpp, CSharp, D, GO, Java, Kotlin, ObjectiveC, PHP, Rust, Scala, Swift, Dart, Javascript,
        Typescript, Cangjie, Delphi, Pascal, FSharp, ReasonML -> "//"
        Clojure, Racket, Asm, LLVM, Lisp -> ";"
        Pypy, Python, Ruby, Julia, Perl, R, Elixir, Pandas -> "#"
        Haskell, Lua, MySQL, PostgreSQL, OracleSQL, MSSQL -> "--"
        Erlang, Octave -> "%"
        OCaml -> "(*"
    }
    private fun commentPostfix(): String = if (this == OCaml) "*)" else ""
    fun createComment(comment: String): String = commentPrefix() + comment + commentPostfix()
    fun encloseCodeInRegion(code: String): String = "\n${createComment(Constants.SUBMIT_CODE_REGION_BEGIN)}\n$code\n${createComment(Constants.SUBMIT_CODE_REGION_END)}"
    fun matchRegion(comment: String, region: String): Boolean = Regex(
        "^${Regex.escape(commentPrefix())}\\s*${Regex.escape(region)}\\s*${Regex.escape(commentPostfix())}$"
    ).matches(comment)

    fun extractCodeFromRegion(code: String): String {
        val region = SourceRegions.locate(code, this) ?: return ""
        return code.substring(region.start, region.end)
    }

    companion object {
        @JvmStatic fun fromString(value: String?): Language? = entries.find { it.value.equals(value, true) }
        @JvmStatic fun prettyPrint(language: Language, version: LanguageVersion): String {
            val text = version.version
            Regex("^([\\d.]+)\\((.*)\\)$").matchEntire(text)?.let {
                return "${language.show}${it.groupValues[1]} ${it.groupValues[2]}"
            }
            if (Regex("^[\\d.]+$").matches(text)) return language.show + text
            Regex("^\\((.+)\\)$").matchEntire(text)?.let { return "${language.show} ${it.groupValues[1]}" }
            return "${language.show} $text"
        }
    }
}

object LanguageSerializer : StableValueSerializer<Language>("Language", Language.entries, { it.value })

sealed class LanguageVersion(val version: String) : Comparable<LanguageVersion> {
    data object AnyVersion : LanguageVersion("")
    data class SpecificVersion(val value: String) : LanguageVersion(value)
    override fun compareTo(other: LanguageVersion): Int = when {
        this == AnyVersion && other == AnyVersion -> 0
        this == AnyVersion -> -1
        other == AnyVersion -> 1
        else -> {
            val left = version.toIntOrNull(); val right = other.version.toIntOrNull()
            if (left != null && right != null) left.compareTo(right) else version.compareTo(other.version)
        }
    }
    companion object {
        @JvmStatic fun fromString(value: String?): LanguageVersion = if (value.isNullOrEmpty()) AnyVersion else SpecificVersion(value)
    }
}
