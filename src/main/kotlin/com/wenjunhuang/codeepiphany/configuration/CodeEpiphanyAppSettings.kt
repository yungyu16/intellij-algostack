package com.wenjunhuang.codeepiphany.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.annotations.Attribute
import com.wenjunhuang.codeepiphany.model.Constants

@Service
@State(name = Constants.APP_SETTING, storages = [Storage(Constants.APP_SETTING_FILE)])
class CodeEpiphanyAppSettings : PersistentStateComponent<CodeEpiphanyAppSettings.AppState> {
    private var settingsState = AppState()
    override fun getState(): AppState = settingsState
    override fun loadState(state: AppState) { settingsState = state }
    class AppState {
        @field:Attribute(converter = ConfigConverters.StringConverter::class)
        var version: String? = null
    }
    companion object {
        @JvmStatic fun getInstance(): CodeEpiphanyAppSettings = ApplicationManager.getApplication().getService(CodeEpiphanyAppSettings::class.java)
    }
}
