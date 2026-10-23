package com.wenjunhuang.codeepiphany.storage

import org.flywaydb.core.Flyway
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.sql.DriverManager

class DatabaseCompatibilityTest {
    @Test fun upgradesTheFirstReleasedSchemaAndReopensExistingLongIds() {
        val folder = Files.createTempDirectory("codeepiphany-upgrade-")
        val file = folder.resolve("challenges.db").toFile()
        val url = "jdbc:sqlite:${file.absolutePath}"
        val id = 1234567890123456789L
        try {
            Flyway.configure(javaClass.classLoader).dataSource(url, null, null)
                .locations("classpath:db/migration").target("1").load().migrate()
            DriverManager.getConnection(url).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate("INSERT INTO challenge(id,title,slug,dojo,dojoId,difficulty,description) VALUES($id,'Legacy','legacy','leetcodecn','42','easy','preserved')")
                }
            }
            SqliteDatabase { file }.use { database ->
                database.read { assertEquals("preserved", it.challenge(id)!!.description) }
                val first = database.transaction { it.defaultSolution(id) }
                assertEquals(first, database.transaction { it.defaultSolution(id) })
            }
            SqliteDatabase { file }.use { database -> database.read { assertEquals(id, it.challenge(id)!!.id) } }
            DriverManager.getConnection(url).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank").use { rows ->
                        val versions = buildList { while (rows.next()) add(rows.getString(1)) }
                        assertEquals(listOf("001", "002", "003"), versions)
                    }
                }
            }
        } finally { Files.walk(folder).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
}
