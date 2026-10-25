package com.wenjunhuang.algostack.domain

import org.junit.Assert.*
import org.junit.Test
import java.net.HttpCookie

class CodeDojoTest {
    @Test fun persistedSiteIdentifiersRemainStable() {
        assertEquals(setOf("leetcodecn"), CodeDojo.entries.map { it.value }.toSet())
        assertEquals(CodeDojo.LeetCodeCN, CodeDojo.fromString("LEETCODECN"))
        assertNull(CodeDojo.fromString("unsupported-site"))
    }
    @Test fun cookieHostsResolveToTheCorrectSite() {
        assertNull(CodeDojo.fromString("leetcode"))
        assertNull(CodeDojo.fromHostname(".leetcode.com"))
        assertEquals(CodeDojo.LeetCodeCN, CodeDojo.fromHostname("WWW.LEETCODE.CN"))
        assertNull(CodeDojo.fromHostname("leetcode.com.example.org"))
        assertNull(CodeDojo.fromHostname("notleetcode.cn"))
    }
    @Test fun loginRequiresANonEmptySessionCookie() {
        for (dojo in CodeDojo.entries) {
            assertFalse(com.wenjunhuang.algostack.runtime.CookieStore.hasSession(listOf(HttpCookie("LEETCODE_SESSION", ""))))
            assertFalse(com.wenjunhuang.algostack.runtime.CookieStore.hasSession(listOf(HttpCookie("csrftoken", "token"))))
            assertTrue(com.wenjunhuang.algostack.runtime.CookieStore.hasSession(listOf(HttpCookie("LEETCODE_SESSION", "session"))))
        }
    }
}
