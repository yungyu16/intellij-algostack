package com.wenjunhuang.algostack.execution

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE

@Serializable
data class InitializationResult(
    val status: String, val path: String? = null, val associated: Boolean = false,
    val implemented: Boolean? = null, val reason: String? = null
)

/** 文件、关联和编辑器不能跨资源事务化；失败保留新文件并报告 partial，不伪装成完成或覆盖重试。 */
internal suspend fun initializeChallengeFile(
    root: Path, target: Path, code: String,
    associationMatches: suspend () -> Boolean,
    hasMapping: () -> Boolean,
    persistAndAssociate: suspend () -> Unit,
    open: suspend () -> Unit,
    initializeProject: suspend () -> Unit = {},
    reportFailure: (Exception) -> Unit = {}
): InitializationResult {
    val destination = withContext(Dispatchers.IO) { safeInitializationPath(root, target) }
    val location = destination.toString()
    var created = false
    var associated = false
    try {
        if (withContext(Dispatchers.IO) { Files.exists(destination, NOFOLLOW_LINKS) }) {
            if (!withContext(Dispatchers.IO) { Files.isRegularFile(destination, NOFOLLOW_LINKS) } || !associationMatches())
                return InitializationResult("conflict", location, reason = "existing_file_not_touched")
            associated = true
            // 已有匹配骨架也可能尚未创建语言工程，不能在工程检查前短路。
            initializeProject()
            return InitializationResult("already_exists", location, associated = true)
        }
        if (hasMapping()) return InitializationResult("conflict", location, reason = "stale_mapping_requires_review")
        currentCoroutineContext().ensureActive()
        withContext(Dispatchers.IO) {
            // 每次写入都重新验证目录，CREATE_NEW 同时阻止同名文件和软链接覆盖。
            safeInitializationPath(root, destination)
            Files.createDirectories(destination.parent)
            safeInitializationPath(root, destination)
            Files.newOutputStream(destination, CREATE_NEW, WRITE).use {
                created = true
                it.write(code.toByteArray(Charsets.UTF_8))
            }
        }
        initializeProject()
        persistAndAssociate()
        associated = associationMatches()
        check(associated) { "Association verification failed" }
        open()
        return InitializationResult("created", location, associated = true, implemented = false)
    } catch (cancelled: CancellationException) {
        // 取消必须向项目任务传播；调用端不能据此假设没有写入，应检查目标文件。
        throw cancelled
    } catch (failure: Exception) {
        reportFailure(failure)
        return InitializationResult(if (created || associated) "partial" else "conflict", location, associated,
            reason = if (created || associated) "initialization_incomplete: ${failure.message}" else "create_failed")
    }
}

internal fun safeInitializationPath(root: Path, target: Path): Path {
    val base = root.toRealPath()
    val destination = target.toAbsolutePath().normalize()
    require(destination.startsWith(base) && destination != base) { "Destination must be inside the open project" }
    var cursor = base
    for (part in base.relativize(destination.parent)) {
        cursor = cursor.resolve(part)
        require(!Files.isSymbolicLink(cursor)) { "Symlink parents are not supported" }
        require(!Files.exists(cursor, NOFOLLOW_LINKS) || Files.isDirectory(cursor, NOFOLLOW_LINKS)) { "Parent is not a directory" }
    }
    return destination
}
