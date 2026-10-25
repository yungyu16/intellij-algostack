import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "io.github.yungyu16.algostack"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

sourceSets {
    main {
        resources.srcDir(layout.buildDirectory.dir("webviewResources"))
    }
}

configurations.configureEach {
    exclude(group = "org.slf4j")
}

// The target IDE provides the Kotlin runtime; do not package another copy.
configurations.matching { it.name == "runtimeClasspath" || it.name.endsWith("RuntimeClasspath") }.configureEach {
    exclude(group = "org.jetbrains.kotlin")
}

kotlin { jvmToolchain(25) }

// 安装包和 IDE 更新通知共用 CHANGELOG 的当前版本条目。
val releaseNotes = providers.fileContents(layout.projectDirectory.file("CHANGELOG.md")).asText
    .zip(providers.gradleProperty("pluginVersion")) { markdown, pluginVersion ->
        val entries = markdown.lineSequence().dropWhile { it.trim() != "## $pluginVersion" }.drop(1)
            .takeWhile { !it.startsWith("## ") }.filter { it.startsWith("- ") }.map { it.removePrefix("- ") }.toList()
        require(entries.isNotEmpty()) { "CHANGELOG.md 缺少 $pluginVersion 的更新条目" }
        entries.joinToString("", "<ul>", "</ul>") {
            "<li>" + it.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</li>"
        }
    }
val releaseNotesFile = layout.buildDirectory.file("releaseNotes/META-INF/release-notes.html")
val generateReleaseNotes = tasks.register("generateReleaseNotes") {
    inputs.property("releaseNotes", releaseNotes)
    outputs.file(releaseNotesFile)
    doLast {
        releaseNotesFile.get().asFile.apply { parentFile.mkdirs(); writeText(releaseNotes.get(), Charsets.UTF_8) }
    }
}

dependencies {
    intellijPlatform {
        val localPath = providers.gradleProperty("localPlatformPath")
        if (localPath.isPresent) local(localPath.get())
        else intellijIdea(providers.gradleProperty("platformVersion"))
        bundledPlugin("com.intellij.java")
        bundledModule("com.intellij.modules.jcef")
        testFramework(TestFrameworkType.Platform)
    }
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    compileOnly(kotlin("stdlib", "2.4.0"))
    testCompileOnly(kotlin("stdlib", "2.4.0"))
    implementation("com.squareup.okhttp3:okhttp-jvm:5.3.2")
    implementation("org.jsoup:jsoup:1.22.1")
    implementation("com.vladsch.flexmark:flexmark:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-attributes:0.64.8")
    implementation("org.apache.commons:commons-text:1.15.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.hamcrest:hamcrest:3.0")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()
        description = "<p>Personal fork of LeetCode Epiphany for LeetCodeCN only.</p>"
        changeNotes = releaseNotes
        ideaVersion { sinceBuild = providers.gradleProperty("platformSinceBuild") }
    }
    pluginVerification { ides { current() } }
}

val npmInstall = tasks.register<Exec>("npmInstall") {
    workingDir("webview")
    commandLine("npm", "ci")
    inputs.files("webview/package.json", "webview/package-lock.json")
    outputs.dir("webview/node_modules")
}

val buildWebview = tasks.register<Exec>("buildWebview") {
    dependsOn(npmInstall)
    workingDir("webview")
    commandLine("npm", "run", "build")
    inputs.files(fileTree("webview") { exclude("node_modules/**") })
    outputs.dir(layout.buildDirectory.dir("webviewResources"))
}

val checkWebview = tasks.register<Exec>("checkWebview") {
    dependsOn(npmInstall)
    workingDir("webview")
    commandLine("npm", "run", "check")
    inputs.files(fileTree("webview") { exclude("node_modules/**") })
}

val verifyRuntimeLibraries = tasks.register("verifyRuntimeLibraries") {
    group = "verification"
    description = "Check that retired database, Scala and jOOQ libraries are not packaged transitively."
    val runtime = configurations.named("runtimeClasspath")
    doLast {
        val retired = setOf("org.scala-lang", "org.scala-lang.modules", "org.typelevel", "io.circe", "org.http4s", "co.fs2", "org.jooq", "com.zaxxer", "org.xerial", "org.flywaydb")
        val found = runtime.get().resolvedConfiguration.resolvedArtifacts.map { it.moduleVersion.id }
            .filter { it.group in retired }.distinct()
        check(found.isEmpty()) { "Retired runtime libraries are still present: ${found.joinToString()}" }
    }
}
val checkAutomationClient = tasks.register<Exec>("checkAutomationClient") {
    group = "verification"
    commandLine("node", "--test", "scripts/test_initialize_challenge.mjs")
    inputs.files("scripts/initialize_challenge.mjs", "scripts/test_initialize_challenge.mjs")
}
tasks.check { dependsOn(checkWebview, verifyRuntimeLibraries, checkAutomationClient) }
tasks.processResources {
    dependsOn(buildWebview, generateReleaseNotes)
    includeEmptyDirs = false
    from("LICENSE") { into("META-INF") }
    from(layout.buildDirectory.dir("releaseNotes"))
}

// 增量编译删除旧包后可能留下空目录，不将退休功能的空包打入插件。
tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach { includeEmptyDirs = false }

tasks.test {
    // 产物回归直接检查最终插件 JAR。
    dependsOn("composedJar")
    systemProperty("algostack.pluginVersion", project.version.toString())
    systemProperty("algostack.pluginArtifact", tasks.named<org.jetbrains.intellij.platform.gradle.tasks.ComposedJarTask>("composedJar").get().archiveFile.get().asFile.absolutePath)
    useJUnit()
    systemProperty("java.awt.headless", "true")
    maxHeapSize = "2g"
}
