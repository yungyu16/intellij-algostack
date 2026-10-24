package com.wenjunhuang.codeepiphany.configuration

import com.wenjunhuang.codeepiphany.model.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.nio.file.Files
import kotlin.io.path.writeText

/** 模板配置文件从 .idea/CodeEpiphany 迁到 .leetcode（可提交到 git）。 */
class PlatformStorageMigrationTest {
    private fun fake(base: String?) = Proxy.newProxyInstance(
        com.intellij.openapi.project.Project::class.java.classLoader,
        arrayOf(com.intellij.openapi.project.Project::class.java)
    ) { _, method, _ ->
        when (method.name) {
            "getBasePath" -> base
            "getName" -> "fake"
            "isDisposed" -> false
            "hashCode" -> base.hashCode()
            "toString" -> "FakeProject($base)"
            else -> null
        }
    } as com.intellij.openapi.project.Project

    @Test fun legacyTemplateConfigIsCopiedToTheDotLeetcodeFolder() {
        val project = Files.createTempDirectory("migrate-project")
        val legacyDir = project.resolve(".idea").resolve(Constants.PROJECT_NAME)
        Files.createDirectories(legacyDir)
        legacyDir.resolve("leetcodecn.xml").writeText("<!-- template config -->")
        try {
            PlatformSettings.migrateLegacyStorage(fake(project.toString()))
            val migrated = project.resolve(".leetcode").resolve("leetcodecn.xml")
            assertTrue("迁移后 .leetcode 应有模板配置", Files.exists(migrated))
            assertEquals("<!-- template config -->", Files.readString(migrated))
            // 幂等：用户改过 .leetcode 副本后不再覆盖
            migrated.writeText("<!-- user edited -->")
            PlatformSettings.migrateLegacyStorage(fake(project.toString()))
            assertEquals("<!-- user edited -->", Files.readString(migrated))
        } finally { project.toFile().deleteRecursively() }
    }

    @Test fun projectsWithoutLegacyConfigGetNothingCopied() {
        val project = Files.createTempDirectory("empty-project")
        try {
            PlatformSettings.migrateLegacyStorage(fake(project.toString()))
            assertFalse(Files.exists(project.resolve(".leetcode")))
        } finally { project.toFile().deleteRecursively() }
    }

    @Test fun templateStoragePointsToTheGitFriendlyFolder() {
        assertEquals(".leetcode/leetcodecn.xml", Constants.LEETCODE_CN_SETTING_FILE)
        assertEquals(".leetcode/leetcode.xml", Constants.LEETCODE_SETTING_FILE)
    }
}
