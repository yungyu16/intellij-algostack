package com.wenjunhuang.codeepiphany.configuration

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.annotations.Attribute
import com.wenjunhuang.codeepiphany.domain.CodeDojo
import com.wenjunhuang.codeepiphany.model.Constants

@Service(Service.Level.PROJECT)
@State(name = Constants.SETTING, storages = [Storage(Constants.SETTING_FILE)])
class CodeEpiphanySettings : PersistentStateComponent<CodeEpiphanySettings.CodeEpiphanySettingsState> {
    private var settingsState = CodeEpiphanySettingsState()
    override fun getState(): CodeEpiphanySettingsState = settingsState
    override fun loadState(state: CodeEpiphanySettingsState) { settingsState = state }
    class CodeEpiphanySettingsState {
        @field:Attribute(converter = ConfigConverters.CodeDojoConverter::class)
        var latestCodeDojo: CodeDojo? = null
    }
    fun interface CodeEpiphanySettingsChangedListener { fun changed() }
    companion object {
        @JvmField val TOPIC = Topic(CodeEpiphanySettingsChangedListener::class.java, Topic.BroadcastDirection.TO_CHILDREN)
        @JvmStatic fun getInstance(project: Project): CodeEpiphanySettings = project.getService(CodeEpiphanySettings::class.java)
    }
}
