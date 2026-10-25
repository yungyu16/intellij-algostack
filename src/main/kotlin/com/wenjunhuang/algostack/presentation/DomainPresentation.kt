@file:JvmName("DomainPresentation")
package com.wenjunhuang.algostack.presentation

import com.wenjunhuang.algostack.PluginBundle
import com.wenjunhuang.algostack.domain.*
import icons.AlgoStackIcons
import javax.swing.Icon

val CodeDojo.show: String get() = PluginBundle.message("leetcodecn.settings.displayName")
val CodeDojo.icon: Icon get() = AlgoStackIcons.Dojos.LEETCODECN
val CodeDojo.iconPath: String get() = AlgoStackIcons.Dojos.LEETCODECN_PATH
val Language.icon: Icon get() = when (this) {
    Language.Asm -> AlgoStackIcons.Languages.ASM
    Language.C -> AlgoStackIcons.Languages.C
    Language.Clojure -> AlgoStackIcons.Languages.CLOJURE
    Language.Cpp -> AlgoStackIcons.Languages.CPP
    Language.CSharp -> AlgoStackIcons.Languages.CSHARP
    Language.Cangjie -> AlgoStackIcons.Languages.CANGJIE
    Language.Dart -> AlgoStackIcons.Languages.DART
    Language.Delphi -> AlgoStackIcons.Languages.DELPHI
    Language.Pascal -> AlgoStackIcons.Languages.PASCAL
    Language.D -> AlgoStackIcons.Languages.DLANG
    Language.Erlang -> AlgoStackIcons.Languages.ERLANG
    Language.Elixir -> AlgoStackIcons.Languages.ELIXIR
    Language.FSharp -> AlgoStackIcons.Languages.FSHARP
    Language.GO -> AlgoStackIcons.Languages.GO
    Language.Haskell -> AlgoStackIcons.Languages.HASKELL
    Language.Java -> AlgoStackIcons.Languages.JAVA
    Language.Javascript -> AlgoStackIcons.Languages.JAVASCRIPT
    Language.Julia -> AlgoStackIcons.Languages.JULIA
    Language.Kotlin -> AlgoStackIcons.Languages.KOTLIN
    Language.ObjectiveC -> AlgoStackIcons.Languages.OBJECTIVEC
    Language.OCaml -> AlgoStackIcons.Languages.OCAML
    Language.Perl -> AlgoStackIcons.Languages.PERL
    Language.PHP -> AlgoStackIcons.Languages.PHP
    Language.Pypy -> AlgoStackIcons.Languages.PYTHON
    Language.Python -> AlgoStackIcons.Languages.PYTHON
    Language.R -> AlgoStackIcons.Languages.R
    Language.Racket -> AlgoStackIcons.Languages.RACKET
    Language.Ruby -> AlgoStackIcons.Languages.RUBY
    Language.Rust -> AlgoStackIcons.Languages.RUST
    Language.Scala -> AlgoStackIcons.Languages.SCALA
    Language.Swift -> AlgoStackIcons.Languages.SWIFT
    Language.Typescript -> AlgoStackIcons.Languages.TYPESCRIPT
    Language.Lua -> AlgoStackIcons.Languages.LUA
    Language.LLVM -> AlgoStackIcons.Languages.LLVM
    Language.Lisp -> AlgoStackIcons.Languages.LISP
    Language.ReasonML -> AlgoStackIcons.Languages.REASONML
    Language.Octave -> AlgoStackIcons.Languages.OCTAVE
    Language.MySQL -> AlgoStackIcons.Languages.MYSQL
    Language.PostgreSQL -> AlgoStackIcons.Languages.POSTGRESQL
    Language.OracleSQL -> AlgoStackIcons.Languages.ORACLESQL
    Language.MSSQL -> AlgoStackIcons.Languages.MSSQL
    Language.Pandas -> AlgoStackIcons.Languages.PANDAS
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
