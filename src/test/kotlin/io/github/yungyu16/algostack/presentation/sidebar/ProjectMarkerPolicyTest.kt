package io.github.yungyu16.algostack.presentation.sidebar

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

/** root `.leetcode` 仅作启用标记：接受文件或目录，不读取也不写入其内容。 */
class ProjectMarkerPolicyTest {
    @Test
    fun rootMarkerAcceptsFileOrDirectoryWithoutWritingAnything() {
        val root = Files.createTempDirectory("leetcode-marker")
        try {
            assertFalse(SidebarPolicy.enabled(root.toString()))
            val marker = Files.createFile(root.resolve(".leetcode"))
            assertTrue(SidebarPolicy.enabled(root.toString()))
            Files.delete(marker); Files.createDirectory(marker)
            assertTrue(SidebarPolicy.enabled(root.toString()))
            assertEquals(1, root.toFile().listFiles()!!.size)
            assertFalse(SidebarPolicy.enabled(null))
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
