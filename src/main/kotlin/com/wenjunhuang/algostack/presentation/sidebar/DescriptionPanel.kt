package com.wenjunhuang.algostack.presentation.sidebar

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.wenjunhuang.algostack.domain.CodeDojo
import com.wenjunhuang.algostack.execution.challengeDescriptionHtml
import com.wenjunhuang.algostack.presentation.browser.WebDocumentView
import com.wenjunhuang.algostack.runtime.HttpService
import com.wenjunhuang.algostack.runtime.onEdt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import javax.swing.JLabel

class DescriptionPanel(project: Project) : SidebarPanel(project) {
    private val document = WebDocumentView(project)
    private val status = JLabel()
    private var html = ""
    init {
        Disposer.register(this, document); add(document, BorderLayout.CENTER); add(status, BorderLayout.SOUTH)
        scope.launch { SidebarContext.getInstance(project).challenge.collectLatest { challenge ->
            onEdt { html = ""; status.text = if (challenge == null) "" else "Loading…"; refresh() }
            if (challenge != null) try {
                val data = HttpService.getInstance().api(challenge.dojo).getQuestionData(challenge.slug)
                require(data.titleSlug == challenge.slug) { "Question identity mismatch" }
                val rendered = challengeDescriptionHtml(data)
                onEdt {
                    html = rendered
                    status.text = ""; refresh()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { onEdt { status.text = failure.message ?: "Cannot load description" } }
        } }
    }
    private fun refresh() {
        val challenge = SidebarContext.getInstance(project).challenge.value
        document.setContent(html, challenge?.dojo ?: CodeDojo.LeetCodeCN)
    }
}
