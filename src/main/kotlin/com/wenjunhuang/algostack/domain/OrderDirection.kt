package com.wenjunhuang.algostack.domain

import kotlinx.serialization.Serializable

@Serializable(with = OrderDirectionSerializer::class)
enum class OrderDirection {
    Ascending, Descending;
    val apiValue: String get() = if (this == Ascending) "ASCENDING" else "DESCENDING"
    companion object {
        fun fromString(value: String?): OrderDirection? = entries.find { it.name.equals(value, true) }
    }
}
object OrderDirectionSerializer : StableValueSerializer<OrderDirection>("OrderDirection", OrderDirection.entries, { it.name })
