package com.wenjunhuang.codeepiphany.runtime

import com.wenjunhuang.codeepiphany.domain.CodeDojo
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpCookie

class CookieStoreTest {
    @Test fun accountCookiesStayIsolatedBySiteAndHostBoundary() {
        val store = CookieStore()
        store.restore(CodeDojo.LeetCodeCN, listOf(HttpCookie("LEETCODE_SESSION", "cn-session")))
        store.restore(CodeDojo.LeetCode, listOf(HttpCookie("LEETCODE_SESSION", "global-session")))
        assertEquals("cn-session", store.loadForRequest("https://leetcode.cn/graphql/".toHttpUrl()).single().value)
        assertEquals("global-session", store.loadForRequest("https://leetcode.com/graphql/".toHttpUrl()).single().value)
        assertTrue(store.loadForRequest("https://leetcode.cn.example.org/".toHttpUrl()).isEmpty())
        store.clear(CodeDojo.LeetCodeCN)
        assertEquals("global-session", store.get(CodeDojo.LeetCode).single().value)
    }
    @Test fun anonymousCsrfResponsesCannotEraseStoredCredentials() {
        val changes = mutableListOf<List<HttpCookie>>()
        val store = CookieStore { _, values -> changes += values }
        val url = "https://leetcode.cn/api/home/".toHttpUrl()
        val csrf = Cookie.Builder().name("csrftoken").value("csrf").domain("leetcode.cn").build()
        store.saveFromResponse(url, listOf(csrf))
        assertTrue(changes.isEmpty())
        store.restore(CodeDojo.LeetCodeCN, listOf(HttpCookie("LEETCODE_SESSION", "saved-session")))
        store.saveFromResponse(url, listOf(csrf))
        assertEquals(setOf("LEETCODE_SESSION", "csrftoken"), changes.single().map { it.name }.toSet())
    }
    @Test fun expiredSessionCookiesAreRemovedAndSnapshotsAreIndependent() {
        val store = CookieStore()
        store.restore(CodeDojo.LeetCodeCN, listOf(HttpCookie("LEETCODE_SESSION", "session")))
        store.get(CodeDojo.LeetCodeCN).single().value = "mutated"
        assertEquals("session", store.get(CodeDojo.LeetCodeCN).single().value)
        store.saveFromResponse("https://leetcode.cn/".toHttpUrl(), listOf(Cookie.Builder()
            .name("LEETCODE_SESSION").value("deleted").domain("leetcode.cn").expiresAt(0).build()))
        assertTrue(store.get(CodeDojo.LeetCodeCN).isEmpty())
    }
    @Test fun legacyCookieTextPreservesEqualsSignsInsideValues() {
        val text = "LEETCODE_SESSION=a=b==; csrftoken=csrf"
        assertEquals("LEETCODE_SESSION=a=b==;csrftoken=csrf", CookieStore.encode(CookieStore.parse(text)))
    }
}
