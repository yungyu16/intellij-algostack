package io.github.yungyu16.algostack.execution

import io.github.yungyu16.algostack.domain.ChallengeIdentity
import io.github.yungyu16.algostack.domain.CodeDojo
import io.github.yungyu16.algostack.domain.Language
import io.github.yungyu16.algostack.leetcode.model.LeetCodeChallengeData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExecutionRequestTest {
    private fun data(inputs: List<String>) = LeetCodeChallengeData(
        "9001", "1", "Two Sum", "two-sum", "",
        difficulty = "Easy",
        exampleTestcaseList = inputs,
        codeSnippets = emptyList()
    )

    @Test
    fun everyRequestUsesFreshPlatformExamplesAndKeepsMultiArgumentCaseBoundaries() {
        val examples = listOf("[2,7,11,15]\n9", "[3,2,4]\n6")
        for (language in listOf(Language.Java, Language.GO)) {
            val identity = ChallengeIdentity(CodeDojo.LeetCodeCN, "two-sum", language)
            val request = executionRequest(data(examples), identity)
            assertEquals("9001", request.questionId)
            assertEquals(examples, request.defaultCases)
            assertEquals("[2,7,11,15]\n9\n[3,2,4]\n6", request.testInput)
            assertEquals("[3,2,4]\n6", request.defaultCases[1])
            assertEquals(language, request.language)
            assertEquals("updated", executionRequest(data(listOf("updated")), identity).testInput)
            assertEquals("", executionRequest(data(emptyList()), identity).testInput)
        }
    }

    @Test
    fun mismatchedPlatformResponseIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            executionRequest(
                data(listOf("fixture")),
                ChallengeIdentity(CodeDojo.LeetCodeCN, "three-sum", Language.Java)
            )
        }
    }
}
