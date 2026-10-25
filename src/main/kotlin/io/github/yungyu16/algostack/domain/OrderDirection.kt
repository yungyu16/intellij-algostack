package io.github.yungyu16.algostack.domain

enum class OrderDirection {
    Ascending, Descending;

    val apiValue: String get() = if (this == Ascending) "ASCENDING" else "DESCENDING"

    companion object {
        fun fromString(value: String?): OrderDirection? = entries.find { it.name.equals(value, true) }
    }
}
