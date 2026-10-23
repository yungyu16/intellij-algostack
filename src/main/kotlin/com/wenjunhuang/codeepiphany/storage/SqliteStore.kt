package com.wenjunhuang.codeepiphany.storage

import com.wenjunhuang.codeepiphany.domain.*
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime

/** 业务查询集中于此；Connection、ResultSet 和 SQL 不进入 UI 或执行流程。 */
class SqliteStore internal constructor(private val connection: Connection) {
    private fun PreparedStatement.bind(values: List<Any?>) {
        values.forEachIndexed { index, value ->
            when (value) {
                // 保持旧 jOOQ SQLite 绑定的 Timestamp 文本格式，避免变为依赖时区的整数编码。
                is LocalDateTime -> setString(index + 1, Timestamp.valueOf(value).toString())
                else -> setObject(index + 1, value)
            }
        }
    }
    private fun update(sql: String, vararg values: Any?): Int {
        check(!connection.autoCommit) { "Writes require a repository transaction" }
        return connection.prepareStatement(sql).use { it.bind(values.toList()); it.executeUpdate() }
    }
    private fun <T> rows(sql: String, values: List<Any?> = emptyList(), map: (ResultSet) -> T): List<T> =
        connection.prepareStatement(sql).use { statement ->
            statement.bind(values)
            statement.executeQuery().use { result -> buildList { while (result.next()) add(map(result)) } }
        }
    private fun ResultSet.long(name: String) = getLong(name)
    private fun ResultSet.text(name: String) = getString(name).orEmpty()
    private fun ResultSet.date(name: String): LocalDateTime? {
        val value = getObject(name) ?: return null
        if (value is Number) return Timestamp(value.toLong()).toLocalDateTime()
        val text = value.toString()
        return text.toLongOrNull()?.let { Timestamp(it).toLocalDateTime() }
            ?: Timestamp.valueOf(text.replace('T', ' ')).toLocalDateTime()
    }
    private fun ResultSet.intOrNull(name: String): Int? = getInt(name).let { if (wasNull()) null else it }
    private fun ResultSet.floatOrNull(name: String): Float? = getFloat(name).let { if (wasNull()) null else it }
    private fun challenge(row: ResultSet) = ChallengeRecord(row.long("id"),
        CodeDojo.fromString(row.text("dojo")) ?: error("Unsupported challenge site"), row.text("dojoId"),
        row.text("title"), row.text("slug"), row.text("difficulty"), row.text("description"))

    fun challenge(id: Long): ChallengeRecord? = rows("SELECT * FROM challenge WHERE id = ? AND dojo IN ('leetcode','leetcodecn')", listOf(id), ::challenge).firstOrNull()

    fun saveChallenge(draft: ChallengeDraft): StoredChallenge {
        val challengeId = rows("SELECT id FROM challenge WHERE dojo = ? AND dojoId = ?",
            listOf(draft.dojo.value, draft.dojoId)) { it.getLong(1) }.firstOrNull() ?: IdGenerator.nextId().also {
            update("INSERT INTO challenge(id, dojo, dojoId, title, slug, difficulty, description) VALUES(?,?,?,?,?,?,?)",
                it, draft.dojo.value, draft.dojoId, draft.title, draft.slug, draft.difficulty, draft.description)
        }
        update("UPDATE challenge SET title=?, slug=?, difficulty=?, description=? WHERE id=?",
            draft.title, draft.slug, draft.difficulty, draft.description, challengeId)
        val languageId = rows("SELECT id FROM challenge_language WHERE challengeId=? AND language=? AND languageVersion=?",
            listOf(challengeId, draft.language.value, draft.version.version)) { it.getLong(1) }.firstOrNull()
            ?: IdGenerator.nextId().also {
                update("INSERT INTO challenge_language(id,challengeId,language,languageVersion,codeTemplate) VALUES(?,?,?,?,?)",
                    it, challengeId, draft.language.value, draft.version.version, draft.template)
            }
        update("UPDATE challenge_language SET codeTemplate=? WHERE id=?", draft.template, languageId)
        update("INSERT INTO leetcode_challenge(id,frontendQuestionId,testCase) VALUES(?,?,?) ON CONFLICT(id) DO UPDATE SET frontendQuestionId=excluded.frontendQuestionId,testCase=excluded.testCase",
            challengeId, draft.frontendId, draft.testCases)
        return StoredChallenge(challengeId, languageId, defaultSolution(challengeId))
    }

    fun defaultSolution(challengeId: Long): Long = rows("SELECT id FROM solution WHERE challengeId=? AND isDefault=1", listOf(challengeId)) { it.getLong(1) }
        .firstOrNull() ?: IdGenerator.nextId().also {
            update("INSERT INTO solution(id,challengeId,title,createDateTime,isDefault) VALUES(?,?,?,?,1)",
                it, challengeId, "Default", LocalDateTime.now())
        }

