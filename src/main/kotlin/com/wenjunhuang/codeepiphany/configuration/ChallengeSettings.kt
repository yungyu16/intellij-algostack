package com.wenjunhuang.codeepiphany.configuration

import com.intellij.openapi.components.*
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.XCollection
import com.wenjunhuang.codeepiphany.domain.*
import com.wenjunhuang.codeepiphany.model.Constants
import java.io.IOException

/** 保留旧 XML 中的用例读取；不读取数据库、不执行迁移。 */
@Service(Service.Level.PROJECT)
@State(name = Constants.CHALLENGE_SETTING, storages = [Storage(Constants.CHALLENGE_SETTING_FILE)])
class ChallengeSettings : PersistentStateComponent<ChallengeSettings.ChallengeSettingsState> {
    private var settingsState = ChallengeSettingsState()
    @Synchronized override fun getState(): ChallengeSettingsState = settingsState
    @Synchronized override fun loadState(state: ChallengeSettingsState) { settingsState = state }
    private fun path(file: VirtualFile): String = file.canonicalPath ?: file.path

    @Synchronized fun addChallenge(path: String, item: Association) {
        settingsState.challenges[path] = ChallengeSettingsStateItem().also {
            it.dojo = item.dojo; it.language = item.language; it.languageVersion = item.identity.version
            it.slug = item.slug; it.title = item.title
            it.testCases = item.testCases.map { case -> case.copy() }.toMutableList()
            it.defaultTestCases = item.defaultTestCases.map { case -> case.copy() }.toMutableList()
        }
    }
    fun addChallenge(file: VirtualFile, item: Association) = addChallenge(path(file), item)
    @Synchronized fun hasMapping(path: String): Boolean = path in settingsState.challenges
    /** 仅供关联管理读取已保存快照；不证明文件身份，业务入口必须使用 resolve。 */
    @Synchronized fun findAssociation(path: String): Association? {
        val item = settingsState.challenges[path] ?: return null
        if (item.slug.isBlank()) return null
        return snapshot(item, ChallengeIdentity(item.dojo ?: return null, item.slug,
            item.language ?: return null, item.languageVersion))
    }
    @Synchronized fun resolve(path: String, text: String): Association? {
        val stored = settingsState.challenges[path]
        val identity = ChallengeRecognition.recognize(text, path.substringAfterLast('.')) ?: return null
        // 身份必须由源文件证明；无 slug 的旧关联只放宽 slug 匹配，不放宽站点和语言/版本。
        val compatible = stored?.takeIf { it.dojo == identity.dojo && it.language == identity.language && it.languageVersion == identity.version &&
            (it.slug.isBlank() || it.slug == identity.slug) }
        return if (compatible != null) snapshot(compatible, identity) else Association(identity)
    }
    fun resolve(file: VirtualFile): Association? {
        if (file.isDirectory || !file.isValid || file.fileType.isBinary) return null
        val text = FileDocumentManager.getInstance().getCachedDocument(file)?.immutableCharSequence?.toString()
            ?: try { VfsUtilCore.loadText(file) } catch (_: IOException) { return null }
        return resolve(path(file), text)
    }
    private fun snapshot(item: ChallengeSettingsStateItem, identity: ChallengeIdentity) = Association(identity,
        item.testCases.map { it.copy() }, item.defaultTestCases.map { it.copy() }, item.title)

    fun updateChallengeTestCases(file: VirtualFile, cases: List<TestCase>) {
        val association = resolve(file) ?: return
        addChallenge(file, association.copy(testCases = cases))
    }
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

    data class Association(val identity: ChallengeIdentity, val testCases: List<TestCase> = emptyList(),
        val defaultTestCases: List<TestCase> = emptyList(), val title: String = "") {
        val dojo get() = identity.dojo
        val slug get() = identity.slug
        val language get() = identity.language
    }
    class ChallengeSettingsState { var challenges: MutableMap<String, ChallengeSettingsStateItem> = linkedMapOf() }
    class ChallengeSettingsStateItem {
        var slug: String = ""
        var title: String = ""
        @get:OptionTag(converter = ConfigConverters.CodeDojoConverter::class)
        var dojo: CodeDojo? = null
        @get:OptionTag(converter = ConfigConverters.LanguageConverter::class)
        var language: Language? = null
        @get:OptionTag(converter = ConfigConverters.LanguageVersionConverter::class)
        var languageVersion: LanguageVersion = LanguageVersion.AnyVersion
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
