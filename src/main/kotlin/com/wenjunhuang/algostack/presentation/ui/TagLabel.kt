package com.wenjunhuang.algostack.presentation.ui

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import com.wenjunhuang.algostack.utils.ui.BackgroundRoundedPanel
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.Icon
import javax.swing.JPanel

class TagLabel : JPanel(BorderLayout()) {
    private val label = JBLabel()
    init {
        isOpaque = false
        val rounded = BackgroundRoundedPanel(12, FlowLayout(FlowLayout.LEFT, 6, 2))
        rounded.background = UIUtil.getPanelBackground(); rounded.add(label); add(rounded)
    }
    fun setText(text: String) { label.text = text }
    fun setIcon(icon: Icon?) { label.icon = icon }
}