    fun executionRequest(challengeId: Long, languageId: Long): ExecutionRequest? = rows("""
        SELECT c.dojoId,c.slug,lc.testCase,cl.language,cl.languageVersion FROM challenge c
        JOIN leetcode_challenge lc ON lc.id=c.id JOIN challenge_language cl ON cl.challengeId=c.id
        WHERE c.id=? AND cl.id=?
    """.trimIndent(), listOf(challengeId, languageId)) {
        ExecutionRequest(it.text("dojoId"), it.text("slug"), it.text("testCase"),
            Language.fromString(it.text("language")) ?: error("Unknown stored language"), LanguageVersion.fromString(it.text("languageVersion")))
    }.firstOrNull()

    fun createSubmission(languageId: Long, solutionId: Long, localCode: String, submitCode: String, id: Long = IdGenerator.nextId()): Long {
        check(rows("SELECT 1 FROM challenge_language cl JOIN solution s ON s.challengeId=cl.challengeId WHERE cl.id=? AND s.id=?",
            listOf(languageId, solutionId)) { it.getInt(1) }.isNotEmpty()) { "Solution and language must belong to the same challenge" }
        update("INSERT INTO solution_submission(id,challengeLanguageId,solutionId,localCode,submitCode,submitDateTime,result) VALUES(?,?,?,?,?,?,?)",
            id, languageId, solutionId, localCode, submitCode, LocalDateTime.now(), SubmissionResult.Processing.value)
        return id
    }

    fun updateSubmission(id: Long, state: SubmissionResult, message: String, remoteId: String?, metrics: SubmissionMetrics? = null) {
        check(update("UPDATE solution_submission SET result=?,message=?,dojoSubmissionId=?,resultDateTime=? WHERE id=?",
            state.value, message, remoteId, if (state == SubmissionResult.Processing) null else LocalDateTime.now(), id) == 1)
        if (metrics != null) update("""INSERT INTO leetcode_submission
            (id,memory,totalCorrect,totalTestcases,statusMemory,runtimePercentile,memoryPercentile,lastTestcase,inputFormatted,expectedOutput,codeOutput,stdOutput,statusRuntime)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET
            memory=excluded.memory,totalCorrect=excluded.totalCorrect,totalTestcases=excluded.totalTestcases,
            statusMemory=excluded.statusMemory,runtimePercentile=excluded.runtimePercentile,memoryPercentile=excluded.memoryPercentile,
            lastTestcase=excluded.lastTestcase,inputFormatted=excluded.inputFormatted,expectedOutput=excluded.expectedOutput,
            codeOutput=excluded.codeOutput,stdOutput=excluded.stdOutput,statusRuntime=excluded.statusRuntime""".trimIndent(),
            id, metrics.memory, metrics.totalCorrect, metrics.totalTestcases, metrics.statusMemory, metrics.runtimePercentile,
            metrics.memoryPercentile, metrics.lastTestcase, metrics.inputFormatted, metrics.expectedOutput,
            metrics.codeOutput, metrics.stdOutput, metrics.statusRuntime)
    }

    fun failSubmission(id: Long, message: String) {
        update("UPDATE solution_submission SET result=?,message=?,resultDateTime=? WHERE id=? AND result=?",
            SubmissionResult.Unknown.value, message, LocalDateTime.now(), id, SubmissionResult.Processing.value)
    }

    private val submissionSelect = """SELECT ss.*,c.id AS challengeId,c.dojo,c.title AS challengeTitle,c.slug,c.difficulty,
        s.title AS solutionTitle,cl.language,cl.languageVersion FROM solution_submission ss
        JOIN solution s ON s.id=ss.solutionId JOIN challenge_language cl ON cl.id=ss.challengeLanguageId
        JOIN challenge c ON c.id=cl.challengeId""".trimIndent()
    private fun submission(row: ResultSet) = SubmissionRecord(row.long("id"), row.long("solutionId"), row.long("challengeLanguageId"),
        row.getString("dojoSubmissionId"), row.text("localCode"), row.text("submitCode"), row.text("result"), row.getString("message"),
        row.date("submitDateTime") ?: LocalDateTime.MIN, row.date("resultDateTime"), row.long("challengeId"),
        CodeDojo.fromString(row.text("dojo")) ?: error("Unsupported submission site"), row.text("challengeTitle"), row.text("slug"),
        row.text("difficulty"), row.text("solutionTitle"), Language.fromString(row.text("language")) ?: error("Unknown language"),
        LanguageVersion.fromString(row.text("languageVersion")))

