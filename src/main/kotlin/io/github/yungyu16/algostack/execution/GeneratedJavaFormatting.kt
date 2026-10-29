package io.github.yungyu16.algostack.execution

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.codeStyle.CodeStyleManager

/** 只格式化内存中的新骨架，不加载或改写已有题解，不优化 import 或执行代码重构。 */
internal fun formatGeneratedJava(fileName: String, code: String): String {
    val application = ApplicationManager.getApplication()
    fun format(): String = WriteAction.compute<String, RuntimeException> {
        val project = ProjectManager.getInstance().defaultProject
        val file = PsiFileFactory.getInstance(project).createFileFromText(fileName, JavaFileType.INSTANCE, code)
        val formatted = CodeStyleManager.getInstance(project).reformat(file)
        // 默认样式可能保留空行缩进；仅清理 PSI 空白节点，不碰字符串、文本块或注释正文。
        StringBuilder(formatted.text).apply {
            PsiTreeUtil.findChildrenOfType(formatted, PsiWhiteSpace::class.java)
                .sortedByDescending { it.textRange.startOffset }.forEach { whitespace ->
                    val clean = whitespace.text.replace(Regex("[ \\t]+(?=\\r?\\n)"), "")
                    if (clean != whitespace.text) replace(whitespace.textRange.startOffset, whitespace.textRange.endOffset, clean)
                }
        }.toString()
    }
    if (application.isDispatchThread) return format()
    // 同步生成接口不能持有读锁等待 EDT 写锁；真实生成调用不在 read action 内。
    check(!application.isReadAccessAllowed) { "Generated Java formatting must run outside a background read action" }
    var result: String? = null
    application.invokeAndWait { result = format() }
    return requireNotNull(result)
}
