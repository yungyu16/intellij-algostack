@file:JvmName("DomainPresentation")
package com.wenjunhuang.codeepiphany.presentation

import com.wenjunhuang.codeepiphany.PluginBundle
import com.wenjunhuang.codeepiphany.domain.*
import icons.CodeEpiphanyIcons
import javax.swing.Icon

val CodeDojo.show: String get() = PluginBundle.message("leetcodecn.settings.displayName")
val CodeDojo.icon: Icon get() = CodeEpiphanyIcons.Dojos.LEETCODECN
val CodeDojo.iconPath: String get() = CodeEpiphanyIcons.Dojos.LEETCODECN_PATH
val Language.icon: Icon get() = when (this) {
    Language.Asm -> CodeEpiphanyIcons.Languages.ASM
    Language.C -> CodeEpiphanyIcons.Languages.C
    Language.Clojure -> CodeEpiphanyIcons.Languages.CLOJURE
    Language.Cpp -> CodeEpiphanyIcons.Languages.CPP
    Language.CSharp -> CodeEpiphanyIcons.Languages.CSHARP
    Language.Cangjie -> CodeEpiphanyIcons.Languages.CANGJIE
    Language.Dart -> CodeEpiphanyIcons.Languages.DART
    Language.Delphi -> CodeEpiphanyIcons.Languages.DELPHI
    Language.Pascal -> CodeEpiphanyIcons.Languages.PASCAL
    Language.D -> CodeEpiphanyIcons.Languages.DLANG
    Language.Erlang -> CodeEpiphanyIcons.Languages.ERLANG
    Language.Elixir -> CodeEpiphanyIcons.Languages.ELIXIR
    Language.FSharp -> CodeEpiphanyIcons.Languages.FSHARP
    Language.GO -> CodeEpiphanyIcons.Languages.GO
    Language.Haskell -> CodeEpiphanyIcons.Languages.HASKELL
    Language.Java -> CodeEpiphanyIcons.Languages.JAVA
    Language.Javascript -> CodeEpiphanyIcons.Languages.JAVASCRIPT
    Language.Julia -> CodeEpiphanyIcons.Languages.JULIA
    Language.Kotlin -> CodeEpiphanyIcons.Languages.KOTLIN
    Language.ObjectiveC -> CodeEpiphanyIcons.Languages.OBJECTIVEC
    Language.OCaml -> CodeEpiphanyIcons.Languages.OCAML
    Language.Perl -> CodeEpiphanyIcons.Languages.PERL
    Language.PHP -> CodeEpiphanyIcons.Languages.PHP
    Language.Pypy -> CodeEpiphanyIcons.Languages.PYTHON
    Language.Python -> CodeEpiphanyIcons.Languages.PYTHON
    Language.R -> CodeEpiphanyIcons.Languages.R
    Language.Racket -> CodeEpiphanyIcons.Languages.RACKET
    Language.Ruby -> CodeEpiphanyIcons.Languages.RUBY
    Language.Rust -> CodeEpiphanyIcons.Languages.RUST
    Language.Scala -> CodeEpiphanyIcons.Languages.SCALA
    Language.Swift -> CodeEpiphanyIcons.Languages.SWIFT
    Language.Typescript -> CodeEpiphanyIcons.Languages.TYPESCRIPT
    Language.Lua -> CodeEpiphanyIcons.Languages.LUA
    Language.LLVM -> CodeEpiphanyIcons.Languages.LLVM
    Language.Lisp -> CodeEpiphanyIcons.Languages.LISP
    Language.ReasonML -> CodeEpiphanyIcons.Languages.REASONML
    Language.Octave -> CodeEpiphanyIcons.Languages.OCTAVE
    Language.MySQL -> CodeEpiphanyIcons.Languages.MYSQL
    Language.PostgreSQL -> CodeEpiphanyIcons.Languages.POSTGRESQL
    Language.OracleSQL -> CodeEpiphanyIcons.Languages.ORACLESQL
    Language.MSSQL -> CodeEpiphanyIcons.Languages.MSSQL
    Language.Pandas -> CodeEpiphanyIcons.Languages.PANDAS
}
val ChallengeStatus.show: String get() = PluginBundle.message("challenge.status.$value")
val PageSize.show: String get() = PluginBundle.message("pagesize.$value")
val ChallengeDifficulty.show: String get() = when (this) {
    is ChallengeDifficulty.CodeDojoDefined -> PluginBundle.messageOfBuildKey("challenge.difficulty.${dojo.value}.$dojoValue") ?: dojoValue
    else -> PluginBundle.message("challenge.difficulty.$value")
}
fun ChallengeDifficulty.showAsHtml(): String {
    val color = when (this) {
        ChallengeDifficulty.Easy -> "#4CAF50"; ChallengeDifficulty.Medium -> "#FFC107"; ChallengeDifficulty.Hard -> "#F44336"
        ChallengeDifficulty.Advanced -> "#7C4DFF"; ChallengeDifficulty.Expert -> "#FF6D00"; is ChallengeDifficulty.CodeDojoDefined -> return ""
    }
    return "<html><font color='$color'>$show</font></html>"
}
val SubmissionResult.show: String get() = PluginBundle.message("submissionResult.$value")
fun SubmissionResult.showAsHtml(): String {
    val color = when (this) {
        SubmissionResult.Processing -> "#2196F3"
        SubmissionResult.Success -> "#00C853"
        SubmissionResult.Failure -> "#FF5252"
        SubmissionResult.CompilationError -> "#FF9800"
        SubmissionResult.Timeout -> "#9C27B0"
        SubmissionResult.MemoryLimitExceeded -> "#E040FB"
        SubmissionResult.OutputLimitExceeded -> "#D500F9"
        SubmissionResult.RuntimeError -> "#D32F2F"
        SubmissionResult.InternalError -> "#B71C1C"
        SubmissionResult.Unknown -> "#9E9E9E"
    }
    return "<html><font color='$color'>$show</font></html>"
}