    fun submission(id: Long): SubmissionRecord? = rows("$submissionSelect WHERE ss.id=? AND c.dojo IN ('leetcode','leetcodecn')", listOf(id), ::submission).firstOrNull()
    fun submissions(filter: SubmissionFilter, offset: Int, limit: Int): SubmissionPage {
        require(offset >= 0 && limit in 1..1000)
        val conditions = mutableListOf("c.dojo IN ('leetcode','leetcodecn')")
        val values = mutableListOf<Any?>()
        if (filter.dojos.isNotEmpty()) {
            conditions += "c.dojo IN (${filter.dojos.joinToString { "?" }})"; values.addAll(filter.dojos.map { it.value })
        }
        if (filter.languages.isNotEmpty()) {
            conditions += "cl.language IN (${filter.languages.joinToString { "?" }})"; values.addAll(filter.languages.map { it.value })
        }
        val where = conditions.joinToString(" AND ", " WHERE ")
        val total = rows("SELECT count(*) FROM ($submissionSelect$where)", values) { it.getInt(1) }.single()
        val direction = if (filter.ascending) "ASC" else "DESC"
        val data = rows("$submissionSelect$where ORDER BY ${filter.sort.column} $direction,ss.id $direction LIMIT ? OFFSET ?",
            values + listOf(limit, offset), ::submission)
        return SubmissionPage(total, data)
    }

    fun submissionMetrics(id: Long): SubmissionMetrics? = rows("SELECT * FROM leetcode_submission WHERE id=?", listOf(id)) {
        SubmissionMetrics(it.intOrNull("memory"), it.intOrNull("totalCorrect"), it.intOrNull("totalTestcases"),
            it.getString("statusMemory"), it.floatOrNull("runtimePercentile"), it.floatOrNull("memoryPercentile"),
            it.getString("lastTestcase"), it.getString("inputFormatted"), it.getString("expectedOutput"),
            it.getString("codeOutput"), it.getString("stdOutput"), it.getString("statusRuntime"))
    }.firstOrNull()

    fun languages(): List<Language> = rows("""SELECT DISTINCT cl.language FROM challenge_language cl
        JOIN challenge c ON c.id=cl.challengeId WHERE c.dojo IN ('leetcode','leetcodecn') ORDER BY cl.language""") {
        Language.fromString(it.getString(1))
    }.filterNotNull()

    fun solutions(challengeId: Long): List<SolutionRecord> = rows("""SELECT s.*,
        (SELECT count(*) FROM solution_submission ss WHERE ss.solutionId=s.id) AS submissions
        FROM solution s WHERE s.challengeId=? ORDER BY s.createDateTime,s.id""", listOf(challengeId)) {
        SolutionRecord(it.long("id"), it.long("challengeId"), it.text("title"), it.text("remark"),
            it.date("createDateTime") ?: LocalDateTime.MIN, it.getInt("isDefault") == 1, it.getInt("submissions"))
    }
    fun solution(id: Long): SolutionRecord? = rows("SELECT challengeId FROM solution WHERE id=?", listOf(id)) { it.getLong(1) }
        .firstOrNull()?.let { solutions(it).find { row -> row.id == id } }
    fun solutionLanguages(challengeId: Long): List<SolutionLanguageCount> = rows("""SELECT s.id,cl.language,cl.languageVersion,count(*) AS total
        FROM solution s JOIN solution_submission ss ON ss.solutionId=s.id JOIN challenge_language cl ON cl.id=ss.challengeLanguageId
        WHERE s.challengeId=? GROUP BY s.id,cl.language,cl.languageVersion""", listOf(challengeId)) {
        SolutionLanguageCount(it.long("id"), Language.fromString(it.text("language")) ?: error("Unknown language"),
            LanguageVersion.fromString(it.text("languageVersion")), it.getInt("total"))
    }
    fun createSolution(challengeId: Long, title: String): Long {
        require(challenge(challengeId) != null) { "Challenge not found" }
        require(title.isNotBlank() && solutions(challengeId).none { it.title == title }) { "Solution name already exists or is empty" }
        return IdGenerator.nextId().also {
            update("INSERT INTO solution(id,challengeId,title,createDateTime,isDefault) VALUES(?,?,?,?,0)", it, challengeId, title, LocalDateTime.now())
        }
    }
    fun renameSolution(id: Long, title: String) {
        val current = solution(id) ?: error("Solution not found")
        require(!current.isDefault && title.isNotBlank() && solutions(current.challengeId).none { it.id != id && it.title == title }) {
            "Cannot rename this solution"
        }
        update("UPDATE solution SET title=? WHERE id=?", title, id)
    }
    fun saveRemark(id: Long, text: String) { check(update("UPDATE solution SET remark=? WHERE id=?", text, id) == 1) { "Solution not found" } }
}
