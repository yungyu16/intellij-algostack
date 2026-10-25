package io.github.yungyu16.algostack.execution

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil

/** 只处理平台新骨架中的空方法；用 Java PSI 区分方法、构造器、注释及字符串，不改已有源码文件。 */
internal fun javaTemplateStub(code: String): String = ReadAction.compute<String, RuntimeException> {
    val project = ProjectManager.getInstance().defaultProject
    val file = PsiFileFactory.getInstance(project).createFileFromText("Solution.java", JavaFileType.INSTANCE, code)
    val replacements = PsiTreeUtil.findChildrenOfType(file, PsiMethod::class.java).mapNotNull { method ->
        // 构造器允许为空；抽象方法没有方法体，已有实现也不覆盖。
        if (method.isConstructor) return@mapNotNull null
        val body = method.body ?: return@mapNotNull null
        if (body.statements.isNotEmpty()) return@mapNotNull null
        val start = body.lBrace?.textRange?.endOffset ?: return@mapNotNull null
        val end = body.rBrace?.textRange?.startOffset ?: return@mapNotNull null
        val lineStart = code.lastIndexOf('\n', method.textRange.startOffset - 1) + 1
        val indent = code.substring(lineStart, method.textRange.startOffset).takeWhile { it == ' ' || it == '\t' }
        val newline = if (code.contains("\r\n")) "\r\n" else "\n"
        val comments = code.substring(start, end).trimEnd()
        Triple(
            start,
            end,
            "$comments$newline$indent    throw new UnsupportedOperationException(\"Not implemented\");$newline$indent"
        )
    }
    StringBuilder(code).apply {
        replacements.sortedByDescending { it.first }
            .forEach { (start, end, replacement) -> replace(start, end, replacement) }
    }.toString()
}
