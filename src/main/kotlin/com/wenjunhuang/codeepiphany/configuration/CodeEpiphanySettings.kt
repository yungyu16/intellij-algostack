package com.wenjunhuang.codeepiphany.configuration

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.annotations.Attribute
import com.wenjunhuang.codeepiphany.domain.CodeDojo
import com.wenjunhuang.codeepiphany.model.Constants
import java.io.File

@Service(Service.Level.PROJECT)
@State(name = Constants.SETTING, storages = [Storage(Constants.SETTING_FILE)])
class CodeEpiphanySettings : PersistentStateComponent<CodeEpiphanySettings.CodeEpiphanySettingsState> {
    private var settingsState = CodeEpiphanySettingsState()
    override fun getState(): CodeEpiphanySettingsState = settingsState
    override fun loadState(state: CodeEpiphanySettingsState) { settingsState = state }

    class CodeEpiphanySettingsState {
        @field:Attribute
        var databaseFolder: String = "\$PROJECT_DIR\$/.idea/${Constants.PROJECT_NAME}"
        @field:Attribute(converter = ConfigConverters.CodeDojoConverter::class)
        var latestCodeDojo: CodeDojo? = null
        fun getDatabaseFolder(project: Project): String = PathMacroManager.getInstance(project).expandPath(databaseFolder) ?: databaseFolder
        fun setDatabaseFolder(folder: String, project: Project) {
            databaseFolder = PathMacroManager.getInstance(project).collapsePath(folder) ?: folder
        }
    }
    fun interface CodeEpiphanySettingsChangedListener { fun changed() }
    fun interface DatabaseFolderNotifier { fun databaseFolderChanged(folder: File) }
    companion object {
        @JvmField val TOPIC = Topic(CodeEpiphanySettingsChangedListener::class.java, Topic.BroadcastDirection.TO_CHILDREN)
        @JvmField @Topic.ProjectLevel val DATABASE_FOLDER_TOPIC = Topic(DatabaseFolderNotifier::class.java)
        @JvmStatic fun getInstance(project: Project): CodeEpiphanySettings = project.getService(CodeEpiphanySettings::class.java)
    }
}
