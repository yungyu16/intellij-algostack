package com.wenjunhuang.codeepiphany.model.template.lexer

import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.MergingLexerAdapter
import com.intellij.psi.tree.TokenSet

class ChallengeFileTemplateTextLexer : MergingLexerAdapter(
    FlexAdapter(object : _ChallengeFileTemplateTextLexer() {
        override fun reset(buffer: CharSequence, start: Int, end: Int, initialState: Int) {
            super.reset(buffer, start, end, initialState)
            onReset()
        }
    }),
    TokenSet.create(ChallengeFileTemplateTokenType.TEXT)
)
