@file:JvmName("DomainPresentation")

package io.github.yungyu16.algostack.presentation

import icons.AlgoStackIcons
import io.github.yungyu16.algostack.PluginBundle
import io.github.yungyu16.algostack.domain.*
import javax.swing.Icon

val CodeDojo.show: String get() = PluginBundle.message("leetcodecn.settings.displayName")
val CodeDojo.icon: Icon get() = AlgoStackIcons.Dojos.LEETCODECN
val Language.icon: Icon
    get() = when (this) {
        Language.Java -> AlgoStackIcons.Languages.JAVA
        Language.GO -> AlgoStackIcons.Languages.GO
    }
val ChallengeStatus.show: String get() = PluginBundle.message("challenge.status.$value")
val PageSize.show: String get() = PluginBundle.message("pagesize.$value")
val ChallengeDifficulty.show: String get() = PluginBundle.message("challenge.difficulty.$value")
fun ChallengeDifficulty.showAsHtml(): String {
    val color = when (this) {
        ChallengeDifficulty.Easy -> "#4CAF50"; ChallengeDifficulty.Medium -> "#FFC107"; ChallengeDifficulty.Hard -> "#F44336"
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
