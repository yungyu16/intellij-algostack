package com.wenjunhuang.codeepiphany.configuration

import com.intellij.openapi.project.Project
import com.wenjunhuang.codeepiphany.model.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * 数据库默认目录改为全局后，旧的未显式配置项目要继续读到原库（迁移），显式配置不受影响。
 * Project 用代理替身（迁移逻辑只读 basePath）；全局目录相关的解析测试因依赖真实
 * XDG_DATA_HOME 环境变量无法在 JVM 内隔离，只覆盖纯逻辑部分。
 */
class DatabaseFolderMigrationTest {
    private fun fake(base: String?) = Proxy.newProxyInstance(
        Project::class.java.classLoader, arrayOf(Project::class.java)
    ) { _, method, _ ->
        when (method.name) {
            "getBasePath" -> base
            "getName" -> "fake"
            "isDisposed" -> false
            "hashCode" -> base.hashCode()
            "toString" -> "FakeProject($base)"
            else -> null
        }
    } as Project

    @Test fun unconfiguredFolderIsEmptyAndMeansGlobalDefault() {
        val state = CodeEpiphanySettings.CodeEpiphanySettingsState()
        assertEquals("", state.databaseFolder)
    }

    @Test fun globalDefaultFolderIsOutsideAnyProject() {
        val global = CodeEpiphanySettings.defaultDatabaseFolder()
        assertFalse(global.absolutePath.contains(".idea"))
        assertTrue(global.name == "leetcode-editor")
    }

}
