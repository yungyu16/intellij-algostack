package com.wenjunhuang.algostack.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.jar.JarFile

/**
 * UI Designer 插桩可能生成重复方法（ClassFormatError），
 * 也可能因表单声明与绑定字段类型不符生成非法调用（VerifyError）。
 * 直接定义产物字节码，并对用例面板触发方法体验证，不依赖 IDE 初始化。
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
                try {
                    define(file.readBytes(), className)
                } catch (failure: ClassFormatError) {
                    if (!failure.message.orEmpty().contains("Duplicate method name")) throw failure
                    broken += "$className: ${failure.message}"
                }
            }
        assertEquals("instrument 产物存在重复方法签名", emptyList<String>(), broken)
    }

    @Test fun instrumentedTestCasePanelPassesJvmVerification() {
        val file = File("build/instrumented/instrumentCode/${panelClassName.replace('.', '/')}.class")
        verifyPanel(file.readBytes())
    }

    @Test fun packagedTestCasePanelPassesJvmVerification() {
        val artifact = File(requireNotNull(System.getProperty("algostack.pluginArtifact")))
        JarFile(artifact).use { jar ->
            val entry = requireNotNull(jar.getJarEntry("${panelClassName.replace('.', '/')}.class"))
            verifyPanel(jar.getInputStream(entry).use { it.readBytes() })
        }
    }

    private fun verifyPanel(bytes: ByteArray) {
        // 直接定义目标字节码，避免 parent-first 命中测试 classpath 上未经插桩的类。
        // defineClass 本身不保证验证方法体；反射构造器和方法会触发 JVM 校验，但不启动 IDE 服务。
        val panel = define(bytes, panelClassName)
        assertTrue(panel.classLoader !== javaClass.classLoader)
        assertEquals(1, panel.declaredConstructors.size)
        assertTrue(panel.declaredMethods.any { it.name == "\$\$\$setupUI\$\$\$" })
    }

    private val panelClassName = "com.wenjunhuang.algostack.utils.testCases.TestCaseItemPanel"

    private fun define(bytes: ByteArray, className: String): Class<*> =
        object : ClassLoader(javaClass.classLoader) {
            fun defineTarget(): Class<*> = defineClass(className, bytes, 0, bytes.size)
        }.defineTarget()
}
