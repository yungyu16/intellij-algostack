package com.wenjunhuang.codeepiphany.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.ceil
import kotlin.math.max

@Serializable
data class Pagination(val currentPage: Int = 1, val pageSize: PageSize = PageSize.Twenty, val totalSize: Int = 0) {
    init { require(currentPage > 0) { "Current page must be greater than 0" } }
    val totalPages: Int get() = max(1, ceil(totalSize.toDouble() / pageSize.value).toInt())
    val offset: Int get() = pageSize.value * (currentPage - 1)
    val limit: Int get() = pageSize.value
    fun resetToFirstPage() = copy(currentPage = 1)
}
@Serializable(with = PageSizeSerializer::class)
enum class PageSize(val value: Int) {
    Twenty(20), Fifty(50), OneHundred(100);
}
object PageSizeSerializer : KSerializer<PageSize> {
    override val descriptor = PrimitiveSerialDescriptor("PageSize", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: PageSize) = encoder.encodeInt(value.value)
    override fun deserialize(decoder: Decoder): PageSize {
        val value = decoder.decodeInt()
        return PageSize.entries.find { it.value == value } ?: throw SerializationException("Unknown page size: $value")
    }
}
