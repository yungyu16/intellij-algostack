package io.github.yungyu16.algostack.execution

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNameHelper
import io.github.yungyu16.algostack.domain.Language
import java.io.File

internal fun isChallengeFileNameValid(name: String, language: Language): Boolean {
    if (language != Language.Java) return name.isNotBlank()
    // 这些受限标识符不是普通关键字，但不能用作 Java 类名。
    if (name in setOf("var", "yield", "record", "sealed", "permits", "_")) return false
    return ReadAction.compute<Boolean, RuntimeException> {
        PsiNameHelper.getInstance(ProjectManager.getInstance().defaultProject).isIdentifier(name)
    }
}

/** 仅处理内存中的新题骨架；只替换外层声明的标识符，不接触平台提交区域或磁盘上的已有源码。 */
internal fun PreparedChallenge.codeForFile(target: File): String {
    if (identity.language != Language.Java) return code
    require(target.extension == "java" && isChallengeFileNameValid(target.nameWithoutExtension, Language.Java)) {
        "Java file name must be a valid class name"
    }
    if (target.name == file.name) return code
    return ReadAction.compute<String, RuntimeException> {
        val project = ProjectManager.getInstance().defaultProject
        val source = PsiFileFactory.getInstance(project)
            .createFileFromText(file.name, JavaFileType.INSTANCE, code) as PsiJavaFile
        val wrapper =
            source.classes.single { it.hasModifierProperty(PsiModifier.PUBLIC) && it.name == file.nameWithoutExtension }
        val range = requireNotNull(wrapper.nameIdentifier).textRange
        val renamed = code.replaceRange(range.startOffset, range.endOffset, target.nameWithoutExtension)
        require(Language.Java.extractCodeFromRegion(renamed) == Language.Java.extractCodeFromRegion(code)) {
            "Renaming the generated wrapper must not change the submission region"
        }
        renamed
    }
}
