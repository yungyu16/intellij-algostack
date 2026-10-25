package com.wenjunhuang.algostack.presentation

import com.intellij.icons.AllIcons
import com.wenjunhuang.algostack.PluginBundle
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.*

/** 原生单行组件：范围切换保留各自草稿，只有 Enter / 搜索才提交关键词。 */
internal class ProblemSearchBar(
    private val changeScope: (QueryMode) -> Unit,
    private val submit: (String) -> Unit
) : JPanel(BorderLayout(6, 0)) {
    internal val scopeSelector = JComboBox(QueryMode.entries.toTypedArray())
    internal val keywordField = JTextField(12)
    internal val searchButton = JButton(PluginBundle.message("ui.query.search"), AllIcons.Actions.Find)
    private var mode = QueryMode.QueryParameters
    private val drafts = mutableMapOf<QueryMode, String>()
    private var updating = false

    init {
        scopeSelector.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int,
                selected: Boolean, focused: Boolean): Component {
                super.getListCellRendererComponent(list, (value as? QueryMode)?.show, index, selected, focused)
                icon = when (value) {
                    QueryMode.QueryParameters -> AllIcons.Actions.ListFiles
                    QueryMode.CompanyQuery -> AllIcons.Nodes.ModuleGroup
                    else -> null
                }
                return this
            }
        }
        scopeSelector.accessibleContext.accessibleName = PluginBundle.message("ui.query.scope")
        searchButton.toolTipText = PluginBundle.message("ui.query.search")
        keywordField.toolTipText = PluginBundle.message("ui.query.searchHint")
        keywordField.accessibleContext.accessibleName = keywordField.toolTipText
        keywordField.minimumSize = Dimension(0, keywordField.preferredSize.height)
        add(scopeSelector, BorderLayout.WEST)
        add(keywordField, BorderLayout.CENTER)
        add(searchButton, BorderLayout.EAST)
        keywordField.addActionListener { submit(keywordField.text) }
        searchButton.addActionListener { submit(keywordField.text) }
        scopeSelector.addActionListener {
            if (!updating) changeScope(scopeSelector.selectedItem as QueryMode)
        }
    }

    fun showScope(next: QueryMode) {
        if (mode != next) {
            drafts[mode] = keywordField.text
            keywordField.text = drafts[next].orEmpty()
            mode = next
        }
        updating = true
        try { scopeSelector.selectedItem = next } finally { updating = false }
    }
}
