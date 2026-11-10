package io.github.yungyu16.algostack.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.annotations.Attribute
import io.github.yungyu16.algostack.model.Constants

@Service
@State(name = Constants.APP_SETTING, storages = [Storage(Constants.APP_SETTING_FILE)])
class AlgoStackAppSettings : PersistentStateComponent<AlgoStackAppSettings.AppState> {
    private var settingsState = AppState()
    override fun getState(): AppState = settingsState
    override fun loadState(state: AppState) {
        settingsState = state
    }

    class AppState {
        @field:Attribute
        var version: String? = null
    }

    companion object {
        @JvmStatic
        fun getInstance(): AlgoStackAppSettings =
            ApplicationManager.getApplication().getService(AlgoStackAppSettings::class.java)
    }
}
