package com.wenjunhuang.codeepiphany.storage

import com.wenjunhuang.codeepiphany.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.LocalDateTime

class SqliteStoreTest {
    private fun fixture(block: (SqliteDatabase, java.io.File) -> Unit) {
        val folder = Files.createTempDirectory("codeepiphany-jdbc-")
        val file = folder.resolve("challenges.db").toFile()
        try { SqliteDatabase { file }.use { block(it, file) } }
        finally { Files.walk(folder).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
    private fun draft(dojo: CodeDojo = CodeDojo.LeetCodeCN, language: Language = Language.Java) =
        ChallengeDraft(dojo, "1", "Two Sum", "two-sum", "easy", "description", "1", "[1,2]", language, LanguageVersion.AnyVersion, "code")

    @Test fun storesChallengesWithoutDuplicatingExistingAssociations() = fixture { database, _ ->
        val first = database.transaction { it.saveChallenge(draft()) }
        val second = database.transaction { it.saveChallenge(draft().copy(description = "updated")) }
        assertEquals(first, second)
        database.read {
            assertEquals("updated", it.challenge(first.challengeId)!!.description)
            assertEquals("[1,2]", it.executionRequest(first.challengeId, first.languageId)!!.defaultCases)
            assertEquals(listOf(Language.Java), it.languages())
        }
    }

    @Test fun rollsBackTheWholeBusinessOperation() = fixture { database, _ ->
        var id = 0L
        assertThrows(IllegalStateException::class.java) {
            database.transaction { id = it.saveChallenge(draft()).challengeId; error("rollback") }
        }
        assertNull(database.read { it.challenge(id) })
    }

    @Test fun recordsSubmissionStatesMetricsFiltersAndRemarks() = fixture { database, _ ->
        val challenge = database.transaction { it.saveChallenge(draft()) }
        val id = database.transaction { it.createSubmission(challenge.languageId, challenge.solutionId, "local", "submitted") }
        database.transaction { it.updateSubmission(id, SubmissionResult.Success, "accepted", "42", SubmissionMetrics(memory = 123, totalCorrect = 10, totalTestcases = 10)) }
        database.read {
            val row = it.submission(id)!!
            assertEquals("local", row.localCode)
            assertEquals("42", row.dojoSubmissionId)
            assertNotNull(row.resultDateTime)
            assertEquals(123, it.submissionMetrics(id)!!.memory)
            assertEquals(1, it.submissions(SubmissionFilter(), 0, 20).total)
            assertEquals(0, it.submissions(SubmissionFilter(dojos = setOf(CodeDojo.LeetCode)), 0, 20).total)
        }
        val note = database.transaction { it.createSolution(challenge.challengeId, "Alternative") }
        database.transaction { it.renameSolution(note, "Renamed"); it.saveRemark(note, "# Notes\nUnicode 中文") }
        assertEquals("# Notes\nUnicode 中文", database.read { it.solution(note)!!.remark })
        assertThrows(IllegalArgumentException::class.java) { database.transaction { it.renameSolution(challenge.solutionId, "Forbidden") } }
    }

    @Test fun readsLegacyLongIdsAndBothSQLiteTimestampRepresentations() = fixture { database, file ->
        database.read { }
        val expected = LocalDateTime.of(2024, 1, 2, 3, 4, 5, 123000000)
        val id = 1234567890123456789L
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use {
                it.executeUpdate("INSERT INTO challenge(id,dojo,dojoId,title,slug,difficulty,description) VALUES($id,'leetcodecn','legacy','Legacy','legacy','easy','old')")
                it.executeUpdate("INSERT INTO solution(id,challengeId,title,createDateTime,isDefault) VALUES(1,$id,'Text date','2024-01-02 03:04:05.123',1)")
                it.executeUpdate("INSERT INTO solution(id,challengeId,title,createDateTime,isDefault) VALUES(2,$id,'Integer date',${Timestamp.valueOf(expected).time},0)")
                it.executeUpdate("INSERT INTO solution(id,challengeId,title,createDateTime,isDefault) VALUES(3,$id,'Precise date','2024-01-02 03:04:05.123456789',0)")
            }
        }
        database.read {
            assertEquals(id, it.challenge(id)!!.id)
            assertEquals(expected, it.solution(1)!!.createdAt)
            assertEquals(expected, it.solution(2)!!.createdAt)
            assertEquals(expected.withNano(123456789), it.solution(3)!!.createdAt)
        }
    }

    @Test fun refusesToReopenAfterDisposal() = fixture { database, _ ->
        database.read { }
        database.close()
        assertThrows(IllegalStateException::class.java) { database.read { it.languages() } }
    }

    @Test fun newlyWrittenDatesKeepTheLegacyTextEncoding() = fixture { database, file ->
        val saved = database.transaction { it.saveChallenge(draft()) }
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT typeof(createDateTime),createDateTime FROM solution WHERE id=${saved.solutionId}").use { rows ->
                    assertTrue(rows.next()); assertEquals("text", rows.getString(1))
                    assertEquals(Timestamp.valueOf(rows.getString(2)).toLocalDateTime(), database.read { it.solution(saved.solutionId)!!.createdAt })
                }
            }
        }
    }

    @Test fun refusesWritesOutsideATransactionAndPreservesTerminalResults() = fixture { database, _ ->
        val challenge = database.transaction { it.saveChallenge(draft()) }
        assertThrows(IllegalStateException::class.java) { database.read { it.saveRemark(challenge.solutionId, "must not persist") } }
        assertEquals("", database.read { it.solution(challenge.solutionId)!!.remark })
        val id = database.transaction { it.createSubmission(challenge.languageId, challenge.solutionId, "local", "code") }
        database.transaction { it.updateSubmission(id, SubmissionResult.Success, "accepted", "remote") }
        database.transaction { it.failSubmission(id, "late cancellation") }
        assertEquals(SubmissionResult.Success.value, database.read { it.submission(id)!!.result })
    }

    @Test fun rejectsCrossChallengeSubmissionAssociations() = fixture { database, _ ->
        val first = database.transaction { it.saveChallenge(draft()) }
        val second = database.transaction { it.saveChallenge(draft().copy(dojoId = "2", slug = "second")) }
        assertThrows(IllegalStateException::class.java) {
            database.transaction { it.createSubmission(first.languageId, second.solutionId, "local", "code") }
        }
        assertEquals(0, database.read { it.submissions(SubmissionFilter(), 0, 20).total })
    }

    @Test fun switchesDatabaseFilesWithoutSharingRows() {
        val folder = Files.createTempDirectory("codeepiphany-db-switch-")
        val first = folder.resolve("first.db").toFile()
        val second = folder.resolve("second.db").toFile()
        var current = first
        try {
            SqliteDatabase { current }.use { database ->
                val saved = database.transaction { it.saveChallenge(draft()) }
                current = second
                assertNull(database.read { it.challenge(saved.challengeId) })
                current = first
                assertEquals("Two Sum", database.read { it.challenge(saved.challengeId)!!.title })
            }
        } finally { Files.walk(folder).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
}
