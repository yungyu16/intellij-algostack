package com.wenjunhuang.codeepiphany.configuration

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.XCollection
import com.wenjunhuang.codeepiphany.domain.*
import com.wenjunhuang.codeepiphany.model.Constants

/** XML beans keep the existing field/tag names; callers receive independent snapshots. */
@Service(Service.Level.PROJECT)
@State(name = Constants.CHALLENGE_SETTING, storages = [Storage(Constants.CHALLENGE_SETTING_FILE)])
class ChallengeSettings : PersistentStateComponent<ChallengeSettings.ChallengeSettingsState> {
    private var settingsState = ChallengeSettingsState()
    @Synchronized override fun getState(): ChallengeSettingsState = settingsState
    @Synchronized override fun loadState(state: ChallengeSettingsState) { settingsState = state }
    private fun path(file: VirtualFile): String = file.canonicalPath ?: file.path

    @Synchronized fun addChallenge(path: String, item: Association) {
        settingsState.challenges[path] = ChallengeSettingsStateItem().also {
            it.challengeId = item.challengeId; it.challengeLanguageId = item.challengeLanguageId
            it.dojo = item.dojo; it.language = item.language
            it.testCases = item.testCases.map { case -> case.copy() }.toMutableList()
            it.defaultTestCases = item.defaultTestCases.map { case -> case.copy() }.toMutableList()
        }
    }
    fun addChallenge(file: VirtualFile, item: Association) = addChallenge(path(file), item)
    @Synchronized fun removeChallenge(path: String) { settingsState.challenges.remove(path) }
    fun removeChallenge(file: VirtualFile) = removeChallenge(path(file))
    @Synchronized fun findChallengeId(path: String): Association? {
        val item = settingsState.challenges[path] ?: return null
        return Association(item.challengeId, item.challengeLanguageId, item.dojo ?: return null,
            item.language ?: return null,
            item.testCases.map { it.copy() }, item.defaultTestCases.map { it.copy() })
    }
    fun findChallengeId(file: VirtualFile): Association? = findChallengeId(path(file))
    @Synchronized fun updateChallengeTestCases(path: String, cases: List<TestCase>) {
        settingsState.challenges[path]?.testCases = cases.map { it.copy() }.toMutableList()
    }
    fun updateChallengeTestCases(file: VirtualFile, cases: List<TestCase>) = updateChallengeTestCases(path(file), cases)
    @Synchronized fun modifyCodeFilePath(old: String, new: String) {
        settingsState.challenges.remove(old)?.let { settingsState.challenges[new] = it }
    }
    @Synchronized fun relocatePaths(old: String, new: String?, directory: Boolean) {
        val paths = settingsState.challenges.keys.filter { it == old || directory && it.startsWith("$old/") }
        for (path in paths) {
            val item = settingsState.challenges.remove(path) ?: continue
            if (new != null) settingsState.challenges[new + path.removePrefix(old)] = item
        }
    }

    data class Association(
        val challengeId: ChallengeId, val challengeLanguageId: ChallengeLanguageId,
        val dojo: CodeDojo, val language: Language,
        val testCases: List<TestCase>, val defaultTestCases: List<TestCase>
    )
    class ChallengeSettingsState { var challenges: MutableMap<String, ChallengeSettingsStateItem> = linkedMapOf() }
    class ChallengeSettingsStateItem {
        var challengeId: Long = 0
        var challengeLanguageId: Long = 0
        @get:OptionTag(converter = ConfigConverters.CodeDojoConverter::class)
        var dojo: CodeDojo? = null
        @get:OptionTag(converter = ConfigConverters.LanguageConverter::class)
        var language: Language? = null
        @get:XCollection(elementTypes = [TestCase::class])
        var testCases: MutableList<TestCase> = arrayListOf()
        @get:XCollection(elementTypes = [TestCase::class])
        var defaultTestCases: MutableList<TestCase> = arrayListOf()
    }
    data class TestCase(var input: String = "", var expectedOutput: String = "")
    companion object {
        @JvmStatic fun getInstance(project: Project): ChallengeSettings = project.getService(ChallengeSettings::class.java)
    }
}
