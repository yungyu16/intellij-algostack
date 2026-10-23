package com.wenjunhuang.codeepiphany.presentation.browser

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class WebResourcesTest {
    private fun hash(script: String) = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(script.toByteArray()))
    @Test fun onlyTrustedTemplateScriptsEnterTheContentPolicy() {
        val template = "<script>window.render = true;</script><div><!--descriptionContent--></div>"
        val injected = "<script>window.untrusted = true;</script><img onerror=\"window.untrusted = true;\">"
        val page = WebResources.descriptionPage(template, injected)
        assertTrue(page.bytes.toString(Charsets.UTF_8).contains(injected))
        assertTrue(page.policy!!.contains(hash("window.render = true;")))
        assertFalse(page.policy.contains(hash("window.untrusted = true;")))
        assertFalse(page.policy.substringBefore("style-src").contains("'unsafe-inline'"))
    }
    @Test fun articleContentsCannotEscapeTheTrustedBootstrapScript() {
        val page = WebResources.articlePage("<script>const content = '{{articleContent}}'; const site = '{{codeDojo}}';</script>",
            "</script><script>evil()</script>", "leetcodecn")
        assertFalse(page.bytes.toString(Charsets.UTF_8).contains("evil()"))
        assertFalse(page.policy!!.contains(hash("evil()")))
    }
    @Test fun resourcesAndRemoteProxyStayInsideTheirBoundaries() {
        assertTrue(WebResources.validResourcePath("assets/KaTeX_Main-Regular.woff2"))
        assertFalse(WebResources.validResourcePath("../META-INF/plugin.xml"))
        assertFalse(WebResources.validResourcePath("assets/../../secret"))
        assertFalse(WebResources.validResourcePath("assets\\..\\secret"))
        assertTrue(WebResources.trustedFrame("https://leetcode.com/graphql/"))
        assertTrue(WebResources.trustedFrame("https://www.leetcode.cn/path"))
        assertFalse(WebResources.trustedFrame("http://leetcode.com/path"))
        assertFalse(WebResources.trustedFrame("https://leetcode.com.evil.example/path"))
        assertFalse(WebResources.trustedFrame("https://leetcode.com@evil.example/path"))
        assertFalse(WebResources.trustedFrame("https://leetcode.com:8443/path"))
    }
}
