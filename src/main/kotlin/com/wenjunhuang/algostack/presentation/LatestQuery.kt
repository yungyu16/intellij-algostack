package com.wenjunhuang.algostack.presentation

import com.intellij.openapi.progress.ProcessCanceledException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface QueryState<out T> {
    data object Idle : QueryState<Nothing>
    data object Loading : QueryState<Nothing>
    data class Success<T>(val value: T) : QueryState<T>
    data class Failure(val error: Throwable) : QueryState<Nothing>
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

    @Synchronized fun submit(parameters: P): Job? {
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
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (cancelled: ProcessCanceledException) { throw cancelled }
            catch (failure: Throwable) {
                synchronized(this@LatestQuery) {
                    if (!closed && generation == id) status.value = QueryState.Failure(failure)
                }
            }
        }.also { job = it }
    }
    @Synchronized fun cancel() {
        generation++; job?.cancel(); job = null; status.value = QueryState.Idle
    }
    @Synchronized override fun close() {
        closed = true
        cancel()
    }
}
