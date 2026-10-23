package com.wenjunhuang.codeepiphany.presentation.sidebar

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.wenjunhuang.codeepiphany.runtime.ProjectTasks
import kotlinx.coroutines.*
import java.awt.BorderLayout
import javax.swing.JPanel

abstract class SidebarPanel(protected val project: Project) : JPanel(BorderLayout()), Disposable {
    private val parent = ProjectTasks.getInstance(project).scope
    private val job = SupervisorJob(parent.coroutineContext[Job])
    protected val scope = CoroutineScope(parent.coroutineContext + job)
    override fun dispose() { job.cancel() }
}
