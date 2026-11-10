package io.github.yungyu16.algostack.execution

import org.junit.Assert.assertEquals
import org.junit.Test

class ChallengeNamesTest {
    @Test
    fun challengeFileName() {
        assertEquals("P0001_TwoSum", ChallengeNames.challengeFileName("1", "two-sum"))
        assertEquals("P12345_TwoSum", ChallengeNames.challengeFileName("12345", "two-sum"))
        // 非数字题号沿用 slug 类名，避免生成非法标识符。
        assertEquals("TwoSum", ChallengeNames.challengeFileName("Interview 4", "two-sum"))
    }

    @Test
    fun snakeCaseName() {
        assertEquals(
            "hello_there_my_good_friend",
            ChallengeNames.snakeCaseName("hello there my_good-friend")
        )
        assertEquals("", ChallengeNames.snakeCaseName(null))
    }

    @Test
    fun challengeDirectoryName() {
        assertEquals("p0001_two_sum", ChallengeNames.challengeDirectoryName("1", "two-sum"))
        assertEquals("p12345_two_sum", ChallengeNames.challengeDirectoryName("12345", "two-sum"))
        assertEquals("two_sum", ChallengeNames.challengeDirectoryName("Interview 4", "two-sum"))
    }
}
