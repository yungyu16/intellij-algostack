package com.wenjunhuang.codeepiphany.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.project.Project

abstract class SettingsUi<S>(private val project: Project) : ConfigurableUi<S>, Disposable {
    fun myProject(): Project = project
    override fun dispose() = Unit
}
