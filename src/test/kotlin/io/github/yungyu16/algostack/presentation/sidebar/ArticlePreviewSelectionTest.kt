package io.github.yungyu16.algostack.presentation.sidebar

import io.github.yungyu16.algostack.presentation.LatestQuery
import io.github.yungyu16.algostack.presentation.QueryState
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel

class ArticlePreviewSelectionTest {
    private class Fixture(scope: CoroutineScope, execute: suspend (String) -> String = { it }) : AutoCloseable {
        val model = DefaultTableModel(arrayOf(arrayOf<Any>("first"), arrayOf<Any>("second")), arrayOf<Any>("slug"))
        val table = JTable(model).apply { setSelectionMode(ListSelectionModel.SINGLE_SELECTION) }
        val query = LatestQuery(scope, 0, execute)
        var preview = ""
        var request: Job? = null
        val selection = ArticlePreviewSelection(table, query, { preview = "" }) {
            request = query.submit(table.getValueAt(table.selectedRow, 0) as String)
        }

        fun apply(state: QueryState<String>) {
            if (query.isCurrent(state) && state is QueryState.Success) preview = state.value
        }

        override fun close() = query.close()
    }

    @Test
    fun clearingSelectionCancelsEvenANonCooperativeDetailRequest() = runBlocking {
        withTimeout(2_000) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            lateinit var f: Fixture
            edt {
                f = Fixture(this) { entered.complete(Unit); withContext(NonCancellable) { release.await() }; "late" }
            }
            try {
                edt { f.table.setRowSelectionInterval(0, 0) }
                entered.await()
                val pending = f.request!!
                edt {
                    f.preview = "previous body"
                    f.table.clearSelection()
                    assertTrue(pending.isCancelled)
                    assertEquals("", f.preview)
                    assertEquals(QueryState.Idle, f.query.states.value)
                }
                release.complete(Unit)
                pending.join()
                edt {
                    f.apply(f.query.states.value)
                    assertEquals("", f.preview)
                    assertEquals(QueryState.Idle, f.query.states.value)
                }
            } finally {
                release.complete(Unit); edt { f.close() }
            }
        }
    }

    @Test
    fun replacingTheListRejectsQueuedDetailsForBothEmptyAndNonEmptyResults() = runBlocking {
        withTimeout(2_000) {
            for (empty in listOf(false, true)) {
                lateinit var f: Fixture
                edt { f = Fixture(this); f.table.setRowSelectionInterval(0, 0) }
                try {
                    f.request!!.join()
                    val queued = f.query.states.value
                    edt {
                        f.apply(queued)
                        assertEquals("first", f.preview)
                        // 真实 JTable 的模型替换会清除 selection，不能留下旧正文或已排队的结果。
                        f.model.setValueAt("replacement", 0, 0)
                        f.model.rowCount = if (empty) 0 else 1
                        f.model.fireTableDataChanged()
                        assertEquals(-1, f.table.selectedRow)
                        assertEquals("", f.preview)
                        assertFalse(f.query.isCurrent(queued))
                        f.apply(queued)
                        assertEquals("", f.preview)
                    }
                } finally {
                    edt { f.close() }
                }
            }
        }
    }

    @Test
    fun listRefreshInvalidatesBeforeResultsArriveAndAlsoWorksWithoutSelection() = runBlocking {
        withTimeout(2_000) {
            lateinit var f: Fixture
            edt { f = Fixture(this); f.table.setRowSelectionInterval(0, 0) }
            try {
                f.request!!.join()
                val queued = f.query.states.value
                edt {
                    f.apply(queued)
                    f.selection.clear()
                    assertEquals(-1, f.table.selectedRow)
                    assertEquals(2, f.table.rowCount)
                    assertEquals("", f.preview)
                    assertFalse(f.query.isCurrent(queued))
                    // 空选择不会再发 selection 事件，刷新仍须显式使详情失效。
                    f.request = f.query.submit("queued without selection")
                }
                f.request!!.join()
                val withoutSelection = f.query.states.value
                edt {
                    f.selection.clear()
                    assertFalse(f.query.isCurrent(withoutSelection))
                    f.apply(withoutSelection)
                    assertEquals("", f.preview)
                }
            } finally {
                edt { f.close() }
            }
        }
    }

    @Test
    fun changingSelectionClearsTheOldPreviewAndPublishesOnlyTheNewDetail() = runBlocking {
        withTimeout(2_000) {
            lateinit var f: Fixture
            edt { f = Fixture(this); f.table.setRowSelectionInterval(0, 0) }
            try {
                f.request!!.join()
                val previous = f.query.states.value
                edt {
                    f.apply(previous)
                    assertEquals("first", f.preview)
                    f.table.selectionModel.valueIsAdjusting = true
                    f.table.setRowSelectionInterval(1, 1)
                    assertEquals("", f.preview)
                    assertEquals(QueryState.Idle, f.query.states.value)
                    f.table.selectionModel.valueIsAdjusting = false
                    f.apply(previous)
                    assertEquals("", f.preview)
                }
                f.request!!.join()
                edt {
                    f.apply(f.query.states.value)
                    assertEquals("second", f.preview)
                }
            } finally {
                edt { f.close() }
            }
        }
    }

    private fun edt(action: () -> Unit) = SwingUtilities.invokeAndWait(action)
}
