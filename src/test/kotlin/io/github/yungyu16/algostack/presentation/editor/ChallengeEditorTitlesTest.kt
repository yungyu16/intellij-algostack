package io.github.yungyu16.algostack.presentation.editor

import com.intellij.testFramework.LightVirtualFile
import io.github.yungyu16.algostack.domain.ChallengeIdentity
import io.github.yungyu16.algostack.domain.CodeDojo
import io.github.yungyu16.algostack.domain.Language
import io.github.yungyu16.algostack.utils.ChallengeFiles
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ChallengeEditorTitlesTest {
    private val first = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", Language.Java)
    private val second = first.copy(slug = "three-sum")
    private val file = LightVirtualFile("unrelated.java")
    private val titles = OpenFileTitles()
    private val requests = mutableListOf<OpenFileTitles.Request>()
    private fun title(identity: ChallengeIdentity? = first): String? = titles.title(file, identity) {
        requests.add(it)
        Job()
    }

    @Test
    fun translatedTitleFallsBackToEnglishAndNeverDisplaysAnEmptyTitle() {
        assertEquals("两数之和", challengeTabTitle(" 两数之和 ", "Two Sum"))
        assertEquals("Two Sum", challengeTabTitle(null, "Two Sum"))
        assertEquals("Two Sum", challengeTabTitle("  ", "Two Sum"))
        assertNull(challengeTabTitle(null, "  "))
        assertNull(challengeTabTitle(" ", ""))
    }

    @Test
    fun pendingAndCompletedCallbacksReuseOneRequest() {
        repeat(20) { assertNull(title()) }
        assertEquals(1, requests.size)
        assertTrue(titles.complete(file, requests.single(), first, "两数之和"))
        repeat(20) { assertEquals("两数之和", title()) }
        assertEquals(1, requests.size)
    }

    @Test
    fun failureOrMissingTitleDoesNotRefreshOrRetryOnEveryPresentationCallback() {
        title()
        assertFalse(titles.complete(file, requests.single(), first, null))
        repeat(20) { assertNull(title()) }
        assertEquals(1, requests.size)
        titles.close(file)
        title()
        assertEquals(2, requests.size)
    }

    @Test
    fun observationRefreshesOnlyForIdentityChangesAndRetainsCompletedAndFailedRequests() {
        fun observe(identity: ChallengeIdentity?) = titles.observe(file, identity) { requests.add(it); Job() }
        repeat(20) { assertFalse(observe(null)) }
        assertTrue(requests.isEmpty())
        assertTrue(observe(first))
        repeat(20) { assertFalse(observe(first)) }
        assertEquals(1, requests.size)
        assertTrue(titles.complete(file, requests.single(), first, "两数之和"))
        assertFalse(titles.complete(file, requests.single(), first, "两数之和"))
        repeat(20) { assertFalse(observe(first)) }
        assertEquals("两数之和", title())
        assertTrue(observe(second))
        assertFalse(titles.complete(file, requests.last(), second, null))
        repeat(20) { assertFalse(observe(second)) }
        assertEquals(2, requests.size)
        assertTrue(observe(null))
        repeat(20) { assertFalse(observe(null)) }
        assertTrue(requests.last().job!!.isCancelled)
    }

    @Test
    fun synchronousObservationsIsolateRoundTripEvenWithoutPresentationCallbacks() {
        title()
        val old = requests.single()
        assertTrue(titles.observe(file, second) { requests.add(it); Job() })
        val middle = requests.last()
        assertTrue(old.job!!.isCancelled)
        assertTrue(titles.observe(file, first) { requests.add(it); Job() })
        assertTrue(middle.job!!.isCancelled)
        assertFalse(titles.complete(file, old, first, "旧标题"))
        assertFalse(titles.complete(file, middle, first, "三数之和"))
        assertTrue(titles.complete(file, requests.last(), first, "两数之和"))
        assertEquals("两数之和", title())
        assertEquals(3, requests.size)
    }

    @Test
    fun switchingIdentityCancelsOldRequestAndRejectsLateResponsesEvenAfterSwitchingBack() {
        title()
        val old = requests.last()
        title(second)
        assertTrue(old.job!!.isCancelled)
        val middle = requests.last()
        title(first)
        assertFalse(titles.complete(file, old, first, "旧标题"))
        assertFalse(titles.complete(file, middle, first, "三数之和"))
        assertNull(title())
        assertTrue(titles.complete(file, requests.last(), first, "两数之和"))
        assertEquals("两数之和", title())
    }

    @Test
    fun responseRechecksSourceIdentityBeforePublishingEvenWithoutATitleCallback() {
        title()
        assertFalse(titles.complete(file, requests.single(), second, "两数之和"))
        assertTrue(requests.single().job!!.isCancelled)
        assertNull(title(second))
        assertEquals(2, requests.size)
    }

    @Test
    fun losingIdentityClearsPreviouslyLoadedTitle() {
        title()
        titles.complete(file, requests.single(), first, "两数之和")
        assertNull(title(null))
        assertNull(title())
        assertEquals(2, requests.size)
    }

    @Test
    fun closeAndReopenRejectsOldResponseAndDisposeCancelsAllWork() {
        title()
        val old = requests.single()
        titles.close(file)
        assertTrue(old.job!!.isCancelled)
        assertFalse(titles.complete(file, old, null, "旧标题"))
        title()
        assertFalse(titles.complete(file, old, first, "旧标题"))
        val current = requests.last()
        titles.dispose()
        assertTrue(current.job!!.isCancelled)
        assertFalse(titles.complete(file, current, first, "两数之和"))
        assertNull(title())
        assertEquals(2, requests.size)
    }

    @Test
    fun concurrentTitleReadsStartOnlyOneRequest() {
        val executor = Executors.newFixedThreadPool(8)
        try {
            val futures = (1..100).map { executor.submit<String?> { title() } }
            futures.forEach { assertNull(it.get(5, TimeUnit.SECONDS)) }
            assertEquals(1, requests.size)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun oldJavaAndGoFilesUseSourceIdentityRegardlessOfFilenameOrCommentPosition() {
        for (language in listOf(Language.Java, Language.GO)) {
            for (prefix in listOf(
                "",
                if (language == Language.Java) "package old;\nclass Old {}\n" else "package old\nfunc old() {}\n"
            )) {
                val source = prefix + language.encloseCodeInRegion("// old solution") +
                    "\n// https://leetcode.cn/problems/two-sum/\n"
                val identity = ChallengeFiles.resolve("unrelated.${language.fileExt}", source)
                assertEquals(first.copy(language = language), identity)
                val oldFile = LightVirtualFile("unrelated.${language.fileExt}")
                lateinit var request: OpenFileTitles.Request
                assertNull(titles.title(oldFile, identity) { request = it; Job() })
                assertTrue(titles.complete(oldFile, request, identity, challengeTabTitle("两数之和", "Two Sum")))
                assertEquals("两数之和", titles.title(oldFile, identity) { error("Must not reload") })
            }
        }
    }
}
