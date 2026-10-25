package io.github.yungyu16.algostack.presentation.sidebar

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import io.github.yungyu16.algostack.runtime.ProjectTasks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import java.awt.BorderLayout
import javax.swing.JPanel

abstract class SidebarPanel(protected val project: Project) : JPanel(BorderLayout()), Disposable {
    private val parent = ProjectTasks.getInstance(project).scope
    private val job = SupervisorJob(parent.coroutineContext[Job])
    protected val scope = CoroutineScope(parent.coroutineContext + job)
    override fun dispose() {
        job.cancel()
    }
}
