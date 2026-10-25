package io.github.yungyu16.algostack.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ChallengeDifficultySerializer::class)
sealed class ChallengeDifficulty(val value: String) {
    data object Easy : ChallengeDifficulty("easy")
    data object Medium : ChallengeDifficulty("medium")
    data object Hard : ChallengeDifficulty("hard")
    data object Advanced : ChallengeDifficulty("advanced")
    data object Expert : ChallengeDifficulty("expert")
    data class CodeDojoDefined(val dojo: CodeDojo, val dojoValue: String) : ChallengeDifficulty("${dojo.value}#$dojoValue")

    val apiValue: String get() = when (this) { Easy -> "EASY"; Medium -> "MEDIUM"; else -> "HARD" }
    companion object {
        @JvmStatic fun fromString(value: String?): ChallengeDifficulty? {
            listOf(Easy, Medium, Hard, Advanced, Expert).find { it.value.equals(value, true) }?.let { return it }
            val parts = value?.split('#', limit = 2) ?: return null
            if (parts.size != 2 || parts[1].isEmpty()) return null
            return CodeDojo.fromString(parts[0])?.let { CodeDojoDefined(it, parts[1]) }
        }
        fun fromApi(value: String): ChallengeDifficulty = when (value.lowercase()) {
            "easy" -> Easy; "medium" -> Medium; "hard" -> Hard
            else -> throw IllegalArgumentException("Unknown difficulty: $value")
        }
    }
}
object ChallengeDifficultySerializer : KSerializer<ChallengeDifficulty> {
    override val descriptor = PrimitiveSerialDescriptor("ChallengeDifficulty", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ChallengeDifficulty) = encoder.encodeString(value.value)
    override fun deserialize(decoder: Decoder): ChallengeDifficulty {
        val value = decoder.decodeString()
        return ChallengeDifficulty.fromString(value) ?: throw SerializationException("Unknown difficulty: $value")
    }
}
