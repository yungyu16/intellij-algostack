package io.github.yungyu16.algostack.leetcode.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class LeetCodeSubmitAnswerRequest(
    val lang: String,
    @SerialName("question_id") val questionId: String,
    @SerialName("typed_code") val typedCode: String
)

@Serializable
data class LeetCodeSubmitAnswerResponse(
    @SerialName("submission_id") val submissionId: Int
)

@Serializable(with = LeetCodeSubmitAnswerResultSerializer::class)
sealed interface LeetCodeSubmitAnswerResult {
    /** 本地受理事件：来自提交 POST 响应，不表示平台判题已开始。 */
    data class Accepted(val submissionId: String) : LeetCodeSubmitAnswerResult

    @Serializable
    data class Started(
        @Transient val leetCodeSubmissionId: String = "",
        val state: String
    ) : LeetCodeSubmitAnswerResult

    @Serializable
    data class Pending(
        @Transient val leetCodeSubmissionId: String = "",
        val state: String
    ) : LeetCodeSubmitAnswerResult

    @Serializable
    data class Success(
        @SerialName("status_code") val statusCode: Int,
        val lang: String,
        @SerialName("run_success") val runSuccess: Boolean,
        @SerialName("compile_error") val compileError: String? = null,
        @SerialName("full_compile_error") val fullCompileError: String? = null,
        @SerialName("runtime_error") val runtimeError: String? = null,
        @SerialName("full_runtime_error") val fullRuntimeError: String? = null,
        @SerialName("status_runtime") val statusRuntime: String,
        val memory: Int,
        @SerialName("display_runtime") val displayRuntime: String? = null,
        @SerialName("question_id") val questionId: String,
        @SerialName("elasped_time") val elaspedTime: Long? = null,
        @SerialName("compare_result") val compareResult: String? = null,
        @SerialName("code_output") val codeOutput: String? = null,
        @SerialName("std_output") val stdOutput: String? = null,
        @SerialName("last_testcase") val lastTestcase: String? = null,
        @SerialName("expected_output") val expectedOutput: String? = null,
        @SerialName("task_finish_time") val taskFinishTime: Long,
        @SerialName("task_name") val taskName: String,
        val finished: Boolean,
        @SerialName("status_msg") val statusMsg: String,
        val state: String,
        @SerialName("fast_submit") val fastSubmit: Boolean? = null,
        @SerialName("total_correct") val totalCorrect: Int? = null,
        @SerialName("total_testcases") val totalTestcases: Int? = null,
        @SerialName("submission_id") val submissionId: String,
        @SerialName("runtime_percentile") val runtimePercentile: Float? = null,
        @SerialName("status_memory") val statusMemory: String,
        @SerialName("memory_percentile") val memoryPercentile: Float? = null,
        @SerialName("pretty_lang") val prettyLang: String,
        @SerialName("input_formatted") val inputFormatted: String? = null,
        val input: String? = null
    ) : LeetCodeSubmitAnswerResult

}

object LeetCodeSubmitAnswerResultSerializer :
    kotlinx.serialization.json.JsonContentPolymorphicSerializer<LeetCodeSubmitAnswerResult>(LeetCodeSubmitAnswerResult::class) {
    override fun selectDeserializer(element: kotlinx.serialization.json.JsonElement): kotlinx.serialization.DeserializationStrategy<LeetCodeSubmitAnswerResult> =
        when (element.jsonObject["state"]?.jsonPrimitive?.content) {
            "STARTED" -> LeetCodeSubmitAnswerResult.Started.serializer()
            "PENDING" -> LeetCodeSubmitAnswerResult.Pending.serializer()
            "SUCCESS" -> LeetCodeSubmitAnswerResult.Success.serializer()
            else -> throw kotlinx.serialization.SerializationException("Unknown result state")
        }
}
