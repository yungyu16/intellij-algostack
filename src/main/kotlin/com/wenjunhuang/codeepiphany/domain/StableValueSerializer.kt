package com.wenjunhuang.codeepiphany.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Locale

/** Persisted identifiers are wire values, independent of enum names or ordinals. */
abstract class StableValueSerializer<T>(name: String, values: Collection<T>, private val valueOf: (T) -> String) : KSerializer<T> {
    private val valuesById = values.associateBy { valueOf(it).lowercase(Locale.ROOT) }
    override val descriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(valueOf(value))
    override fun deserialize(decoder: Decoder): T {
        val id = decoder.decodeString()
        return valuesById[id.lowercase(Locale.ROOT)] ?: throw SerializationException("Unknown ${descriptor.serialName}: $id")
    }
}
