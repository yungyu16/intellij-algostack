package com.wenjunhuang.codeepiphany.presentation.sidebar

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.wenjunhuang.codeepiphany.configuration.CodeEpiphanySettings
import com.wenjunhuang.codeepiphany.configuration.PlatformSettings
import com.wenjunhuang.codeepiphany.domain.CodeDojo
import com.wenjunhuang.codeepiphany.presentation.browser.WebDocumentView
import com.wenjunhuang.codeepiphany.runtime.onEdt
import kotlinx.coroutines.launch
import java.awt.BorderLayout

class DescriptionPanel(project: Project) : SidebarPanel(project) {
    private val document = WebDocumentView(project)
    init {
        Disposer.register(this, document); add(document, BorderLayout.CENTER)
        scope.launch { SidebarContext.getInstance(project).challenge.collect { onEdt { refresh() } } }
        project.messageBus.connect(this).subscribe(CodeEpiphanySettings.TOPIC, CodeEpiphanySettings.CodeEpiphanySettingsChangedListener { refresh() })
    }
    private fun refresh() {
        val challenge = SidebarContext.getInstance(project).challenge.value
        document.setContent(challenge?.description ?: "", challenge?.dojo ?: CodeDojo.LeetCodeCN,
            challenge?.let { PlatformSettings.getInstance(project, it.dojo).state.descriptionCSS }.orEmpty())
    }
}
