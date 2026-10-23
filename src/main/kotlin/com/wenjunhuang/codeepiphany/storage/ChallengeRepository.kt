package com.wenjunhuang.codeepiphany.storage

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import com.wenjunhuang.codeepiphany.configuration.CodeEpiphanySettings
import com.wenjunhuang.codeepiphany.model.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Service(Service.Level.PROJECT)
class ChallengeRepository(private val project: Project) : Disposable {
    private val database = SqliteDatabase {
        File(CodeEpiphanySettings.getInstance(project).state.getDatabaseFolder(project), Constants.CHALLENGE_STORAGE_FILE)
    }
    init {
        project.messageBus.connect(this).subscribe(CodeEpiphanySettings.DATABASE_FOLDER_TOPIC,
            CodeEpiphanySettings.DatabaseFolderNotifier { notifyChanged() })
    }
    suspend fun <T> query(block: (SqliteStore) -> T): T = withContext(Dispatchers.IO) { database.read(block) }
    suspend fun <T> transaction(block: (SqliteStore) -> T): T = withContext(Dispatchers.IO) {
        database.transaction(block).also { notifyChanged() }
    }
    /** 仅用于 VFS 的同步读写契约；普通 UI 调用 query/transaction。 */
    fun <T> readForFileSystem(block: (SqliteStore) -> T): T = database.read(block)
    fun <T> writeForFileSystem(block: (SqliteStore) -> T): T = database.transaction(block).also { notifyChanged() }
    private fun notifyChanged() = ApplicationManager.getApplication().invokeLater({
        if (!project.isDisposed) project.messageBus.syncPublisher(CHANGED).changed()
    }, ModalityState.any())
    override fun dispose() = database.close()
    fun interface Listener { fun changed() }
    companion object {
        @JvmField val CHANGED = Topic(Listener::class.java, Topic.BroadcastDirection.NONE)
        @JvmStatic fun getInstance(project: Project): ChallengeRepository = project.getService(ChallengeRepository::class.java)
    }
}
