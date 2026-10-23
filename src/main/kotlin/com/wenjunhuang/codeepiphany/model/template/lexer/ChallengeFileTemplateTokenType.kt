package com.wenjunhuang.codeepiphany.model.template.lexer

import com.intellij.lang.Language
import com.intellij.psi.tree.IElementType

object ChallengeFileTemplateTokenType {
    @JvmField val ESCAPE = IElementType("ESCAPE", Language.ANY)
    @JvmField val TEXT = IElementType("TEXT", Language.ANY)
    @JvmField val IDENTIFIER = IElementType("IDENTIFIER", Language.ANY)
    @JvmField val START_PAREN = IElementType("START_PAREN", Language.ANY)
    @JvmField val END_PAREN = IElementType("END_PAREN", Language.ANY)
    @JvmField val START_REF = IElementType("START_REF", Language.ANY)
    @JvmField val START_REF_SILENT = IElementType("START_REF_SILENT", Language.ANY)
    @JvmField val START_REF_FORMAL = IElementType("START_REF_FORMAL", Language.ANY)
    @JvmField val START_REF_SILENT_FORMAL = IElementType("START_REF_SILENT_FORMAL", Language.ANY)
    @JvmField val END_REF_FORMAL = IElementType("END_REF_FORMAL", Language.ANY)
    @JvmField val DIRECTIVE = IElementType("DIRECTIVE", Language.ANY)
    @JvmField val OPERATOR = IElementType("OPERATOR", Language.ANY)
}
