package io.github.yungyu16.algostack.execution

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import io.github.yungyu16.algostack.domain.CodeDojo
import io.github.yungyu16.algostack.domain.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE

private val languageProjectLock = Mutex()

/** 在固定源码目录的祖先中复用工程；没有描述文件时才创建内置布局的语言工程。 */
internal suspend fun ensureLanguageProject(
    root: Path, dojo: CodeDojo, language: Language,
    goVersion: () -> String = ::installedGoVersion
) {
    languageProjectLock.withLock {
        withContext(Dispatchers.IO) {
            val base = root.toRealPath()
            val layout = ChallengeConventions.layout(base, dojo, language)
            val folder = layout.sourceFolder
            safeInitializationPath(base, folder.resolve(".project-path-check"))
            val descriptor = if (language == Language.Java) "pom.xml" else "go.mod"
            var cursor = folder
            while (true) {
                val existing = safeInitializationPath(base, cursor.resolve(descriptor))
                if (Files.exists(existing, NOFOLLOW_LINKS)) {
                    require(
                        Files.isRegularFile(
                            existing,
                            NOFOLLOW_LINKS
                        )
                    ) { "$descriptor must be a regular file, not a symlink" }
                    return@withContext
                }
                // 不在已有其他构建配置上叠加 Maven/module，也不猜多模块工作区的归属。
                val otherBuilds = if (language == Language.Java)
                    listOf(
                        "build.gradle",
                        "build.gradle.kts",
                        "settings.gradle",
                        "settings.gradle.kts"
                    ) else listOf("go.work")
                require(otherBuilds.none { Files.exists(cursor.resolve(it), NOFOLLOW_LINKS) }) {
                    "Existing build/workspace configuration: create $descriptor explicitly for the conventional source folder"
                }
                if (cursor == base) break
                cursor = cursor.parent
            }
            val destination = safeInitializationPath(base, layout.moduleRoot.resolve(descriptor))
            val content =
                if (language == Language.Java) minimalPom else "module leetcode-practice\n\ngo ${goDirective(goVersion())}\n"
            Files.createDirectories(destination.parent)
            safeInitializationPath(base, destination)
            try {
                Files.writeString(destination, content, CREATE_NEW, WRITE)
            } catch (exists: FileAlreadyExistsException) {
                // 其他调用者/进程先创建时只复用，不截断，也不尝试修复其内容。
                require(
                    Files.isRegularFile(
                        destination,
                        NOFOLLOW_LINKS
                    )
                ) { "$descriptor must be a regular file, not a symlink" }
            }
        }
    }
}

internal fun goDirective(version: String): String {
    val match = Regex("^go version go(\\d+)\\.(\\d+)(?:[.\\s]|rc|beta)").find(version.trim())
        ?: error("Cannot determine installed Go version; create go.mod explicitly for the conventional source folder")
    return "${match.groupValues[1]}.${match.groupValues[2]}"
}

private fun installedGoVersion(): String {
    // 只查询本机工具链，不允许 Go 自动下载 toolchain 或加载用户 go.work。
    val command =
        GeneralCommandLine("go", "version").withEnvironment("GOTOOLCHAIN", "local").withEnvironment("GOWORK", "off")
    val result = CapturingProcessHandler(command).runProcess(5_000)
    check(!result.isTimeout && result.exitCode == 0) { "Cannot query local Go; install Go or create go.mod explicitly" }
    return result.stdout
}

private val minimalPom = """
    <?xml version="1.0" encoding="UTF-8"?>
    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
        <modelVersion>4.0.0</modelVersion>
        <groupId>local.practice</groupId>
        <artifactId>leetcode-practice</artifactId>
        <version>1.0-SNAPSHOT</version>
        <properties>
            <maven.compiler.source>1.8</maven.compiler.source>
            <maven.compiler.target>1.8</maven.compiler.target>
            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        </properties>
    </project>
""".trimIndent() + "\n"
