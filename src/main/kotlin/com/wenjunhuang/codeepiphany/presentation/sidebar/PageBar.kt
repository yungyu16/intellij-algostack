package com.wenjunhuang.codeepiphany.presentation.sidebar

import com.wenjunhuang.codeepiphany.domain.PageSize
import com.wenjunhuang.codeepiphany.domain.Pagination
import java.awt.FlowLayout
import javax.swing.*

class PageBar(private val changed: (Int, PageSize) -> Unit) : JPanel(FlowLayout(FlowLayout.RIGHT)) {
    private var page = Pagination()
    private var updating = false
    private val number = JSpinner(SpinnerNumberModel(1, 1, 1, 1))
    private val size = JComboBox(PageSize.entries.map { it.value }.toTypedArray())
    private val summary = JLabel()
    init {
        fun button(text: String, next: () -> Int) = JButton(text).apply { addActionListener { changed(next().coerceIn(1, page.totalPages), page.pageSize) } }
        add(button("«") { 1 }); add(button("‹") { page.currentPage - 1 }); add(number); add(summary)
        add(button("›") { page.currentPage + 1 }); add(button("»") { page.totalPages }); add(size)
        number.addChangeListener { if (!updating) changed((number.value as Number).toInt(), page.pageSize) }
        size.addActionListener { if (!updating) changed(1, PageSize.entries.first { it.value == size.selectedItem }) }
        update(page)
    }
    fun update(value: Pagination) {
        page = value; updating = true
        (number.model as SpinnerNumberModel).maximum = maxOf(page.currentPage, page.totalPages)
        number.value = page.currentPage; size.selectedItem = page.pageSize.value
        summary.text = "/ ${page.totalPages} (${page.totalSize})"; updating = false
    }
}
