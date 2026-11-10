package io.github.yungyu16.algostack.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageContractTest {
    @Test
    fun submissionResultRoundTripsThroughWireValues() {
        for (result in SubmissionResult.entries) assertEquals(result, SubmissionResult.fromString(result.value))
    }

    @Test
    fun submissionRegionsKeepTheLineCommentSyntaxAndLineEndings() {
        val code = "first\r\nsecond\r\n"
        for (language in listOf(Language.Java, Language.GO)) {
            val source = language.encloseCodeInRegion(code)
            assertEquals(language.name, code + "\n", language.extractCodeFromRegion(source))
        }
        assertEquals("", Language.Java.extractCodeFromRegion("// no markers\nclass Solution {}"))
    }

    @Test
    fun leetCodeLanguageMappingsKeepPlatformSlugs() {
        assertEquals("java", Language.Java.toLeetCode())
        assertEquals("golang", Language.GO.toLeetCode())
        assertEquals(Language.Java, languageFromLeetCode("java"))
        assertEquals(Language.GO, languageFromLeetCode("golang"))
        assertEquals(null, languageFromLeetCode("python3"))
    }
}
