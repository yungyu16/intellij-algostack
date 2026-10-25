package io.github.yungyu16.algostack.presentation.sidebar

import io.github.yungyu16.algostack.domain.ChallengeIdentity
import java.nio.file.Files
import java.nio.file.Path

object SidebarPolicy {
    fun enabled(root: String?): Boolean = root?.let { Files.exists(Path.of(it, ".leetcode")) } == true
    const val CONSOLE = "Console"
    const val PROBLEMS = "Problems"
    fun visibleTabs(challenge: ChallengeIdentity?): List<String> =
        listOf(CONSOLE, PROBLEMS) + if (challenge == null) emptyList() else listOf("Description", "Solutions")

    fun visibleTab(name: String, challenge: ChallengeIdentity?): Boolean = name in visibleTabs(challenge)
}
