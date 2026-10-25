package io.github.yungyu16.algostack.leetcode.api.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class LeetCodeRunRequest(
    val lang: String,
    @SerialName("data_input") val dataInput: String,
    @SerialName("question_id") val questionId: String,
    @SerialName("typed_code") val typedCode: String
)

@Serializable
data class LeetCodeRunResponse(
    @SerialName("interpret_id") val interpretId: String,
    @SerialName("test_case") val testCase: String,
    @SerialName("interpret_expected_id") val interpretExpectedId: String? = null
)

@Serializable(with = LeetCodeRunResultSerializer::class)
sealed interface LeetCodeRunResult {
    @Serializable
    data class Started(
        val state: String
    ) : LeetCodeRunResult

    @Serializable
    data class Pending(
        val state: String
    ) : LeetCodeRunResult

    @Serializable
    data class Success(
        val state: String,
        @SerialName("status_code") val statusCode: Int,
        @SerialName("run_success") val runSuccess: Boolean,
        @SerialName("correct_answer") val correctAnswer: Boolean? = null,
        @SerialName("compare_result") val compareResult: String? = null,
        @SerialName("compile_error") val compileError: String? = null,
        @SerialName("full_compile_error") val fullCompileError: String? = null,
        @SerialName("runtime_error") val runtimeError: String? = null,
        @SerialName("full_runtime_error") val fullRuntimeError: String? = null,
        @SerialName("status_runtime") val statusRuntime: String? = null,
        val memory: Int? = null,
        @SerialName("code_answer") val codeAnswer: List<String> = emptyList(),
        @SerialName("expected_code_answer") val expectedCodeAnswer: List<String> = emptyList(),
        @SerialName("code_output") val codeOutput: List<String> = emptyList(),
        @SerialName("expected_code_output") val expectedCodeOutput: List<String> = emptyList(),
        @SerialName("std_output_list") val stdOutputList: List<String> = emptyList(),
        @SerialName("expected_std_output_list") val expectedStdOutputList: List<String> = emptyList(),
        @SerialName("task_finish_time") val taskFinishTime: Long,
        @SerialName("task_name") val taskName: String,
        @SerialName("status_msg") val statusMsg: String,
        @SerialName("fast_submit") val fastSubmit: Boolean? = null,
        @SerialName("total_correct") val totalCorrect: Int? = null,
        @SerialName("total_testcases") val totalTestcases: Int? = null,
        @SerialName("submission_id") val submissionId: String,
        @SerialName("runtime_percentile") val runtimePercentile: Double? = null,
        @SerialName("status_memory") val statusMemory: String,
        @SerialName("memory_percentile") val memoryPercentile: Double? = null,
        @SerialName("pretty_lang") val prettyLang: String
    ) : LeetCodeRunResult

}

object LeetCodeRunResultSerializer : kotlinx.serialization.json.JsonContentPolymorphicSerializer<LeetCodeRunResult>(LeetCodeRunResult::class) {
    override fun selectDeserializer(element: kotlinx.serialization.json.JsonElement): kotlinx.serialization.DeserializationStrategy<LeetCodeRunResult> =
        when (element.jsonObject["state"]?.jsonPrimitive?.content) {
            "STARTED" -> LeetCodeRunResult.Started.serializer()
            "PENDING" -> LeetCodeRunResult.Pending.serializer()
            "SUCCESS" -> LeetCodeRunResult.Success.serializer()
            else -> throw kotlinx.serialization.SerializationException("Unknown result state")
        }
}
