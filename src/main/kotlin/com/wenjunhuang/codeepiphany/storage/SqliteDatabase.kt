package com.wenjunhuang.codeepiphany.storage

import org.flywaydb.core.Flyway
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/** 项目内串行执行短数据库操作；每次操作自行关闭连接，不保留连接池。 */
class SqliteDatabase(private val file: () -> File) : AutoCloseable {
    private val lock = Any()
    @Volatile private var closed = false
    private var initializedPath: String? = null

    fun <T> read(block: (SqliteStore) -> T): T = connection { block(SqliteStore(it)) }

    fun <T> transaction(block: (SqliteStore) -> T): T = connection { connection ->
        connection.autoCommit = false
        try {
            val result = block(SqliteStore(connection))
            connection.commit()
            result
        } catch (failure: Throwable) {
            try { connection.rollback() } catch (rollback: Throwable) { failure.addSuppressed(rollback) }
            throw failure
        }
    }

    private fun <T> connection(block: (Connection) -> T): T = synchronized(lock) {
        check(!closed) { "Database has been closed" }
        val target = file().canonicalFile
        check(target.parentFile.isDirectory || target.parentFile.mkdirs()) { "Cannot create database directory" }
        val url = "jdbc:sqlite:${target.path}"
        if (initializedPath != target.path || !target.exists()) {
            Flyway.configure(javaClass.classLoader).dataSource(url, null, null)
                .locations("classpath:db/migration").load().migrate()
            initializedPath = target.path
        }
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use { it.execute("PRAGMA busy_timeout = 5000") }
            block(connection)
        }
    }

    // 已开始的操作会在 use 中完成并关闭连接；排队中的操作随后失败，不会重新打开数据库。
    override fun close() { closed = true }
}
