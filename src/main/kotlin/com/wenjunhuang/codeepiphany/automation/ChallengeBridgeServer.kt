package com.wenjunhuang.codeepiphany.automation

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.wenjunhuang.codeepiphany.execution.InitializationResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.HexFormat
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

@Serializable
data class InitializationRequest(val project: String, val slug: String, val language: String, val dojo: String = "leetcodecn") {
    fun valid(): Boolean = project.isNotBlank() && language in setOf("java", "go") && dojo == "leetcodecn"
        && slug.length <= 160 && Regex("[a-z0-9]+(?:-[a-z0-9]+)*").matches(slug)
}

internal interface ChallengeBridgeBackend {
    fun projects(): List<String>
    suspend fun initialize(request: InitializationRequest): InitializationResult
}

/** 与旧桥接客户端兼容；只有窄接口，不开放任意文件、代码执行或平台测试/提交。 */
internal class ChallengeBridgeServer(private val backend: ChallengeBridgeBackend) : AutoCloseable {
    private val json = Json { encodeDefaults = true }
    internal val token = HexFormat.of().formatHex(ByteArray(32).also { SecureRandom().nextBytes(it) })
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 8)
    private val workers = Executors.newFixedThreadPool(2) { runnable -> Thread(runnable, "codeepiphany-challenge-bridge").apply { isDaemon = true } }
    private val creating = Semaphore(1)
    private var descriptor: Path? = null
    val port: Int get() = server.address.port
    init { server.executor = workers; server.createContext("/", ::handle); server.start() }

    fun publish(directory: Path) {
        Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        require(!Files.isSymbolicLink(directory)) { "Bridge directory must not be a symlink" }
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
        val target = directory.resolve("${ProcessHandle.current().pid()}.json")
        val temporary = Files.createTempFile(directory, ".bridge-", ".tmp",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        try {
            Files.writeString(temporary, buildJsonObject {
                put("version", 1); put("pid", ProcessHandle.current().pid()); put("port", port); put("token", token)
            }.toString())
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
            descriptor = target
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun handle(exchange: HttpExchange) {
        exchange.use { x ->
            val headers = x.requestHeaders
            val authorization = headers.getFirst("Authorization").orEmpty()
            if (!x.remoteAddress.address.isLoopbackAddress || headers.getFirst("Host") != "127.0.0.1:$port"
                || headers.containsKey("Origin") || !MessageDigest.isEqual(authorization.toByteArray(), "Bearer $token".toByteArray())) {
                reply(x, 403, error("forbidden")); return
            }
            try {
                if (x.requestURI.toString() == "/status" && x.requestMethod == "GET") {
                    reply(x, 200, buildJsonObject { put("version", 1); put("projects", JsonArray(backend.projects().map(::JsonPrimitive))) }); return
                }
                if (x.requestURI.toString() != "/initialize-challenge" || x.requestMethod != "POST") {
                    reply(x, 404, error("unsupported_operation")); return
                }
                if (headers.getFirst("Content-Type") != "application/json") { reply(x, 415, error("json_required")); return }
                val body = x.requestBody.readNBytes(4097)
                if (body.size > 4096) { reply(x, 413, error("request_too_large")); return }
                val request = try { json.decodeFromString<InitializationRequest>(body.toString(Charsets.UTF_8)) }
                    catch (_: Exception) { reply(x, 400, error("invalid_request")); return }
                if (request.dojo != "leetcodecn") { reply(x, 400, error("unsupported_dojo_only_leetcodecn")); return }
                if (!request.valid()) { reply(x, 400, error("invalid_request")); return }
                if (request.project !in backend.projects()) { reply(x, 409, error("project_not_open")); return }
                if (!creating.tryAcquire()) { reply(x, 409, error("busy")); return }
                try {
                    val result = runBlocking { withTimeout(120_000) { backend.initialize(request) } }
                    reply(x, 200, json.encodeToJsonElement(result))
                } finally { creating.release() }
            } catch (_: Exception) {
                // 底层异常可能含网络响应或凭据，不透传；取消后也可能已有文件，禁止盲目覆盖重试。
                runCatching { reply(x, 409, error("initialize_failed_or_cancelled_check_target")) }
            }
        }
    }

    private fun error(code: String) = buildJsonObject { put("error", code) }
    private fun reply(x: HttpExchange, status: Int, value: JsonElement) {
        val bytes = value.toString().toByteArray(Charsets.UTF_8)
        x.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        x.responseHeaders.set("Cache-Control", "no-store")
        x.sendResponseHeaders(status, bytes.size.toLong()); x.responseBody.write(bytes)
    }
    override fun close() {
        server.stop(0); workers.shutdownNow()
        descriptor?.let { file -> runCatching {
            if (Json.parseToJsonElement(Files.readString(file)).jsonObject["token"]?.jsonPrimitive?.content == token)
                Files.deleteIfExists(file)
        } }
    }
}
