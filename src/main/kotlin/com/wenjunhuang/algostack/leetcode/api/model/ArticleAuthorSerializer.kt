package com.wenjunhuang.algostack.leetcode.api.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/** Both sites return avatar/name/slug either directly or wrapped in a same-named object. */
object ArticleAuthorSerializer : KSerializer<LeetCodeQuestionSolutionArticleAuthor> {
    @Serializable
    private data class Fields(
        val username: String,
        val certificationLevel: String,
        val userAvatar: String,
        val userSlug: String,
        val realName: String
    )

    override val descriptor: SerialDescriptor = Fields.serializer().descriptor

    override fun deserialize(decoder: Decoder): LeetCodeQuestionSolutionArticleAuthor {
        val input = decoder as? JsonDecoder ?: throw SerializationException("Expected JSON author")
        val fields = input.decodeJsonElement().jsonObject
        fun string(name: String, wrapped: Boolean = false): String {
            val value = fields[name]
            val primitive = if (wrapped && value is JsonObject) value[name] else value
            if (primitive !is JsonPrimitive || !primitive.isString) {
                throw SerializationException("Missing or invalid author field: $name")
            }
            return primitive.content
        }
        return LeetCodeQuestionSolutionArticleAuthor(
            string("username"), string("certificationLevel"), string("userAvatar", true),
            string("userSlug", true), string("realName", true)
        )
    }

    override fun serialize(encoder: Encoder, value: LeetCodeQuestionSolutionArticleAuthor) {
        encoder.encodeSerializableValue(Fields.serializer(), Fields(
            value.username, value.certificationLevel, value.userAvatar, value.userSlug, value.realName
        ))
    }
}
