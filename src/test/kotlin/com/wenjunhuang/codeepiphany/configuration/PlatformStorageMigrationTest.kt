package com.wenjunhuang.codeepiphany.configuration

import com.wenjunhuang.codeepiphany.presentation.sidebar.SidebarPolicy
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class PlatformStorageMigrationTest {
    @Test fun rootMarkerAcceptsFileOrDirectoryWithoutWritingAnything() {
        val root = Files.createTempDirectory("leetcode-marker")
        try {
            assertFalse(SidebarPolicy.enabled(root.toString()))
            val marker = Files.createFile(root.resolve(".leetcode"))
            assertTrue(SidebarPolicy.enabled(root.toString()))
            Files.delete(marker); Files.createDirectory(marker)
            assertTrue(SidebarPolicy.enabled(root.toString()))
            assertEquals(1, root.toFile().listFiles()!!.size)
            assertFalse(SidebarPolicy.enabled(null))
        } finally { root.toFile().deleteRecursively() }
    }
}
