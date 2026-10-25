package com.wenjunhuang.algostack.domain

import kotlinx.serialization.Serializable

@Serializable(with = ChallengeStatusSerializer::class)
enum class ChallengeStatus(val value: String) {
    Solved("solved"), Unsolved("unsolved"), Tried("tried");
    val apiValue: String get() = when (this) { Solved -> "AC"; Unsolved -> "NOT_STARTED"; Tried -> "TRIED" }
    companion object {
        @JvmStatic fun fromString(value: String?): ChallengeStatus? = entries.find { it.value.equals(value, true) }
        fun fromApi(value: String?): ChallengeStatus = when (value?.uppercase()) {
            "AC", "SOLVED" -> Solved
            "TRIED", "NOTAC", "ATTEMPTED" -> Tried
            else -> Unsolved
        }
    }
}
object ChallengeStatusSerializer : StableValueSerializer<ChallengeStatus>("ChallengeStatus", ChallengeStatus.entries, { it.value })
