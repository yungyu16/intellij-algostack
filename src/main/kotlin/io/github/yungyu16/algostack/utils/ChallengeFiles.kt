package io.github.yungyu16.algostack.utils

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import io.github.yungyu16.algostack.domain.ChallengeIdentity
import io.github.yungyu16.algostack.domain.ChallengeRecognition
import java.io.IOException

/** 只按当前源码识别题目，不读取、创建或更新项目 XML。调用方负责 read action。 */
object ChallengeFiles {
    fun resolve(path: String, text: String): ChallengeIdentity? =
        ChallengeRecognition.recognize(text, path.substringAfterLast('.'))

    fun resolve(file: VirtualFile): ChallengeIdentity? {
        if (file.isDirectory || !file.isValid || file.fileType.isBinary) return null
        val text = FileDocumentManager.getInstance().getCachedDocument(file)?.immutableCharSequence?.toString()
            ?: try {
                VfsUtilCore.loadText(file)
            } catch (_: IOException) {
                return null
            }
        return resolve(file.path, text)
    }
}
