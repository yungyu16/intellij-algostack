package io.github.yungyu16.algostack.execution

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import io.github.yungyu16.algostack.domain.*
import io.github.yungyu16.algostack.leetcode.model.LeetCodeChallengeData
import io.github.yungyu16.algostack.presentation.auth.LoginDialog
import io.github.yungyu16.algostack.presentation.sidebar.SidebarPolicy
import io.github.yungyu16.algostack.runtime.AuthService
import io.github.yungyu16.algostack.runtime.HttpService
import io.github.yungyu16.algostack.utils.ChallengeFiles
import io.github.yungyu16.algostack.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path

suspend fun ensureLoggedIn(project: Project, dojo: CodeDojo): Boolean =
    SidebarPolicy.enabled(project.basePath) && AuthService.getInstance(project)
        .ensureAuthenticated(dojo) { LoginDialog.login(project, dojo) }

internal data class GenerationSettings(val folder: String, val fileName: String, val code: String)
internal data class PreparedChallenge(val file: File, val code: String, val identity: ChallengeIdentity)

private fun generationSettings(
    project: Project,
    dojo: CodeDojo,
    language: Language,
    version: LanguageVersion
): GenerationSettings =
    ChallengeConventions.generation(Path.of(requireNotNull(project.basePath)), dojo, language, version)

private suspend fun prepareChallenge(
    dojo: CodeDojo, slug: String, language: Language,
    version: LanguageVersion, setting: GenerationSettings,
    onStage: (String) -> Unit = {}
): PreparedChallenge {
    onStage("question_fetch_decode")
    val data = HttpService.getInstance().api(dojo).getQuestionData(slug)
    onStage("question_identity")
    require(data.titleSlug == slug) { "Question identity mismatch" }
    onStage("template_render_recognition")
    return renderChallenge(data, dojo, language, version, setting)
}

internal fun renderChallenge(
    data: LeetCodeChallengeData, dojo: CodeDojo, language: Language,
    version: LanguageVersion, setting: GenerationSettings
): PreparedChallenge {
    val template = challengeTemplate(data, dojo, language, version)
    val parts = setting.fileName.split(Regex("[\\\\/]"))
        .filter { it.isNotEmpty() }.map { FileUtils.sanitizeFilename(renderTemplate(it, template).trim()) }
    require(parts.isNotEmpty() && parts.none { it.isBlank() }) { "File name template generated an empty path" }
    val identity = ChallengeIdentity(dojo, data.titleSlug, language, version)
    val code = renderTemplate(setting.code, template)
    require(
        ChallengeRecognition.recognize(
            code,
            language.fileExt
        ) == identity
    ) { "Template must provide a matching problem URL and one submission region" }
    return PreparedChallenge(
        File(setting.folder, parts.joinToString(File.separator) + ".${language.fileExt}"),
        code,
        identity
    )
}

/** 手动入口使用固定约定，保留交互登录和重命名对话框。 */
suspend fun openChallenge(
    project: Project,
    dojo: CodeDojo,
    slug: String,
    language: Language,
    version: LanguageVersion
) {
    check(SidebarPolicy.enabled(project.basePath)) { "Create a root .leetcode marker and reopen the project first" }
    val setting = generationSettings(project, dojo, language, version)
    if (!ensureLoggedIn(project, dojo)) return
    val prepared = prepareChallenge(dojo, slug, language, version, setting)
    val root = withContext(Dispatchers.IO) { Path.of(requireNotNull(project.basePath)).toRealPath() }
    if (language == Language.Java || language == Language.GO) {
        val folder = Path.of(setting.folder).toAbsolutePath().normalize()
        require(
            prepared.file.toPath().toAbsolutePath().normalize().startsWith(folder)
        ) { "Generated file must stay inside the conventional source folder" }
        withContext(Dispatchers.IO) { safeInitializationPath(root, prepared.file.toPath()) }
    }
    ensureLanguageProject(root, dojo, language)
    val file = saveNewFile(
        project, prepared.file,
        root.takeIf { language == Language.Java || language == Language.GO },
        isValidName = { isChallengeFileNameValid(it, language) },
        contentForFile = prepared::codeForFile
    )
    openEditor(project, file)
}

/** 自动化入口：只准备题目，共用固定生成约定，不弹窗、不登录、不覆盖代码。 */
suspend fun initializeChallenge(
    project: Project,
    dojo: CodeDojo,
    slug: String,
    language: Language,
    onStage: (String) -> Unit = {}
): InitializationResult {
    onStage("initialization_preconditions")
    require(language == Language.Java || language == Language.GO) { "Only Java and Go are supported" }
    require(slug.length <= 160 && Regex("[a-z0-9]+(?:-[a-z0-9]+)*").matches(slug)) { "Invalid slug" }
    check(!project.isDisposed && project.isOpen) { "Project is not open" }
    if (!SidebarPolicy.enabled(project.basePath)) return InitializationResult(
        "conflict",
        reason = "root_leetcode_marker_required"
    )
    onStage("generation_settings")
    val setting = generationSettings(project, dojo, language, LanguageVersion.AnyVersion)
    onStage("project_real_path")
    val root = withContext(Dispatchers.IO) { Path.of(requireNotNull(project.basePath)).toRealPath() }
    val prepared = prepareChallenge(dojo, slug, language, LanguageVersion.AnyVersion, setting, onStage)
    onStage("destination_validation")
    val target = prepared.file.toPath().toAbsolutePath().normalize()
    val folder = Path.of(setting.folder).toAbsolutePath().normalize()
    require(folder.startsWith(root) && target.startsWith(folder)) { "Automation destination must stay inside the open project and conventional folder" }
    var virtualFile: VirtualFile? = null
    onStage("file_initialization")
    return initializeChallengeFile(
        root, target, prepared.code,
        identityMatches = {
            onStage("source_identity")
            withContext(Dispatchers.IO) {
                ChallengeFiles.resolve(target.toString(), java.nio.file.Files.readString(target)) == prepared.identity
            }
        },
        refresh = {
            onStage("vfs_refresh")
            currentCoroutineContext().ensureActive()
            val file = withContext(Dispatchers.IO) {
                LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target.toFile())
            }
                ?: error("Cannot refresh generated file")
            virtualFile = file
        },
        open = {
            onStage("editor_open")
            openEditor(project, requireNotNull(virtualFile))
        },
        initializeProject = {
            onStage("language_project")
            ensureLanguageProject(root, dojo, language)
        },
        reportFailure = {
            Logger.getInstance("AlgoStack.Initialization")
                .warn("初始化文件流程未完成 dojo=${dojo.value} language=${language.value}\n${initializationFailureDetails(it)}")
        })
}
