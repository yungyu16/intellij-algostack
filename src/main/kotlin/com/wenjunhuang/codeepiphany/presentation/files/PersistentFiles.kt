package com.wenjunhuang.codeepiphany.presentation.files

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerBase
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.*
import com.intellij.testFramework.LightVirtualFileBase
import com.intellij.util.LocalTimeCounter
import com.intellij.vcs.editor.ComplexPathVirtualFileSystem
import com.wenjunhuang.codeepiphany.configuration.CodeEpiphanySettings
import com.wenjunhuang.codeepiphany.domain.CodeDojo
import com.wenjunhuang.codeepiphany.runtime.ProjectTasks
import com.wenjunhuang.codeepiphany.runtime.onEdt
import com.wenjunhuang.codeepiphany.storage.ChallengeRepository
import com.wenjunhuang.codeepiphany.storage.SubmissionRecord
import com.wenjunhuang.codeepiphany.storage.SolutionRecord
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

private fun textFileType(extension: String) = FileTypeManager.getInstance().getFileTypeByExtension(extension)
    .takeUnless { it == FileTypes.UNKNOWN } ?: FileTypes.PLAIN_TEXT

data class NotePath(val solutionId: Long, val challengeId: Long, val dojo: CodeDojo, val ownerHash: String) : ComplexPathVirtualFileSystem.ComplexPath {
    override val projectHash: String get() = ownerHash
    override val sessionId: String get() = solutionId.toString()
}
enum class CodeType { Local, Submission }
data class CodePath(val submissionId: Long, val ownerHash: String, val codeType: CodeType) : ComplexPathVirtualFileSystem.ComplexPath {
    override val projectHash: String get() = ownerHash
    override val sessionId: String get() = submissionId.toString()
}
object NotePathCodec : ComplexPathVirtualFileSystem.ComplexPathSerializer<NotePath> {
    override fun serialize(path: NotePath): String = buildJsonObject {
        put("solutionId", path.solutionId); put("challengeId", path.challengeId)
        put("codeDojo", buildJsonObject { put(path.dojo.name, JsonObject(emptyMap())) }); put("projectHash", path.ownerHash)
    }.toString()
    override fun deserialize(path: String): NotePath {
        val value = Json.parseToJsonElement(path).jsonObject
        val site = value.getValue("codeDojo").let { if (it is JsonPrimitive) it.content else it.jsonObject.keys.single() }
        return NotePath(value.getValue("solutionId").jsonPrimitive.long, value.getValue("challengeId").jsonPrimitive.long,
            CodeDojo.fromString(site) ?: throw IllegalArgumentException("Unsupported note site"), value.getValue("projectHash").jsonPrimitive.content)
    }
}
object CodePathCodec : ComplexPathVirtualFileSystem.ComplexPathSerializer<CodePath> {
    override fun serialize(path: CodePath): String = buildJsonObject {
        put("submissionId", path.submissionId); put("projectHash", path.ownerHash)
        put("codeType", buildJsonObject { put(path.codeType.name, JsonObject(emptyMap())) })
    }.toString()
    override fun deserialize(path: String): CodePath {
        val value = Json.parseToJsonElement(path).jsonObject
        val type = value.getValue("codeType").let { if (it is JsonPrimitive) it.content else it.jsonObject.keys.single() }
        return CodePath(value.getValue("submissionId").jsonPrimitive.long, value.getValue("projectHash").jsonPrimitive.content, CodeType.valueOf(type))
    }
}

@Service(Service.Level.PROJECT)
class FileCache(project: Project) : Disposable {
    val notes = ConcurrentHashMap<NotePath, NoteFile>()
    val codes = ConcurrentHashMap<CodePath, CodeFile>()
    init {
        project.messageBus.connect(this).subscribe(CodeEpiphanySettings.DATABASE_FOLDER_TOPIC,
            CodeEpiphanySettings.DatabaseFolderNotifier { notes.clear(); codes.clear() })
    }
    override fun dispose() { notes.clear(); codes.clear() }
}
class NoteFileSystem : ComplexPathVirtualFileSystem<NotePath>(NotePathCodec) {
    override fun getProtocol() = "solution-remark"
    override fun findOrCreateFile(project: Project, path: NotePath): VirtualFile = project.getService(FileCache::class.java).notes.computeIfAbsent(path) {
        // IDE 恢复已打开的虚拟文件时，入口本身是同步契约。
        val data = ChallengeRepository.getInstance(project).readForFileSystem { it.solution(path.solutionId) to it.challenge(path.challengeId)?.title }
        val row = data.first ?: throw IOException("Solution no longer exists")
        NoteFile(project, path, row, this, data.second)
    }
}
class CodeFileSystem : ComplexPathVirtualFileSystem<CodePath>(CodePathCodec) {
    override fun getProtocol() = "submission-code"
    override fun findOrCreateFile(project: Project, path: CodePath): VirtualFile = project.getService(FileCache::class.java).codes.computeIfAbsent(path) {
        val row = ChallengeRepository.getInstance(project).readForFileSystem { it.submission(path.submissionId) }
            ?: throw IOException("Submission no longer exists")
        CodeFile(project, path, row, this)
    }
}

