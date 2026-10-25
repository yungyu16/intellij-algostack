package com.wenjunhuang.algostack.presentation.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.wenjunhuang.algostack.PluginBundle
import com.wenjunhuang.algostack.configuration.ChallengeSettings.TestCase
import com.wenjunhuang.algostack.utils.testCases.TestCaseItemPanel
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

class TestCasesDialog(private val project: Project, initial: List<TestCase>, private val defaults: List<TestCase>) : DialogWrapper(project) {
    private val rows = arrayListOf<TestCaseItemPanel>()
    private val list = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val root = JPanel(BorderLayout()).apply { preferredSize = JBUI.size(800, 600) }
    init {
        val toolbar = ActionManager.getInstance().createActionToolbar("AlgoStack.TestCases", DefaultActionGroup(
            object : DumbAwareAction(PluginBundle.message("action.AlgoStack.Actions.TestCases.Add.text"), null, AllIcons.General.Add) {
                override fun actionPerformed(event: AnActionEvent) { rebuild(cases() + TestCase()) }
            }, object : DumbAwareAction(PluginBundle.message("action.AlgoStack.Actions.TestCases.Reset.text"), null, AllIcons.Actions.Refresh) {
                override fun actionPerformed(event: AnActionEvent) { rebuild(defaults) }
            }), true)
        toolbar.targetComponent = root
        root.add(toolbar.component, BorderLayout.NORTH); root.add(JBScrollPane(list), BorderLayout.CENTER)
        rebuild(initial); init(); title = PluginBundle.message("testcases.dialog.title")
    }
    fun cases(): List<TestCase> = rows.map { TestCase(it.inputValue, it.expectedValue) }
    private fun rebuild(values: List<TestCase>) {
        rows.clear(); list.removeAll()
        values.forEachIndexed { index, value ->
            val panel = TestCaseItemPanel(project, PluginBundle.message("testcases.title", index + 1), value.input, value.expectedOutput,
                { rebuild(cases().filterIndexed { i, _ -> i != index }) }, null)
            rows += panel; list.add(panel.component)
        }
        list.revalidate(); list.repaint()
    }
    override fun createCenterPanel(): JComponent = root
}
