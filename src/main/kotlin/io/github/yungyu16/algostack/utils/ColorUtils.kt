package io.github.yungyu16.algostack.utils

import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import java.awt.Color

/** 与上游 ColorUtils 一致的配色常量，供控制台、提交结果与查询界面共用。 */
object ColorUtils {
    val ERROR_FOREGROUND = JBColor(0xeb353a, 0xee615e)
    val ERROR_BACKGROUND = JBColor(0xfdeff0, 0x362b2a)
    val SUCCESS_FOREGROUND = JBColor(0x388e3c, 0x66bb6a)
    val SUCCESS_BACKGROUND = JBColor(0xe8f5e9, 0x1b5e20)
    val LABEL_GRAY_COLOR: JBColor = JBColor.namedColor("Label.infoForeground", JBColor(Gray._120, Gray._135))

    /** 解析 #RGB 或 #RRGGBB 形式的颜色；格式非法时返回 null。 */
    fun hexToColor(hex: String): Color? {
        if (!hex.startsWith("#")) return null
        return runCatching {
            val components = hex.substring(1).chunked(2).map { Integer.parseInt(it, 16) }
            when (components.size) {
                3 -> Color(components[0], components[1], components[2])
                4 -> Color(components[0], components[1], components[2], components[3])
                else -> null
            }
        }.getOrNull()
    }
}
