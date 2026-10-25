package io.github.yungyu16.algostack.execution

import com.intellij.testFramework.TestApplicationManager
import io.github.yungyu16.algostack.domain.CodeDojo
import io.github.yungyu16.algostack.presentation.show
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

class ExecutionMessageTest {
    companion object {
        @BeforeClass @JvmStatic fun initializeApplication() { TestApplicationManager.getInstance() }
    }

    @Test fun progressAndConsoleStartMessageHasAllRequiredArguments() {
        val dojo = CodeDojo.LeetCodeCN
        for (submit in listOf(false, true)) {
            val message = executionStartMessage(submit, "Solution.java", dojo)
            assertTrue(message.contains("Solution.java"))
            if (submit) assertTrue(message.contains(dojo.show))
            assertFalse("未替换的消息参数：$message", Regex("\\{\\d+}").containsMatchIn(message))
        }
    }
}
