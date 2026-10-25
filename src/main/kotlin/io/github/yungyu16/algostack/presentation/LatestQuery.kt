package io.github.yungyu16.algostack.presentation

import com.intellij.openapi.progress.ProcessCanceledException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface QueryState<out T> {
    data object Idle : QueryState<Nothing>
    data object Loading : QueryState<Nothing>

    // 每次发布的终态实例兼作请求标识。不能改成 data class：慢消费者可能错过 Loading，
    // 此时 StateFlow 会合并内容相等的新结果，而 isCurrent 已拒绝旧实例，造成两次都不展示。
    class Success<T>(val value: T) : QueryState<T>
    class Failure(val error: Throwable) : QueryState<Nothing>
}

/** One view owns one query job. Replacing/closing it never cancels the project scope. */
class LatestQuery<P, R>(
    private val scope: CoroutineScope,
    private val debounceMillis: Long = 300,
    private val execute: suspend (P) -> R
) : AutoCloseable {
    private val status = MutableStateFlow<QueryState<R>>(QueryState.Idle)
    val states = status.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private var closed = false

    @Synchronized
    fun submit(parameters: P): Job? {
        if (closed) return null
        val id = ++generation
        job?.cancel()
        status.value = QueryState.Loading
        return scope.launch {
            try {
                if (debounceMillis > 0) delay(debounceMillis)
                val result = execute(parameters)
                currentCoroutineContext().ensureActive()
                synchronized(this@LatestQuery) {
                    if (!closed && generation == id) status.value = QueryState.Success(result)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (cancelled: ProcessCanceledException) {
                throw cancelled
            } catch (failure: Throwable) {
                synchronized(this@LatestQuery) {
                    if (!closed && generation == id) status.value = QueryState.Failure(failure)
                }
            }
        }.also { job = it }
    }

    /** 消费者切到 EDT 后再检查；submit/cancel 与 UI 应用须在同一线程，避免检查后上下文再变。 */
    fun isCurrent(state: QueryState<R>): Boolean = states.value === state

    @Synchronized
    fun cancel() {
        generation++; job?.cancel(); job = null; status.value = QueryState.Idle
    }

    @Synchronized
    override fun close() {
        closed = true
        cancel()
    }
}
