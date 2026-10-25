package io.github.yungyu16.algostack.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class FileUtilsTest {
    @Test
    fun removesIllegalFilenameCharactersWithoutLosingUnicode() {
        assertEquals("两数之和java", FileUtils.sanitizeFilename("两数之和:java?"))
        assertEquals("unnamed_file", FileUtils.sanitizeFilename(" \n\t "))
        assertEquals("unnamed_file", FileUtils.sanitizeFilename(null))
    }
}
