package com.wenjunhuang.codeepiphany.presentation.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager
import com.wenjunhuang.codeepiphany.configuration.PlatformSettings
import com.wenjunhuang.codeepiphany.domain.Language
import com.wenjunhuang.codeepiphany.presentation.*
import icons.CodeEpiphanyIcons
import javax.swing.JComponent

fun site(event: AnActionEvent): SiteHost? = event.getData(ActionContext.SITE) ?: event.project?.let {
    ToolWindowManager.getInstance(it).getToolWindow("leetcode-editor")?.contentManager?.selectedContent?.component as? SiteHost
}
abstract class FilterAction(private val kind: FilterKind) : ComboBoxAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    override fun update(event: AnActionEvent) {
        val host = event.getData(ActionContext.FILTERS)
        event.presentation.isEnabledAndVisible = host != null && filterChoices(host.items(kind), host.selected(kind)).isNotEmpty()
    }
    public override fun createPopupActionGroup(button: JComponent, context: DataContext): DefaultActionGroup {
        val host = ActionContext.FILTERS.getData(context) ?: return DefaultActionGroup()
        val root = DefaultActionGroup()
        val groups = linkedMapOf<String, DefaultActionGroup>()
        for (item in filterChoices(host.items(kind), host.selected(kind))) {
            val action = object : CheckboxAction(item.text, item.text, item.icon), DumbAware {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
                override fun isSelected(event: AnActionEvent): Boolean = item.id in host.selected(kind)
                override fun setSelected(event: AnActionEvent, selected: Boolean) {
                    if ((!selected && !kind.clearable)) return
                    if (selected != (item.id in host.selected(kind))) host.toggle(kind, item.id)
                }
            }
            if (item.group == null) root.add(action)
            else groups.getOrPut(item.group) { DefaultActionGroup(item.group, true).also(root::add) }.add(action)
        }
        return root
    }
}
class CategoryAction : FilterAction(FilterKind.Category)
class FavoriteAction : FilterAction(FilterKind.Favorite)
class DifficultyAction : FilterAction(FilterKind.Difficulty)
class StatusAction : FilterAction(FilterKind.Status)
class TagsAction : FilterAction(FilterKind.Tags)
class CompanyAction : FilterAction(FilterKind.Company)
class PositionAction : FilterAction(FilterKind.Position)
class InterviewPeriodAction : FilterAction(FilterKind.InterviewPeriod)
class LanguageAction : FilterAction(FilterKind.Language)
class DojoAction : FilterAction(FilterKind.Dojo)
class RefreshAction : com.intellij.openapi.project.DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    override fun actionPerformed(event: AnActionEvent) { event.getData(ActionContext.QUERY)?.refresh() }
    override fun update(event: AnActionEvent) { event.presentation.isEnabledAndVisible = event.getData(ActionContext.QUERY) != null }
}
class OpenChallengeGroup : ActionGroup(), DumbAware {
    init { isPopup = true }
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    override fun update(event: AnActionEvent) { event.presentation.isEnabledAndVisible = event.getData(ActionContext.CHALLENGES)?.canOpenSelection == true }
    override fun getChildren(event: AnActionEvent?): Array<AnAction> {
        val project = event?.project ?: return emptyArray()
        val host = event.getData(ActionContext.CHALLENGES) ?: return emptyArray()
        return PlatformSettings.getInstance(project, host.dojo).getSelectedLanguages().map { (language, version) ->
            object : com.intellij.openapi.project.DumbAwareAction(Language.prettyPrint(language, version), null, language.icon) {
                override fun actionPerformed(event: AnActionEvent) = host.openSelection(language, version)
            }
        }.toTypedArray()
    }
}
class ModeAction : ActionGroup(), DumbAware {
    init { isPopup = true }
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    override fun update(event: AnActionEvent) {
        val owner = site(event); event.presentation.isEnabledAndVisible = owner?.loggedIn == true
        event.presentation.icon = when (owner?.mode) { QueryMode.CompanyQuery -> CodeEpiphanyIcons.BUILDING; else -> CodeEpiphanyIcons.QUERY_PARAM }
    }
    override fun getChildren(event: AnActionEvent?): Array<AnAction> {
        val owner = event?.let(::site) ?: return emptyArray()
        return QueryMode.entries.map { mode -> object : CheckboxAction(mode.show), DumbAware {
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            override fun isSelected(event: AnActionEvent): Boolean = owner.mode == mode
            override fun setSelected(event: AnActionEvent, selected: Boolean) { if (selected) owner.switchTo(mode) }
        } }.toTypedArray()
    }
}
