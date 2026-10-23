package com.wenjunhuang.codeepiphany.leetcode.settings

import com.intellij.openapi.project.Project
import com.wenjunhuang.codeepiphany.domain.CodeDojo
import com.wenjunhuang.codeepiphany.settings.dojo.BaseSettingsConfigurable

class LeetCodeSettingsConfigurable(project: Project) : BaseSettingsConfigurable(project, CodeDojo.LeetCode, "CodeEpiphany.Settings.LeetCode")
class LeetCodeCNSettingsConfigurable(project: Project) : BaseSettingsConfigurable(project, CodeDojo.LeetCodeCN, "CodeEpiphany.Settings.LeetCodeCN")
