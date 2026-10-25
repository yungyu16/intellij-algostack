package io.github.yungyu16.algostack.presentation.editor

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import io.github.yungyu16.algostack.execution.ExecutionService
import io.github.yungyu16.algostack.presentation.sidebar.SidebarPolicy
import io.github.yungyu16.algostack.runtime.AuthService
import io.github.yungyu16.algostack.utils.ChallengeFiles

fun editorState(context: DataContext): EditorState? =
    PlatformCoreDataKeys.FILE_EDITOR.getData(context)?.getUserData(EditorState.KEY)

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
        val identity =
            if (project != null && file != null && SidebarPolicy.enabled(project.basePath)) ChallengeFiles.resolve(file) else null
        event.presentation.isVisible = identity != null
        event.presentation.isEnabled =
            identity != null && AuthService.getInstance(project!!).isLoggedIn(identity.dojo) &&
                !ExecutionService.getInstance(project).isRunning(file!!)
    }

    companion object {
        private val logger = Logger.getInstance(ExecuteAction::class.java)
    }
}

class RunTestAction : ExecuteAction(false)
class SubmitCodeAction : ExecuteAction(true)
class EditorMenuActionGroup : DefaultActionGroup(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(event: AnActionEvent) {
        val project = event.project;
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE)
        event.presentation.isEnabledAndVisible =
            project != null && file != null && SidebarPolicy.enabled(project.basePath) && ChallengeFiles.resolve(file) != null
    }
}

class SurroundSubmissionRegionAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled =
            editorState(event.dataContext) != null && event.getData(CommonDataKeys.EDITOR)?.selectionModel?.hasSelection() == true
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val identity = editorState(event.dataContext)?.identity ?: return
        val selection = editor.selectionModel
        val text = selection.selectedText ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            editor.document.replaceString(
                selection.selectionStart,
                selection.selectionEnd,
                identity.language.encloseCodeInRegion(text)
            )
        }
    }
}
