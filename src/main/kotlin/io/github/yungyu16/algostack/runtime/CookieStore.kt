package io.github.yungyu16.algostack.runtime

import io.github.yungyu16.algostack.domain.CodeDojo
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.net.HttpCookie

class CookieStore(private val changed: (CodeDojo, List<HttpCookie>) -> Unit = { _, _ -> }) : CookieJar {
    private val lock = Any()
    private val cookieMap = mutableMapOf<CodeDojo, MutableMap<String, HttpCookie>>()
    private val initialized = mutableSetOf<CodeDojo>()
    private fun copy(cookie: HttpCookie) = cookie.clone() as HttpCookie
    fun restore(dojo: CodeDojo, values: List<HttpCookie>) = synchronized(lock) {
        initialized += dojo
        cookieMap[dojo] = values.associateBy({ it.name.lowercase() }, ::copy).toMutableMap()
    }

    fun get(dojo: CodeDojo): List<HttpCookie> = synchronized(lock) {
        cookieMap[dojo]?.values?.filterNot { it.hasExpired() }?.map(::copy).orEmpty()
    }

    fun clear(dojo: CodeDojo) {
        synchronized(lock) { initialized += dojo; cookieMap.remove(dojo) }
        changed(dojo, emptyList())
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val dojo = CodeDojo.fromHostname(url.host) ?: return emptyList()
        return get(dojo).mapNotNull { cookie ->
            runCatching {
                Cookie.Builder().name(cookie.name).value(cookie.value).domain(dojo.domain).path(cookie.path ?: "/")
                    .apply { if (cookie.secure) secure(); if (cookie.isHttpOnly) httpOnly() }.build()
            }.getOrNull()
        }.filter { it.matches(url) }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val dojo = CodeDojo.fromHostname(url.host) ?: return
        val snapshot = synchronized(lock) {
            val stored = cookieMap.getOrPut(dojo) { linkedMapOf() }
            for (value in cookies) {
                if (CodeDojo.fromHostname(value.domain) != dojo) continue
                val key = value.name.lowercase()
                if (value.expiresAt <= System.currentTimeMillis()) stored.remove(key)
                else stored[key] = HttpCookie(value.name, value.value).apply {
                    domain = value.domain; path = value.path; secure = value.secure; isHttpOnly = value.httpOnly
                    if (value.expiresAt != Long.MAX_VALUE) maxAge =
                        ((value.expiresAt - System.currentTimeMillis()) / 1_000).coerceAtLeast(1)
                }
            }
            stored.values.filterNot { it.hasExpired() }.map(::copy) to (dojo in initialized)
        }
        // Anonymous requests must not overwrite a saved session before authentication restores it.
        if (snapshot.second) changed(dojo, snapshot.first)
    }

    companion object {
        fun hasSession(values: List<HttpCookie>): Boolean =
            values.any { it.name == "LEETCODE_SESSION" && it.value.isNotEmpty() }

        fun parse(value: String): List<HttpCookie> = value.split(';').mapNotNull { part ->
            val item = part.trim();
            val split = item.indexOf('=')
            if (split <= 0) null else HttpCookie(item.substring(0, split), item.substring(split + 1))
        }

        fun encode(values: List<HttpCookie>): String = values.joinToString(";") { "${it.name}=${it.value}" }
    }
}
