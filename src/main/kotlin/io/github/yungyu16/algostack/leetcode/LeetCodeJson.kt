package io.github.yungyu16.algostack.leetcode

import kotlinx.serialization.json.Json

/** Match the existing protocol: accept extra response fields, retain explicit null/default request fields. */
object LeetCodeJson {
    val format = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        explicitNulls = true
    }
}
