package io.github.yungyu16.algostack.domain

import kotlinx.serialization.Serializable

@Serializable(with = CodeDojoSerializer::class)
enum class CodeDojo(val domain: String, val value: String) {
    LeetCodeCN("leetcode.cn", "leetcodecn");

    val loginUrl: String get() = "https://$domain/accounts/login/"

    companion object {
        @JvmStatic
        fun fromString(value: String?): CodeDojo? = entries.find { it.value.equals(value, true) }
        @JvmStatic
        fun fromHostname(host: String): CodeDojo? = entries.find {
            host.equals(it.domain, true) || host.endsWith(".${it.domain}", true)
        }
    }
}

object CodeDojoSerializer : StableValueSerializer<CodeDojo>("CodeDojo", CodeDojo.entries, { it.value })
