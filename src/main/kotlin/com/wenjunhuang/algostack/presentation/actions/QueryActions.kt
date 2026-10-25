package com.wenjunhuang.algostack.presentation.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager
import com.wenjunhuang.algostack.execution.ChallengeConventions
import com.wenjunhuang.algostack.domain.Language
import com.wenjunhuang.algostack.presentation.*
import icons.AlgoStackIcons
import javax.swing.JComponent

fun site(event: AnActionEvent): SiteHost? = event.getData(ActionContext.SITE) ?: event.project?.let {
    ToolWindowManager.getInstance(it).getToolWindow("题栈")?.contentManager?.selectedContent?.component as? SiteHost
}
abstract class FilterAction(private val kind: FilterKind) : ComboBoxAction(), DumbAware {
    init {
        templatePresentation.icon = when (kind) {
            FilterKind.Category -> AllIcons.Actions.GroupBy
            FilterKind.Favorite -> AllIcons.Actions.ListFiles
            FilterKind.Difficulty -> AllIcons.Nodes.SortBySeverity
            FilterKind.Status -> AllIcons.General.InspectionsOK
            FilterKind.Tags -> AllIcons.Nodes.Tag
            FilterKind.Company -> AllIcons.Nodes.ModuleGroup
            FilterKind.Position -> AllIcons.General.User
            FilterKind.InterviewPeriod -> AllIcons.General.History
            else -> null
        }
    }
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
        return ChallengeConventions.languages.map { (language, version) ->
            object : com.intellij.openapi.project.DumbAwareAction(Language.prettyPrint(language, version), null, language.icon) {
                override fun actionPerformed(event: AnActionEvent) = host.openSelection(language, version)
            }
        }.toTypedArray()
    }
}
