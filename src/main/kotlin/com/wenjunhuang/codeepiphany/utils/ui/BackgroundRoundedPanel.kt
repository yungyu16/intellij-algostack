package com.wenjunhuang.codeepiphany.utils.ui

import com.intellij.util.ui.GraphicsUtil
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.LayoutManager
import javax.swing.JComponent

class BackgroundRoundedPanel(private val radius: Int, layoutManager: LayoutManager) : JComponent() {
    init { layout = layoutManager; isOpaque = false }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val graphics = g.create() as Graphics2D
        try {
            val config = GraphicsUtil.setupAAPainting(graphics)
            try {
                graphics.color = background
                graphics.fillRoundRect(0, 0, width, height, radius, radius)
            } finally { config.restore() }
        } finally { graphics.dispose() }
    }
}
