package io.github.yungyu16.algostack.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class FileUtilsTest {
    @Test fun removesIllegalFilenameCharactersWithoutLosingUnicode() {
        assertEquals("两数之和java", FileUtils.sanitizeFilename("两数之和:java?"))
        assertEquals("unnamed_file", FileUtils.sanitizeFilename(" \n\t "))
        assertEquals("unnamed_file", FileUtils.sanitizeFilename(null))
    }

    @Test fun readsTheQualifiedJavaClassName() {
        val source = Files.createTempFile("algostack-test-", ".java")
        try {
            Files.writeString(source, "package sample.algorithms;\npublic class P0001_TwoSum { }")
            assertEquals("sample.algorithms.P0001_TwoSum", FileUtils.getJavaMainClassName(source.toString()))
        } finally { Files.deleteIfExists(source) }
    }
}
