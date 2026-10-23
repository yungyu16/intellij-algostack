package com.wenjunhuang.codeepiphany.storage

import com.wenjunhuang.codeepiphany.domain.Language
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.sql.DriverManager

class SubmissionQueriesTest {
    @Test fun languageChoicesExcludeUnsupportedSitesAndDuplicates() {
        val folder = Files.createTempDirectory("codeepiphany-submissions-")
        val file = folder.resolve("challenges.db").toFile()
        try {
            SqliteDatabase { file }.use { database ->
                database.read { }
                DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
                    for ((index, pair) in listOf("leetcode" to "java", "leetcodecn" to "java", "leetcodecn" to "python", "atcoder" to "cpp").withIndex()) {
                        val id = index + 1L
                        connection.createStatement().use {
                            it.executeUpdate("INSERT INTO challenge(id,dojo,dojoId,title,slug,difficulty,description) VALUES($id,'${pair.first}','$id','fixture','fixture','easy','fixture')")
                            it.executeUpdate("INSERT INTO challenge_language(id,challengeId,language,languageVersion,codeTemplate) VALUES($id,$id,'${pair.second}','','fixture')")
                        }
                    }
                }
                assertEquals(listOf(Language.Java, Language.Python), database.read { it.languages() })
            }
        } finally { Files.walk(folder).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
}
