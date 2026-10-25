package com.wenjunhuang.algostack.execution

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.wenjunhuang.algostack.domain.CodeDojo
import com.wenjunhuang.algostack.domain.Language
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

/** 仅默认布局可推断工程根；自定义路径必须已有明确的工程描述文件，不能替用户选择 module。 */
internal suspend fun ensureLanguageProject(
    root: Path, dojo: CodeDojo, language: Language, setting: GenerationSettings,
    goVersion: () -> String = ::installedGoVersion
) {
    if (language != Language.Java && language != Language.GO) return
    languageProjectLock.withLock {
        withContext(Dispatchers.IO) {
            val base = root.toRealPath()
            val folder = Path.of(setting.folder).toAbsolutePath().normalize()
            safeInitializationPath(base, folder.resolve(".project-path-check"))
            val descriptor = if (language == Language.Java) "pom.xml" else "go.mod"
            var cursor = folder
            while (true) {
                val existing = safeInitializationPath(base, cursor.resolve(descriptor))
                if (Files.exists(existing, NOFOLLOW_LINKS)) {
                    require(Files.isRegularFile(existing, NOFOLLOW_LINKS)) { "$descriptor must be a regular file, not a symlink" }
                    return@withContext
                }
                // 不在已有其他构建配置上叠加 Maven/module，也不猜多模块工作区的归属。
                val otherBuilds = if (language == Language.Java)
                    listOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts") else listOf("go.work")
                require(otherBuilds.none { Files.exists(cursor.resolve(it), NOFOLLOW_LINKS) }) {
                    "Existing build/workspace configuration: create $descriptor explicitly for the configured source folder"
                }
                if (cursor == base) break
                cursor = cursor.parent
            }
            val moduleRoot = base.resolve(if (language == Language.Java) "code/java" else "code/go")
            val defaultFolder = moduleRoot.resolve(if (language == Language.Java) "src/main/java/${dojo.value}" else dojo.value)
            require(folder == defaultFolder) {
                "Custom source folder has no $descriptor inside the project: create the language project explicitly or restore the default source folder"
            }
            val destination = safeInitializationPath(base, moduleRoot.resolve(descriptor))
            val content = if (language == Language.Java) minimalPom else "module leetcode-practice\n\ngo ${goDirective(goVersion())}\n"
            Files.createDirectories(destination.parent)
            safeInitializationPath(base, destination)
            try {
                Files.writeString(destination, content, CREATE_NEW, WRITE)
            } catch (exists: FileAlreadyExistsException) {
                // 其他调用者/进程先创建时只复用，不截断，也不尝试修复其内容。
                require(Files.isRegularFile(destination, NOFOLLOW_LINKS)) { "$descriptor must be a regular file, not a symlink" }
            }
        }
    }
}

internal fun goDirective(version: String): String {
    val match = Regex("^go version go(\\d+)\\.(\\d+)(?:[.\\s]|rc|beta)").find(version.trim())
        ?: error("Cannot determine installed Go version; create go.mod explicitly for the configured source folder")
    return "${match.groupValues[1]}.${match.groupValues[2]}"
}

private fun installedGoVersion(): String {
    // 只查询本机工具链，不允许 Go 自动下载 toolchain 或加载用户 go.work。
    val command = GeneralCommandLine("go", "version").withEnvironment("GOTOOLCHAIN", "local").withEnvironment("GOWORK", "off")
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
