package com.wenjunhuang.codeepiphany.execution

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.wenjunhuang.codeepiphany.configuration.ChallengeSettings
import com.wenjunhuang.codeepiphany.configuration.PlatformSettings
import com.wenjunhuang.codeepiphany.domain.*
import com.wenjunhuang.codeepiphany.runtime.AuthService
import com.wenjunhuang.codeepiphany.runtime.HttpService
import com.wenjunhuang.codeepiphany.presentation.auth.LoginDialog
import com.wenjunhuang.codeepiphany.presentation.sidebar.SidebarPolicy
import com.wenjunhuang.codeepiphany.runtime.onEdt
import com.wenjunhuang.codeepiphany.utils.FileUtils
import com.wenjunhuang.codeepiphany.leetcode.api.model.LeetCodeChallengeData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path

suspend fun ensureLoggedIn(project: Project, dojo: CodeDojo): Boolean =
    SidebarPolicy.enabled(project.basePath) && AuthService.getInstance(project).ensureAuthenticated(dojo) { LoginDialog.login(project, dojo) }

internal data class GenerationSettings(val folder: String, val fileName: String, val code: String)
internal data class PreparedChallenge(val file: File, val code: String, val template: ChallengeTemplate, val identity: ChallengeIdentity)

private suspend fun generationSettings(project: Project, dojo: CodeDojo, language: Language, version: LanguageVersion): GenerationSettings? = onEdt {
    val setting = PlatformSettings.getInstance(project, dojo).getLanguageSetting(language, version)
    if (setting?.sourceFolder.isNullOrBlank() || setting.fileNameTemplate.isNullOrBlank() || setting.codeTemplate.isNullOrBlank()) null
    else GenerationSettings(PathMacroManager.getInstance(project).expandPath(setting.sourceFolder) ?: setting.sourceFolder!!,
        setting.fileNameTemplate!!, setting.codeTemplate!!)
}

private suspend fun prepareChallenge(dojo: CodeDojo, slug: String, language: Language,
    version: LanguageVersion, setting: GenerationSettings): PreparedChallenge {
    val data = HttpService.getInstance().api(dojo).getQuestionData(slug)
    require(data.titleSlug == slug) { "Question identity mismatch" }
    return renderChallenge(data, dojo, language, version, setting)
}

internal fun renderChallenge(data: LeetCodeChallengeData, dojo: CodeDojo, language: Language,
    version: LanguageVersion, setting: GenerationSettings): PreparedChallenge {
    val template = challengeTemplate(data, dojo, language, version)
    val parts = setting.fileName.split(Regex("[\\\\/]"))
        .filter { it.isNotEmpty() }.map { FileUtils.sanitizeFilename(renderTemplate(it, language, template).trim()) }
    require(parts.isNotEmpty() && parts.none { it.isBlank() }) { "File name template generated an empty path" }
    val identity = ChallengeIdentity(dojo, data.titleSlug, language, version)
    val code = ChallengeRecognition.header(identity) + renderTemplate(setting.code, language, template)
    return PreparedChallenge(File(setting.folder, parts.joinToString(File.separator) + ".${language.fileExt}"), code, template, identity)
}

private suspend fun associateChallenge(project: Project, prepared: PreparedChallenge, file: VirtualFile) {
    currentCoroutineContext().ensureActive()
    check(!project.isDisposed) { "Project closed" }
    ChallengeSettings.getInstance(project).addChallenge(file, ChallengeSettings.Association(
        prepared.identity, prepared.template.cases, prepared.template.cases, prepared.template.name))
}

/** 原手动入口保留交互登录、配置引导和重命名对话框。 */
suspend fun openChallenge(project: Project, dojo: CodeDojo, slug: String, language: Language, version: LanguageVersion) {
    check(SidebarPolicy.enabled(project.basePath)) { "Create a root .leetcode marker and reopen the project first" }
    val setting = generationSettings(project, dojo, language, version)
    if (setting == null) {
        onEdt {
            if (MessageDialogBuilder.yesNo("Error", "Please configure source folder and language settings").ask(project))
                ShowSettingsUtil.getInstance().showSettingsDialog(project, if (dojo == CodeDojo.LeetCode) "CodeEpiphany.Settings.LeetCode" else "CodeEpiphany.Settings.LeetCodeCN")
        }
        return
    }
    if (!ensureLoggedIn(project, dojo)) return
    val prepared = prepareChallenge(dojo, slug, language, version, setting)
    val root = withContext(Dispatchers.IO) { Path.of(requireNotNull(project.basePath)).toRealPath() }
    if (language == Language.Java || language == Language.GO) {
        val folder = Path.of(setting.folder).toAbsolutePath().normalize()
        require(prepared.file.toPath().toAbsolutePath().normalize().startsWith(folder)) { "Generated file must stay inside the configured source folder" }
        withContext(Dispatchers.IO) { safeInitializationPath(root, prepared.file.toPath()) }
    }
    ensureLanguageProject(root, dojo, language, setting)
    val file = saveNewFile(project, prepared.file, prepared.code,
        root.takeIf { language == Language.Java || language == Language.GO })
    associateChallenge(project, prepared, file)
    openEditor(project, file)
}

/** 自动化入口：只准备题目，复用配置与生成/关联逻辑，不弹窗、不登录、不覆盖代码。 */
suspend fun initializeChallenge(project: Project, dojo: CodeDojo, slug: String, language: Language): InitializationResult {
    require(language == Language.Java || language == Language.GO) { "Only Java and Go are supported" }
    require(slug.length <= 160 && Regex("[a-z0-9]+(?:-[a-z0-9]+)*").matches(slug)) { "Invalid slug" }
    check(!project.isDisposed && project.isOpen) { "Project is not open" }
    if (!SidebarPolicy.enabled(project.basePath)) return InitializationResult("conflict", reason = "root_leetcode_marker_required")
    val setting = generationSettings(project, dojo, language, LanguageVersion.AnyVersion)
        ?: return InitializationResult("conflict", reason = "configure_language_template_first")
    val root = withContext(Dispatchers.IO) { Path.of(requireNotNull(project.basePath)).toRealPath() }
    val prepared = prepareChallenge(dojo, slug, language, LanguageVersion.AnyVersion, setting)
    val target = prepared.file.toPath().toAbsolutePath().normalize()
    val folder = Path.of(setting.folder).toAbsolutePath().normalize()
    require(folder.startsWith(root) && target.startsWith(folder)) { "Automation destination must stay inside the open project and configured folder" }
    var virtualFile: VirtualFile? = null
    val settings = ChallengeSettings.getInstance(project)
    return initializeChallengeFile(root, target, prepared.code,
        associationMatches = {
            withContext(Dispatchers.IO) {
                settings.resolve(target.toString(), java.nio.file.Files.readString(target))?.identity == prepared.identity
            }
        },
        hasMapping = { settings.hasMapping(target.toString()) },
        persistAndAssociate = {
            currentCoroutineContext().ensureActive()
            val file = withContext(Dispatchers.IO) { LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target.toFile()) }
                ?: error("Cannot refresh generated file")
            virtualFile = file
            associateChallenge(project, prepared, file)
        },
        open = { openEditor(project, requireNotNull(virtualFile)) },
        initializeProject = { ensureLanguageProject(root, dojo, language, setting) },
        reportFailure = { Logger.getInstance("CodeEpiphany.Initialization").warn("Challenge initialization incomplete for ${dojo.value}/${language.value}", it) })
}
