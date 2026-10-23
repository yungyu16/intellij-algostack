package com.wenjunhuang.codeepiphany.utils.template

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
    @Test fun helloWorld() { assertEquals("Hello, world!", render("Helloworld", "world")) }
    @Test fun camelCaseName() { assertEquals("HelloThereMyGoodFriend", render("CamelCaseName", "hello there my_good-friend")) }
}
