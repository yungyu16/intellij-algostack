package io.github.yungyu16.algostack.presentation.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.yungyu16.algostack.domain.ChallengeIdentity
import io.github.yungyu16.algostack.presentation.sidebar.SidebarPolicy
import io.github.yungyu16.algostack.runtime.HttpService
import io.github.yungyu16.algostack.utils.ChallengeFiles
import kotlinx.coroutines.*

/** 只保有打开文件的本次身份；失败和空标题也保留，避免 presentation 回调形成请求循环。 */
internal class OpenFileTitles {
    internal class Request(val identity: ChallengeIdentity) {
        var title: String? = null
        var job: Job? = null
    }

    private val entries = mutableMapOf<VirtualFile, Request>()
    private var disposed = false

    @Synchronized
    fun title(file: VirtualFile, identity: ChallengeIdentity?, start: (Request) -> Job): String? {
        observe(file, identity, start)
        return entries[file]?.title
    }

    /** 同步观察身份并取消旧请求；仅身份改变时需要刷新 presentation。 */
    @Synchronized
    fun observe(file: VirtualFile, identity: ChallengeIdentity?, start: (Request) -> Job): Boolean {
        if (disposed || entries[file]?.identity == identity) return false
        close(file)
        if (identity != null) {
            val request = Request(identity)
            entries[file] = request
            request.job = start(request)
        }
        return true
    }

    @Synchronized
    fun complete(file: VirtualFile, request: Request, current: ChallengeIdentity?, title: String?): Boolean {
        if (disposed || entries[file] !== request) return false
        if (current != request.identity) {
            close(file); return false
        }
        val changed = request.title != title
        request.title = title
        return changed
    }

    @Synchronized
    fun close(file: VirtualFile) {
        entries.remove(file)?.job?.cancel()
    }

    @Synchronized
    fun dispose() {
        disposed = true
        entries.values.forEach { it.job?.cancel() }
        entries.clear()
    }
}

internal fun challengeTabTitle(translatedTitle: String?, title: String): String? =
    translatedTitle?.trim()?.takeIf { it.isNotEmpty() } ?: title.trim().takeIf { it.isNotEmpty() }

@Service(Service.Level.PROJECT)
class ChallengeEditorTitles(private val project: Project, private val scope: CoroutineScope) : Disposable {
    private val titles = OpenFileTitles()
    private val logger = Logger.getInstance(ChallengeEditorTitles::class.java)

    init {
        project.messageBus.connect(this)
            .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
                override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                    // 分屏仍有同一文件时保留标题。
                    if (!source.isFileOpen(file)) titles.close(file)
                }

                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    refresh(file)
                }
            })
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                FileDocumentManager.getInstance().getFile(event.document)?.let(::refresh)
            }
        }, this)
    }

    private fun identity(file: VirtualFile): ChallengeIdentity? =
        if (project.isDisposed || !file.isValid || !SidebarPolicy.enabled(project.basePath) ||
            !FileEditorManager.getInstance(project).isFileOpen(file)
        ) null else ChallengeFiles.resolve(file)

    fun title(file: VirtualFile): String? = ReadAction.compute<String?, RuntimeException> {
        titles.title(file, identity(file)) { request -> loadTitle(file, request) }
    }

    private fun loadTitle(file: VirtualFile, request: OpenFileTitles.Request): Job = scope.launch(Dispatchers.IO) {
        val title = try {
            val data = HttpService.getInstance().api(request.identity.dojo).getQuestionData(request.identity.slug)
            if (data.titleSlug == request.identity.slug) challengeTabTitle(data.translatedTitle, data.title) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to load editor title: slug=${request.identity.slug}", e)
            null
        }
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) ReadAction.run<RuntimeException> {
                if (titles.complete(file, request, identity(file), title)) {
                    FileEditorManager.getInstance(project).updateFilePresentation(file)
                }
            }
        }
    }

    private fun refresh(file: VirtualFile) {
        val changed = ReadAction.compute<Boolean, RuntimeException> {
            // 文档监听器属于全局 multicaster，其他项目或未打开文件不能触发请求和排队。
            if (project.isDisposed || !FileEditorManager.getInstance(project).isFileOpen(file)) return@compute false
            // 在文档变更通知内观察身份，不能把 A → B → A 合并成一次延后读取。
            titles.observe(file, identity(file)) { request -> loadTitle(file, request) }
        }
        if (!changed) return
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed && FileEditorManager.getInstance(project).isFileOpen(file)) {
                FileEditorManager.getInstance(project).updateFilePresentation(file)
            }
        }
    }

    override fun dispose() {
        titles.dispose()
    }
}
