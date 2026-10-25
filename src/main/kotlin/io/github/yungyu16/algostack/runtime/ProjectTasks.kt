package io.github.yungyu16.algostack.runtime

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*

/** IDE-injected scope is cancelled on project close or plugin unload. */
@Service(Service.Level.PROJECT)
class ProjectTasks(val scope: CoroutineScope) {
    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

    companion object {
        @JvmStatic
        fun getInstance(project: Project): ProjectTasks = project.getService(ProjectTasks::class.java)
    }
}

suspend fun <T> onEdt(block: () -> T): T = withContext(Dispatchers.EDT) { block() }
