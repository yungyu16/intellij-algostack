package io.github.yungyu16.algostack.domain

enum class ChallengeDifficulty(val value: String) {
    Easy("easy"), Medium("medium"), Hard("hard");

    val apiValue: String
        get() = when (this) {
            Easy -> "EASY"
            Medium -> "MEDIUM"
            Hard -> "HARD"
        }

    companion object {
        @JvmStatic
        fun fromString(value: String?): ChallengeDifficulty? = entries.find { it.value.equals(value, true) }
        fun fromApi(value: String): ChallengeDifficulty = when (value.lowercase()) {
            "easy" -> Easy
            "medium" -> Medium
            "hard" -> Hard
            else -> throw IllegalArgumentException("Unknown difficulty: $value")
        }
    }
}
