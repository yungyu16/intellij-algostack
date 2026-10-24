package com.wenjunhuang.codeepiphany.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * UI Designer 表单的绑定代码在 instrument 阶段可能被重复生成，
 * 完全相同的方法签名会让插件在运行时抛 ClassFormatError（例如用例编辑面板无法打开）。
 */
class InstrumentedFormsTest {
    @Test fun instrumentedClassesHaveNoDuplicatedMethodSignatures() {
        val instrumented = File("build/instrumented/instrumentCode")
        assertTrue("未找到 instrument 产物：${instrumented.absolutePath}", instrumented.isDirectory)
        val broken = mutableListOf<String>()
        instrumented.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".class") && !it.name.contains("$") }
            .forEach { file ->
                val className = file.relativeTo(instrumented).path.removeSuffix(".class").replace(File.separatorChar, '.')
                // 重复方法签名在类加载阶段就会抛 ClassFormatError，反射拿不到方法列表。
                val failure = runCatching { define(file.readBytes(), className) }.exceptionOrNull()
                if (failure is ClassFormatError && failure.message.orEmpty().contains("Duplicate method name")) {
                    broken += "$className: ${failure.message}"
                }
            }
        assertEquals("instrument 产物存在重复方法签名", emptyList<String>(), broken)
    }

    private fun define(bytes: ByteArray, className: String): Class<*> =
        object : ClassLoader(javaClass.classLoader) {
            override fun findClass(name: String): Class<*> = defineClass(name, bytes, 0, bytes.size)
        }.loadClass(className)
}
