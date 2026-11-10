package io.github.yungyu16.algostack.domain

enum class SubmissionResult(val value: String) {
    Processing("processing"),
    Success("success"),
    Failure("failure"),
    CompilationError("compilationError"),
    Timeout("timeout"),
    MemoryLimitExceeded("memoryLimit"),
    OutputLimitExceeded("outputLimit"),
    RuntimeError("runtimeError"),
    InternalError("internalError"),
    Unknown("unknown");

    companion object {
        @JvmStatic
        fun fromString(value: String?): SubmissionResult? = entries.find { it.value.equals(value, true) }
        fun fromApi(message: String, correctAnswer: Boolean? = null): SubmissionResult = when {
            message.contains("Accepted", true) -> if (correctAnswer == false) Failure else Success
            message.contains("Compile Error", true) -> CompilationError
            message.contains("Runtime Error", true) -> RuntimeError
            message.contains("Time Limit Exceeded", true) -> Timeout
            message.contains("Wrong Answer", true) -> Failure
            else -> Unknown
        }
    }
}
