package com.wenjunhuang.codeepiphany.model.template

import com.wenjunhuang.codeepiphany.model.template.lexer.ChallengeFileTemplateTextLexer
import org.junit.Assert.*
import org.junit.Test

class ChallengeFileTemplateTextLexerTest {
    @Test fun nestedMethodReferencePreservesEveryCharacter() {
        val input = "public \${velocity.camelCaseName(\$challenge.slug)} {}"
        val lexer = ChallengeFileTemplateTextLexer()
        lexer.start(input)
        val tokens = buildList { while (lexer.tokenType != null) { add(lexer.tokenType.toString() to lexer.tokenText); lexer.advance() } }
        assertEquals(input, tokens.joinToString("") { it.second.orEmpty() })
        assertEquals(listOf("TEXT", "START_REF_FORMAL", "IDENTIFIER", "START_PAREN", "START_REF", "IDENTIFIER", "END_PAREN", "END_REF_FORMAL", "TEXT"), tokens.map { it.first })
    }
    @Test fun resetClearsAnUnfinishedReference() {
        val lexer = ChallengeFileTemplateTextLexer()
        lexer.start("\${unfinished"); lexer.advance(); lexer.start("plain text")
        assertEquals("TEXT", lexer.tokenType.toString()); assertEquals("plain text", lexer.tokenText)
        lexer.advance(); assertNull(lexer.tokenType)
    }
}
