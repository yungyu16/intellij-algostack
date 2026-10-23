package com.wenjunhuang.codeepiphany.presentation.dojo

import com.wenjunhuang.codeepiphany.presentation.*

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.*
import com.intellij.ui.content.*
import com.wenjunhuang.codeepiphany.PluginBundle
import com.wenjunhuang.codeepiphany.configuration.CodeEpiphanySettings
import com.wenjunhuang.codeepiphany.configuration.PlatformSettings
import com.wenjunhuang.codeepiphany.domain.CodeDojo
import com.wenjunhuang.codeepiphany.model.Actions
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class DojoWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, window: ToolWindow) {
        window.setDefaultContentUiType(ToolWindowContentUiType.COMBO)
        val manager = window.contentManager
        fun titleActions() {
            val content = manager.selectedContent
            if (content?.component is ChallengesView) {
                val view = content.component as ChallengesView
                CodeEpiphanySettings.getInstance(project).state.latestCodeDojo = view.dojo
                window.setTitleActions(listOfNotNull(ActionManager.getInstance().getAction(Actions.LEETCODE_TITLE_TOOLBAR_GROUP),
                    ActionManager.getInstance().getAction(Actions.TITLE_TOOLBAR_GROUP)))
            } else window.setTitleActions(emptyList())
        }
        manager.addContentManagerListener(object : ContentManagerListener {
            override fun selectionChanged(event: ContentManagerEvent) = titleActions()
        })
        fun updateContents() {
            manager.contents.filter { it.component !is ChallengesView }.forEach { manager.removeContent(it, true) }
            for (dojo in CodeDojo.entries) {
                val existing = manager.contents.find { (it.component as? ChallengesView)?.dojo == dojo }
                if (PlatformSettings.getInstance(project, dojo).getSelectedLanguages().isEmpty()) {
                    if (existing != null) manager.removeContent(existing, true)
                } else if (existing == null) {
                    val view = ChallengesView(project, dojo)
                    val content = manager.factory.createContent(view, dojo.show, false)
                    content.setDisposer(view); manager.addContent(content)
                }
            }
            if (manager.contentCount == 0) {
                val prompt = com.wenjunhuang.codeepiphany.toolwindows.dojo.SetupRequiredPromptView(project).component
                manager.addContent(manager.factory.createContent(prompt, PluginBundle.message("ui.setupView.title"), false))
            }
            val preferred = CodeEpiphanySettings.getInstance(project).state.latestCodeDojo
            manager.contents.find { (it.component as? ChallengesView)?.dojo == preferred }?.let { manager.setSelectedContent(it) }
            titleActions()
        }
        project.messageBus.connect(project).subscribe(CodeEpiphanySettings.TOPIC,
            CodeEpiphanySettings.CodeEpiphanySettingsChangedListener { updateContents() })
        updateContents()
    }
}
