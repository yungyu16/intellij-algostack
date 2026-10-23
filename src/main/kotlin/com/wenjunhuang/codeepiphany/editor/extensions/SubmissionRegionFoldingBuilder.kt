package com.wenjunhuang.codeepiphany.editor.extensions

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.wenjunhuang.codeepiphany.model.Constants

class SubmissionRegionFoldingBuilder : FoldingBuilderEx(), DumbAware {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val group = FoldingGroup.newGroup("CodeEpiphany Submission Region")
        return PsiTreeUtil.findChildrenOfType(root, PsiComment::class.java).filter {
            it.text.contains(Constants.SUBMIT_CODE_REGION_BEGIN) || it.text.contains(Constants.SUBMIT_CODE_REGION_END)
        }.map { FoldingDescriptor(it.node, it.textRange, group) }.toTypedArray()
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = true
    override fun getPlaceholderText(node: ASTNode): String = when (val psi = node.psi) {
        is PsiComment -> if (psi.text.contains(Constants.SUBMIT_CODE_REGION_BEGIN)) "🚀 Begin 🚀" else "🔚 End 🔚"
        else -> ""
    }
}
