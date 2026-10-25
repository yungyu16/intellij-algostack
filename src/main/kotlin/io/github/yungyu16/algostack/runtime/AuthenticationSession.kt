package io.github.yungyu16.algostack.runtime

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 每个站点独立协调；网络验证不持锁，只有短凭据提交不可取消。 */
internal class AuthenticationSession<T>(
    private val read: suspend () -> String?,
    private val validate: suspend (String) -> T?,
    private val persist: suspend (T?) -> Unit,
    private val publish: (T?) -> Unit,
    private val authenticated: () -> Boolean,
    private val restoreFailed: (Exception) -> Unit
) {
    private val mutex = Mutex()
    private val entry = Mutex()

    // entry 锁内读写：启动和多个视图共享一次静默尝试，失效/缺失凭据不反复验证。
    private var silentRestoreAttempted = false
    private var generation = 0L
    private var validations = 0
    @Volatile
    var revision = 0L
        private set

    suspend fun restore(): Boolean {
        if (authenticated()) return true
        val ticket = mutex.withLock {
            // 静默恢复不能抢占正在验证的手动登录。
            if (validations > 0) return false
            generation
        }
        val saved = read() ?: return false
        val candidate = validate(saved) ?: return false
        return commit(ticket, candidate)
    }

    suspend fun accept(value: String): Boolean = accept { validate(value) }

    suspend fun accept(validation: suspend () -> T?): Boolean {
        val ticket = mutex.withLock { validations++; ++generation }
        try {
            val candidate = validation() ?: return false
            return commit(ticket, candidate)
        } finally {
            withContext(NonCancellable) { mutex.withLock { validations-- } }
        }
    }

    suspend fun logout() {
        val ticket = mutex.withLock { ++generation }
        commit(ticket, null)
    }

    private suspend fun commit(ticket: Long, candidate: T?): Boolean {
        val committed = mutex.withLock {
            currentCoroutineContext().ensureActive()
            if (ticket != generation) return@withLock false
            withContext(NonCancellable) {
                persist(candidate)
                generation++
                revision++
                publish(candidate)
            }
            true
        }
        // 即使提交已经完成，调用方仍须收到取消，不能误报正常成功。
        currentCoroutineContext().ensureActive()
        return committed
    }

    suspend fun ensureAuthenticated(interactive: (suspend () -> Boolean)?): Boolean = entry.withLock {
        if (authenticated()) return@withLock true
        val restored = if (!silentRestoreAttempted || interactive != null) {
            try {
                restore().also { silentRestoreAttempted = true }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                silentRestoreAttempted = true; restoreFailed(failure); false
            }
        } else false
        if (restored || authenticated()) true else interactive?.invoke() ?: false
    }
}
