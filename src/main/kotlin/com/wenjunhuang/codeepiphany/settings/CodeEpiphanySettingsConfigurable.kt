package com.wenjunhuang.codeepiphany.settings

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent
import javax.swing.JPanel

/** 父节点只承载两个站点的配置入口。 */
class CodeEpiphanySettingsConfigurable : Configurable {
    override fun getDisplayName() = "leetcode-editor"
    override fun createComponent(): JComponent = JPanel()
    override fun isModified() = false
    override fun apply() = Unit
}
