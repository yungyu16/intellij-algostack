package io.github.yungyu16.algostack.presentation.browser

import io.github.yungyu16.algostack.presentation.browser.WebDocumentNavigation.Action
import org.junit.Assert.*
import org.junit.Test

class WebDocumentNavigationTest {
    private val bootstrap = "http://127.0.0.1:12345/leetCodeSolutionArticle/index.html?reload=42"
    private fun policy(article: Boolean) = WebDocumentNavigation(article).apply { bootstrapUrl = bootstrap }

    @Test
    fun articleLinksNeverLeaveThePanel() {
        val article = policy(true)
        val description = policy(false)
        for (url in listOf(
            "https://leetcode.cn/u/author/", "https://example.com/solution",
            "file:///tmp/solution.java", "mailto:author@example.com", "javascript:window.open('x')",
            "about:blank", "not a valid URI"
        )) {
            assertEquals(url, Action.Block, article.beforeBrowse(url, true, true, false))
            assertEquals(url, Action.OpenExternally, description.beforeBrowse(url, true, true, false))
            // 无手势脚本导航和重定向同样不能替换题解正文。
            assertEquals(url, Action.Block, article.beforeBrowse(url, true, false, false))
            assertEquals(url, Action.Block, article.beforeBrowse(url, true, false, true))
            assertEquals(url, Action.Allow, description.beforeBrowse(url, true, false, true))
        }
    }

    @Test
    fun articleAllowsOnlyCurrentBootstrapAndItsFragments() {
        val article = policy(true)
        assertEquals(Action.Allow, article.beforeBrowse(bootstrap, true, false, false))
        assertEquals(Action.Allow, article.beforeBrowse("$bootstrap#section-1", true, true, false))
        assertEquals(Action.Allow, article.beforeBrowse("$bootstrap#section-2", true, false, false))
        assertEquals(Action.Allow, article.beforeBrowse("$bootstrap#", true, true, false))
        assertEquals(Action.Block, article.beforeBrowse(bootstrap, true, true, false))
        assertEquals(Action.Block, article.beforeBrowse(bootstrap, true, false, true))
        for (url in listOf(
            bootstrap.replace("42", "41"), bootstrap.substringBefore('?'),
            bootstrap.replace("12345", "54321"), "$bootstrap/other#section",
            "https://example.com/#section"
        )) {
            assertEquals(url, Action.Block, article.beforeBrowse(url, true, false, false))
        }
        article.bootstrapUrl = bootstrap.replace("42", "43")
        assertEquals(Action.Block, article.beforeBrowse(bootstrap, true, false, false))
        assertEquals(Action.Block, article.beforeBrowse("$bootstrap#old", true, true, false))
        assertEquals(Action.Allow, article.beforeBrowse(article.bootstrapUrl, true, false, false))
        assertEquals(Action.Block, WebDocumentNavigation(true).beforeBrowse("", true, false, false))
    }

    @Test
    fun embeddedFramesCanLoadButArticleClicksCannotNavigateThem() {
        val url = "https://www.leetcode.cn/embed/"
        assertEquals(Action.Allow, policy(true).beforeBrowse(url, false, false, false))
        assertEquals(Action.Block, policy(true).beforeBrowse(url, false, true, false))
        assertEquals(Action.Allow, policy(false).beforeBrowse(url, false, true, false))
    }

    @Test
    fun newWindowsAreBlockedOnlyForArticles() {
        // onBeforePopup（含 target=_blank/window.open）和 onOpenURLFromTab 共用此策略。
        assertTrue(policy(true).blockNewWindow)
        assertFalse(policy(false).blockNewWindow)
    }

    @Test
    fun descriptionKeepsExistingGestureAndRedirectHandling() {
        val description = policy(false)
        assertEquals(Action.OpenExternally, description.beforeBrowse("$bootstrap#section", true, true, false))
        assertEquals(Action.Allow, description.beforeBrowse(bootstrap, true, false, false))
        assertEquals(Action.Allow, description.beforeBrowse("https://example.com", true, true, true))
        assertEquals(Action.Allow, description.beforeBrowse("https://example.com", false, true, false))
    }
}
