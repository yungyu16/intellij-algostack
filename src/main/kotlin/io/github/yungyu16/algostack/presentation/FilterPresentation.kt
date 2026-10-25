package io.github.yungyu16.algostack.presentation

import io.github.yungyu16.algostack.domain.CodeDojo
import io.github.yungyu16.algostack.domain.OrderDirection
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTable
import java.awt.Point

/** 各模式只展示自身真正参与请求的筛选，保留其他模式的选择供切回时恢复。 */
internal val QueryMode.filterKinds: Set<FilterKind>
    get() = when (this) {
        QueryMode.QueryParameters -> setOf(FilterKind.Category, FilterKind.Favorite, FilterKind.Difficulty, FilterKind.Status, FilterKind.Tags)
        QueryMode.CompanyQuery -> setOf(FilterKind.Company, FilterKind.Position, FilterKind.InterviewPeriod, FilterKind.Difficulty, FilterKind.Status, FilterKind.Tags)
    }

internal val FilterKind.clearable: Boolean get() = this != FilterKind.InterviewPeriod

internal fun <T> toggleSingle(current: T?, choices: List<T>, id: String, key: (T) -> String): T? =
    if (current != null && key(current) == id) null else choices.find { key(it) == id } ?: current

/** 按当前选择增删，不能用新元数据重建全部选择而悄悄丢掉过期项。 */
internal fun <T> toggleMultiple(current: List<T>, choices: List<T>, id: String, key: (T) -> String): List<T> =
    if (current.any { key(it) == id }) current.filterNot { key(it) == id }
    else choices.find { key(it) == id }?.let { current + it } ?: current

internal fun filterChoices(available: List<FilterItem>, selected: Set<String>): List<FilterItem> =
    available + (selected - available.map { it.id }.toSet()).map { FilterItem(it, it) }

internal fun sortedHeader(title: String, field: String?, order: QueryOrder?): String =
    if (field == null || order?.field != field) title
    else "$title ${if (order.direction == OrderDirection.Ascending) "↑" else "↓"}"

internal fun modelColumnAt(table: JTable, point: Point): Int = table.convertColumnIndexToModel(table.columnAtPoint(point))

internal fun refreshHeaders(table: JTable) {
    for (index in 0 until table.columnModel.columnCount) {
        val column = table.columnModel.getColumn(index)
        column.headerValue = table.model.getColumnName(column.modelIndex)
    }
    table.tableHeader.repaint()
}

internal fun filterChip(kind: FilterKind, item: FilterItem, remove: () -> Unit): JComponent =
    // 公司查询必须有时间范围，可从菜单选择 All，但不存在“无时间范围”的状态。
    if (!kind.clearable) JLabel(item.text)
    else JButton("${item.text} ×").apply { isFocusable = false; addActionListener { remove() } }
