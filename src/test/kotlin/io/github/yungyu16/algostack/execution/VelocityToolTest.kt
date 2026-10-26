package io.github.yungyu16.algostack.execution

import org.apache.velocity.VelocityContext
import org.apache.velocity.app.Velocity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringWriter

class VelocityToolTest {
    private fun render(resource: String, name: String): String {
        val template = javaClass.getResourceAsStream("/velocity/$resource.txt")!!.bufferedReader().use { it.readText() }
        val context = VelocityContext().apply { put("name", name); put("velocity", VelocityTool) }
        return StringWriter().use { Velocity.evaluate(context, it, "test", template); it.toString() }
    }

    @Test
    fun helloWorld() {
        assertEquals("Hello, world!", render("Helloworld", "world"))
    }

    @Test
    fun camelCaseName() {
        assertEquals("HelloThereMyGoodFriend", render("CamelCaseName", "hello there my_good-friend"))
    }

    @Test
    fun snakeCaseName() {
        assertEquals(
            "hello_there_my_good_friend",
            VelocityTool.snakeCaseName("hello there my_good-friend")
        )
        assertEquals("", VelocityTool.snakeCaseName(null))
    }

    @Test
    fun challengeDirectoryName() {
        assertEquals("p0001_two_sum", VelocityTool.challengeDirectoryName("1", "two-sum"))
        assertEquals("p12345_two_sum", VelocityTool.challengeDirectoryName("12345", "two-sum"))
        // 非数字题号与 Java 一致只沿用 slug，不再拼接题号。
        assertEquals("two_sum", VelocityTool.challengeDirectoryName("Interview 4", "two-sum"))
    }
}
