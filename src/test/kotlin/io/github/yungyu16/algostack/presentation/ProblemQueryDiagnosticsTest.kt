package io.github.yungyu16.algostack.presentation

import org.junit.Assert.*
import org.junit.Test

class ProblemQueryDiagnosticsTest {
    @Test fun eventsAreBoundedAndDoNotIncludeArbitraryOptionText() {
        var now = 0L
        val messages = mutableListOf<String>()
        val diagnostics = ProblemQueryDiagnostics({ now }, messages::add)
        diagnostics.filter(QueryMode.QueryParameters, FilterKind.Tags, "array\n injected=true", true)
        repeat(100) { diagnostics.filter(QueryMode.QueryParameters, FilterKind.Tags, "array", false) }
        diagnostics.page("loaded", QueryMode.QueryParameters, 1, 20, 100)
        assertEquals(2, messages.size)
        assertFalse(messages.first().contains('\n'))
        assertTrue(messages.first().contains("optionId=array__injected_true selected=true"))
        now = 2_000_000_000L
        diagnostics.filter(QueryMode.QueryParameters, FilterKind.Tags, "array", false)
        assertTrue(messages.last().endsWith("suppressed=100"))
    }
}
