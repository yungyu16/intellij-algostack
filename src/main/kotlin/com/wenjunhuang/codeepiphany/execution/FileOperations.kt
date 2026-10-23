package com.wenjunhuang.codeepiphany.execution

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.wenjunhuang.codeepiphany.PluginBundle
import com.wenjunhuang.codeepiphany.runtime.onEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption

suspend fun saveNewFile(project: Project, requested: File, text: String): VirtualFile {
    val target = if (withContext(Dispatchers.IO) { requested.exists() }) onEdt {
        val extension = FileUtilRt.getExtension(requested.name).let { if (it.isEmpty()) "" else ".$it" }
        val name = Messages.showInputDialog(project, PluginBundle.message("file.creation.exists"),
            PluginBundle.message("file.creation.exists.title"), Messages.getQuestionIcon(),
            FileUtilRt.getNameWithoutExtension(requested.name), object : InputValidator {
                override fun checkInput(inputString: String): Boolean = inputString.isNotBlank() && !File(requested.parentFile, inputString + extension).exists()
                override fun canClose(inputString: String): Boolean = checkInput(inputString)
            }) ?: throw kotlinx.coroutines.CancellationException("File creation cancelled")
        File(requested.parentFile, name + extension)
    } else requested
    return withContext(Dispatchers.IO) {
        target.parentFile.mkdirs()
        Files.writeString(target.toPath(), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target) ?: throw IOException("Cannot refresh created file: $target")
    }
}

suspend fun saveAndRead(file: VirtualFile): String {
    onEdt {
        val manager = FileDocumentManager.getInstance()
        manager.getDocument(file)?.let { manager.saveDocument(it) }
        if (manager.isFileModified(file)) throw IOException("Cannot save edited file: ${file.name}")
    }
    return withContext(Dispatchers.IO) { VfsUtilCore.loadText(file) }
}

suspend fun openEditor(project: Project, file: VirtualFile) = onEdt {
    FileEditorManager.getInstance(project).openFile(file, true)
    Unit
}
