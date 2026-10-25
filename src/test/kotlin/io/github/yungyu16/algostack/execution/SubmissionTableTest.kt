package io.github.yungyu16.algostack.execution

import org.junit.Assert.assertEquals
import org.junit.Test

/** 提交指标表格与上游 Tabulator 的输出一致：带边框、列宽取最长单元格、单元格右对齐。 */
class SubmissionTableTest {
    @Test fun tableKeepsBordersAndRightAlignedColumns() {
        val table = formatSubmissionTable(
            listOf("Metric", "Value"),
            listOf("Runtime", "6 ms (Beats 92.88%)"),
            listOf("Memory", "46.8 MB (Beats 85.83%)")
        )
        val lines = table.lines().toList()
        assertEquals(6, lines.size)
        assertEquals("+-------+----------------------+", lines[0])
        assertEquals("| Metric|" + " ".repeat(17) + "Value|", lines[1])
        assertEquals("+-------+----------------------+", lines[2])
        assertEquals("|Runtime|" + " ".repeat(3) + "6 ms (Beats 92.88%)|", lines[3])
        assertEquals("| Memory|46.8 MB (Beats 85.83%)|", lines[4])
        assertEquals("+-------+----------------------+", lines[5])
    }

    @Test fun columnWidthFollowsTheLongestCell() {
        val lines = formatSubmissionTable(listOf("A", "B"), listOf("longer", "x")).lines().toList()
        assertEquals("+------+-+", lines[0])
        assertEquals("|     A|B|", lines[1])
        assertEquals("|longer|x|", lines[3])
    }
}
