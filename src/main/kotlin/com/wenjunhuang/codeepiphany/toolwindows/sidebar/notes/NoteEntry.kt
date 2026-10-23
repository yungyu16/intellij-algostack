package com.wenjunhuang.codeepiphany.toolwindows.sidebar.notes

import com.wenjunhuang.codeepiphany.domain.Language
import com.wenjunhuang.codeepiphany.domain.LanguageVersion

sealed interface NoteEntry {
    data class NoteNode(val solutionId: Long, val title: String, val submissions: Int, val isDefault: Boolean) : NoteEntry
    data class LanguageNode(val language: Language, val version: LanguageVersion, val submissionCount: Int) : NoteEntry
}
