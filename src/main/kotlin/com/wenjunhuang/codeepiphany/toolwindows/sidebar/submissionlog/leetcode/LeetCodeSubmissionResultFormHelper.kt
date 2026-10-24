package com.wenjunhuang.codeepiphany.toolwindows.sidebar.submissionlog.leetcode

import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.wenjunhuang.codeepiphany.domain.SubmissionResult
import com.wenjunhuang.codeepiphany.storage.SubmissionMetrics
import com.wenjunhuang.codeepiphany.storage.SubmissionRecord
import com.wenjunhuang.codeepiphany.utils.ColorUtils
import com.wenjunhuang.codeepiphany.utils.ui.BackgroundRoundedPanel
import java.awt.BorderLayout
import javax.swing.JComponent

object LeetCodeSubmissionResultFormHelper {
    @JvmStatic fun createFromSubmissionType(result: SubmissionResult, record: SubmissionRecord, metrics: SubmissionMetrics): JComponent = when (result) {
        SubmissionResult.Success -> LeetCodeSuccessResultForm(metrics.statusRuntime.orEmpty(), metrics.statusMemory.orEmpty()).component
        SubmissionResult.Failure -> LeetCodeWrongAnswerResultForm(metrics.inputFormatted.orEmpty(), metrics.codeOutput.orEmpty(), metrics.expectedOutput.orEmpty()).component
        else -> BackgroundRoundedPanel(12, BorderLayout()).apply {
            background = ColorUtils.ERROR_BACKGROUND; border = JBUI.Borders.empty(5)
            add(JBTextArea(record.message.orEmpty()).apply { lineWrap = true; isEditable = false; isOpaque = false; foreground = ColorUtils.ERROR_FOREGROUND })
        }
    }
}
