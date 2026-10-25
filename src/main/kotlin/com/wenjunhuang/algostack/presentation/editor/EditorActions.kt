package com.wenjunhuang.algostack.presentation.editor

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.wenjunhuang.algostack.configuration.ChallengeSettings
import com.wenjunhuang.algostack.execution.ExecutionService
import com.wenjunhuang.algostack.model.Actions
import com.wenjunhuang.algostack.runtime.AuthService
import com.wenjunhuang.algostack.runtime.HttpService
import com.wenjunhuang.algostack.runtime.ProjectTasks
import com.wenjunhuang.algostack.runtime.onEdt
import com.wenjunhuang.algostack.execution.challengeTemplate
import com.wenjunhuang.algostack.presentation.LogConsole
import com.wenjunhuang.algostack.presentation.sidebar.SidebarPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.swing.JComponent
import java.util.concurrent.atomic.AtomicInteger

fun editorState(context: DataContext): EditorState? = PlatformCoreDataKeys.FILE_EDITOR.getData(context)?.getUserData(EditorState.KEY)
abstract class ExecuteAction(private val submit: Boolean) : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        if (!SidebarPolicy.enabled(project.basePath)) return
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        logger.info("execute action: action=${javaClass.simpleName}, file=${file.name}")
        ExecutionService.getInstance(project).start(file, submit)
    }
    override fun update(event: AnActionEvent) {
        val project = event.project
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE)
        val association = if (project != null && file != null && SidebarPolicy.enabled(project.basePath)) ChallengeSettings.getInstance(project).resolve(file) else null
        if (event.place == Actions.CHALLENGE_EDITOR_TOOLBAR_PLACE && toolbarUpdateLogs.getAndIncrement() < 5)
            logger.info("toolbar action update: action=${javaClass.simpleName}, file=${file?.name}, association=${association?.dojo}, visible=${association != null}")
        event.presentation.isVisible = association != null
        event.presentation.isEnabled = association != null && AuthService.getInstance(project!!).isLoggedIn(association.dojo) &&
            !ExecutionService.getInstance(project).isRunning(file!!)
    }
    companion object {
        private val logger = Logger.getInstance(ExecuteAction::class.java)
        private val toolbarUpdateLogs = AtomicInteger()
    }
}
class RunTestAction : ExecuteAction(false)
class SubmitCodeAction : ExecuteAction(true)
class EditorMenuActionGroup : DefaultActionGroup(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(event: AnActionEvent) {
        val project = event.project; val file = event.getData(CommonDataKeys.VIRTUAL_FILE)
        event.presentation.isEnabledAndVisible = project != null && file != null && SidebarPolicy.enabled(project.basePath) && ChallengeSettings.getInstance(project).resolve(file) != null
    }
}
class SurroundSubmissionRegionAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    override fun update(event: AnActionEvent) { event.presentation.isEnabled = editorState(event.dataContext) != null && event.getData(CommonDataKeys.EDITOR)?.selectionModel?.hasSelection() == true }
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val association = editorState(event.dataContext)?.association ?: return
        val selection = editor.selectionModel
        val text = selection.selectedText ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            editor.document.replaceString(selection.selectionStart, selection.selectionEnd, association.language.encloseCodeInRegion(text))
        }
    }
}
class TestCasesEditionAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    override fun update(event: AnActionEvent) { event.presentation.isEnabledAndVisible = editorState(event.dataContext)?.association != null }
    override fun actionPerformed(event: AnActionEvent) {
        val state = editorState(event.dataContext) ?: return
        val association = state.association ?: return
        ProjectTasks.getInstance(state.project).scope.launch {
            try {
                val defaults = association.defaultTestCases.ifEmpty {
                    val data = HttpService.getInstance().api(association.dojo).getQuestionData(association.slug)
                    require(data.titleSlug == association.slug) { "Question identity mismatch" }
                    challengeTemplate(data, association.dojo, association.language, association.identity.version).cases
                }
                onEdt {
                    if (state.association?.identity != association.identity) return@onEdt
                    val dialog = TestCasesDialog(state.project, association.testCases.ifEmpty { defaults }, defaults)
                    if (dialog.showAndGet() && state.association?.identity == association.identity)
                        ChallengeSettings.getInstance(state.project).updateChallengeTestCases(state.file, dialog.cases())
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { LogConsole.getInstance(state.project).error(association.dojo, "Cannot load test cases: ${failure.message}") }
        }
    }
}
