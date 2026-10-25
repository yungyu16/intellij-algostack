package io.github.yungyu16.algostack.presentation

/** 每类事件最多两秒一条；只记选项标识与计数，不记关键词、账号、Cookie 或响应正文。 */
internal class ProblemQueryDiagnostics(
    private val clock: () -> Long = System::nanoTime,
    private val emit: (String) -> Unit
) {
    private val last = mutableMapOf<String, Long>()
    private val suppressed = mutableMapOf<String, Int>()

    @Synchronized private fun record(event: String, fields: String) {
        val now = clock()
        val previous = last[event]
        if (previous != null && now - previous < 2_000_000_000L) {
            suppressed[event] = suppressed.getOrDefault(event, 0) + 1
            return
        }
        last[event] = now
        emit("problem_query event=$event $fields suppressed=${suppressed.remove(event) ?: 0}")
    }

    fun filter(mode: QueryMode, kind: FilterKind, id: String, selected: Boolean) {
        val safeId = id.take(80).replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        record("filter", "mode=$mode kind=$kind optionId=$safeId selected=$selected")
    }

    fun page(event: String, mode: QueryMode, page: Int, count: Int, total: Int?) {
        record(event, "mode=$mode page=$page count=$count total=${total ?: "unknown"}")
    }
}
