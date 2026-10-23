package com.wenjunhuang.codeepiphany.runtime

/** 同一浏览器凭据最多验证三次；只有异常可重试，无效凭据等待 Cookie 改变。 */
internal class AuthenticationRetry(private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private var candidate: String? = null
    private var attempts = 0
    private var nextAttempt = Long.MAX_VALUE

    fun begin(value: String): Boolean {
        if (value != candidate) {
            candidate = value
            attempts = 0
            nextAttempt = 0
        }
        if (attempts >= 3 || now() < nextAttempt) return false
        attempts++
        nextAttempt = Long.MAX_VALUE
        return true
    }

    fun failed() {
        if (attempts < 3) nextAttempt = now() + if (attempts == 1) 2_000 else 5_000
    }
}
