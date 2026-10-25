package com.wenjunhuang.codeepiphany.presentation.sidebar

import com.wenjunhuang.codeepiphany.domain.ChallengeIdentity
import java.nio.file.Files
import java.nio.file.Path

object SidebarPolicy {
    fun enabled(root: String?): Boolean = root?.let { Files.exists(Path.of(it, ".leetcode")) } == true
    fun visibleTab(name: String, challenge: ChallengeIdentity?): Boolean =
        name !in setOf("Description", "Solutions") || challenge != null
}