class NoteFile(private val project: Project, val notePath: NotePath, initial: SolutionRecord, private val system: NoteFileSystem, challengeTitle: String?) :
    LightVirtualFileBase(notePath.sessionId, textFileType("md"), LocalTimeCounter.currentTime()), VirtualFilePathWrapper {
    private val databaseFolder = CodeEpiphanySettings.getInstance(project).state.databaseFolder
    @Volatile private var title = challengeTitle?.let { "$it - ${initial.title}" } ?: initial.title
    @Volatile private var content = initial.remark
    init { FileDocumentManagerBase.TRACK_NON_PHYSICAL.set(this, true) }
    override fun getFileSystem(): VirtualFileSystem = system
    override fun getPath(): String = system.getPath(notePath)
    override fun getPresentablePath() = title
    override fun getPresentableName() = title
    override fun enforcePresentableName() = true
    fun updateTitle(challengeTitle: String, solutionTitle: String) {
        val updated = "$challengeTitle - $solutionTitle"
        if (title != updated) {
            title = updated
            com.intellij.openapi.fileEditor.ex.FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(this)
        }
    }
    override fun isValid(): Boolean = !project.isDisposed && databaseFolder == CodeEpiphanySettings.getInstance(project).state.databaseFolder
    override fun contentsToByteArray() = content.toByteArray(Charsets.UTF_8)
    override fun getInputStream() = ByteArrayInputStream(contentsToByteArray())
    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream = object : ByteArrayOutputStream() {
        override fun close() {
            if (!isValid) throw IOException("Database location changed or project closed; save the note elsewhere")
            val text = toString(Charsets.UTF_8)
            // 保存成功返回前必须持久化，不能把同步 VFS 保存改成无人等待的后台写入。
            ChallengeRepository.getInstance(project).writeForFileSystem { it.saveRemark(notePath.solutionId, text) }
            content = text
            super.close()
        }
    }
}
class CodeFile(private val project: Project, val codePath: CodePath, val record: SubmissionRecord, private val system: CodeFileSystem) :
    LightVirtualFileBase(codePath.sessionId, textFileType(record.language.fileExt), LocalTimeCounter.currentTime()), VirtualFilePathWrapper {
    private val content = if (codePath.codeType == CodeType.Local) record.localCode else record.submitCode
    override fun getFileSystem(): VirtualFileSystem = system
    override fun getPath(): String = system.getPath(codePath)
    override fun getPresentablePath() = codePath.sessionId
    override fun enforcePresentableName() = true
    override fun isWritable() = false
    override fun isValid() = !project.isDisposed
    override fun contentsToByteArray() = content.toByteArray(Charsets.UTF_8)
    override fun getInputStream() = ByteArrayInputStream(contentsToByteArray())
    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream = throw IOException("Submission code is read-only")
}
class CodeEditorProvider : FileEditorProvider, DumbAware {
    private val delegate = PsiAwareTextEditorProvider()
    override fun accept(project: Project, file: VirtualFile) = file is CodeFile
    override fun createEditor(project: Project, file: VirtualFile) = delegate.createEditor(project, file)
    override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR
    override fun getEditorTypeId() = "CodeEpiphany.SubmissionCode.${delegate.editorTypeId}"
}

fun openNote(project: Project, row: SolutionRecord, dojo: CodeDojo, challengeTitle: String) {
    val path = NotePath(row.id, row.challengeId, dojo, project.locationHash)
    val system = VirtualFileManager.getInstance().getFileSystem("solution-remark") as NoteFileSystem
    val file = project.getService(FileCache::class.java).notes.computeIfAbsent(path) { NoteFile(project, path, row, system, challengeTitle) }
    FileEditorManager.getInstance(project).openFile(file, true)
}
fun codeFile(project: Project, row: SubmissionRecord, type: CodeType): CodeFile {
    val path = CodePath(row.id, project.locationHash, type)
    val system = VirtualFileManager.getInstance().getFileSystem("submission-code") as CodeFileSystem
    return project.getService(FileCache::class.java).codes.computeIfAbsent(path) { CodeFile(project, path, row, system) }
}
fun openCode(project: Project, row: SubmissionRecord, type: CodeType) {
    FileEditorManager.getInstance(project).openFile(codeFile(project, row, type), true)
}
