package com.wenjunhuang.codeepiphany.settings.dojo

import com.wenjunhuang.codeepiphany.presentation.*

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.tabs.*
import com.intellij.util.ui.components.BorderLayoutPanel
import com.wenjunhuang.codeepiphany.PluginBundle
import com.wenjunhuang.codeepiphany.configuration.CodeEpiphanySettings
import com.wenjunhuang.codeepiphany.configuration.PlatformSettings
import com.wenjunhuang.codeepiphany.domain.*
import com.wenjunhuang.codeepiphany.settings.SettingsUi
import javax.swing.JComponent

class SettingsTabPanel(project: Project, private val dojo: CodeDojo) : SettingsUi<PlatformSettings.CodeDojoSettingsState>(project) {
    private val panels = linkedMapOf<Pair<Language, LanguageVersion>, LanguageSettingsPanel>()
    private val tabs = JBTabsFactory.createTabs(project, this) as JBTabsEx
    private val general = GeneralSettingsPanel(project).also { Disposer.register(this, it) }
    private val languages = DefaultActionGroup("Languages", true).apply { templatePresentation.icon = AllIcons.General.Add }
    private val generalTab = TabInfo(general.component).setText(PluginBundle.message("configure.general.text"))
        .setIcon(AllIcons.General.Gear).setTabPaneActions(DefaultActionGroup(languages))
    private val root = BorderLayoutPanel().addToCenter(tabs.component)
    init {
        for ((language, version) in TemplateSamples.languages) languages.add(object : DumbAwareAction(Language.prettyPrint(language, version), null, language.icon) {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT
            override fun update(event: AnActionEvent) { event.presentation.isEnabledAndVisible = (language to version) !in panels }
            override fun actionPerformed(event: AnActionEvent) = add(PlatformSettings.LanguageSettingsState().apply {
                this.language = language; languageVersion = version
            })
        })
    }
    private fun add(state: PlatformSettings.LanguageSettingsState) {
        val language = state.language ?: return
        val version = state.languageVersion ?: return
        val key = language to version
        val panel = LanguageSettingsPanel(myProject(), dojo) { lang, ver -> TemplateSamples.template(dojo, lang, ver) }
        Disposer.register(this, panel); panel.reset(state); panels[key] = panel
        val tab = TabInfo(panel.component).setObject(key).setText(Language.prettyPrint(language, version)).setIcon(language.icon)
            .setTabPaneActions(DefaultActionGroup(languages))
        tab.setTabLabelActions(DefaultActionGroup(object : DumbAwareAction("Close", null, AllIcons.Actions.Close) {
            override fun actionPerformed(event: AnActionEvent) { panels.remove(key); tabs.removeTab(tab); Disposer.dispose(panel) }
        }), "CodeEpiphany.LanguageSettings")
        tabs.addTab(tab); tabs.select(tab, true)
    }
    override fun reset(state: PlatformSettings.CodeDojoSettingsState) {
        tabs.removeAllTabs(); panels.values.forEach(Disposer::dispose); panels.clear()
        tabs.addTab(generalTab); general.reset(state); state.languageSettings.forEach(::add)
    }
    override fun isModified(state: PlatformSettings.CodeDojoSettingsState): Boolean =
        general.isModified(state) || panels.keys != state.languageSettings.map { it.language to it.languageVersion }.toSet() ||
            state.languageSettings.any { panels[it.language to it.languageVersion]?.isModified(it) != false }
    @Throws(ConfigurationException::class)
    override fun apply(state: PlatformSettings.CodeDojoSettingsState) {
        val draft = PlatformSettings.CodeDojoSettingsState()
        general.apply(draft)
        for ((key, panel) in panels) {
            try { draft.languageSettings += PlatformSettings.LanguageSettingsState().also(panel::apply) }
            catch (failure: ConfigurationException) { tabs.findInfo(key)?.let { tabs.select(it, true) }; throw failure }
        }
        state.descriptionCSS = draft.descriptionCSS; state.languageSettings = draft.languageSettings
        myProject().messageBus.syncPublisher(CodeEpiphanySettings.TOPIC).changed()
    }
    override fun getComponent(): JComponent = root
}
