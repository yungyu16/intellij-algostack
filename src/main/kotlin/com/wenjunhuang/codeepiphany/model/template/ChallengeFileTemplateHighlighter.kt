package com.wenjunhuang.codeepiphany.model.template

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Colors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.util.LayerDescriptor
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileTypes.*
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.LightVirtualFile
import com.wenjunhuang.codeepiphany.domain.Language
import com.wenjunhuang.codeepiphany.model.template.lexer.ChallengeFileTemplateTextLexer
import com.wenjunhuang.codeepiphany.model.template.lexer.ChallengeFileTemplateTokenType as Token

class ChallengeFileTemplateHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = ChallengeFileTemplateTextLexer()
    override fun getTokenHighlights(type: IElementType): Array<TextAttributesKey> = when (type) {
        Token.START_REF, Token.START_REF_FORMAL, Token.START_REF_SILENT, Token.START_REF_SILENT_FORMAL, Token.END_REF_FORMAL, Token.DIRECTIVE -> pack(Colors.KEYWORD)
        Token.OPERATOR -> pack(Colors.OPERATION_SIGN)
        Token.START_PAREN, Token.END_PAREN -> pack(Colors.PARENTHESES)
        Token.IDENTIFIER -> pack(Colors.STATIC_FIELD)
        else -> emptyArray()
    }
    companion object {
        @JvmStatic fun createLanguageEditorHighlighter(project: Project?, language: Language?): EditorHighlighter =
            createLanguageEditorHighlighterByExtension(project, language?.fileExt)
        @JvmStatic fun createLanguageEditorHighlighterByExtension(project: Project?, extension: String?): EditorHighlighter =
            LexerEditorHighlighter(syntax(project, extension), EditorColorsManager.getInstance().globalScheme)
        @JvmStatic fun createVelocityTemplateLanguageEditorHighlighter(project: Project?, language: Language?): EditorHighlighter = velocity(project, language?.fileExt)
        @JvmStatic fun createVelocityTemplatePlainTextHighlighter(project: Project?): EditorHighlighter = velocity(project, null)
        private fun syntax(project: Project?, extension: String?): SyntaxHighlighter {
            val type = extension?.let { FileTypeManager.getInstance().getFileTypeByExtension(it) }
                ?.takeUnless { it == FileTypes.UNKNOWN } ?: FileTypes.PLAIN_TEXT
            return SyntaxHighlighterFactory.getSyntaxHighlighter(type, project, null) ?: PlainSyntaxHighlighter()
        }
        private fun velocity(project: Project?, extension: String?): EditorHighlighter {
            if (FileTypeManager.getInstance().getFileTypeByExtension("ft") != FileTypes.UNKNOWN)
                return EditorHighlighterFactory.getInstance().createEditorHighlighter(project, LightVirtualFile("template.${extension ?: "txt"}.ft"))
            return LayeredLexerEditorHighlighter(ChallengeFileTemplateHighlighter(), EditorColorsManager.getInstance().globalScheme).apply {
                registerLayer(Token.TEXT, LayerDescriptor(syntax(project, extension), ""))
            }
        }
    }
}
