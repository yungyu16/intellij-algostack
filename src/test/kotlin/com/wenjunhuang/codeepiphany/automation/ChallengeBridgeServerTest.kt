package com.wenjunhuang.codeepiphany.automation

import com.wenjunhuang.codeepiphany.execution.InitializationResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.net.http.*
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class ChallengeBridgeServerTest {
    private fun request(server: ChallengeBridgeServer, body: String? = null, route: String = "/initialize-challenge",
        token: String = server.token, origin: Boolean = false): HttpRequest = HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}$route"))
        .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer $token").header("Content-Type", "application/json")
        .apply { if (origin) header("Origin", "https://example.invalid") }
        .method(if (body == null) "GET" else "POST", HttpRequest.BodyPublishers.ofString(body.orEmpty())).build()

    @Test fun narrowAuthenticatedEndpointSupportsBothLanguagesAndRejectsInternationalSite() {
        val calls = CopyOnWriteArrayList<InitializationRequest>()
        val backend = object : ChallengeBridgeBackend {
            override fun projects() = listOf("/project")
            override suspend fun initialize(request: InitializationRequest): InitializationResult {
                calls += request; return InitializationResult("created", associated = true, implemented = false)
            }
        }
        HttpClient.newHttpClient().use { client -> ChallengeBridgeServer(backend).use { server ->
            val input = InitializationRequest("/project", "two-sum", "go")
            val body = Json.encodeToString(input)
            fun status(request: HttpRequest) = client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()
            assertEquals(200, status(request(server, route = "/status")))
            assertEquals(403, status(request(server, body, token = "wrong")))
            assertEquals(403, status(request(server, body, origin = true)))
            assertEquals(404, status(request(server)))
            assertEquals(404, status(request(server, body, route = "/submit")))
            assertEquals(400, status(request(server, "{")))
            assertEquals(413, status(request(server, "x".repeat(4097))))
            for (invalid in listOf(input.copy(slug = "../escape"), input.copy(language = "python"), input.copy(dojo = "other"), input.copy(dojo = "leetcode")))
                assertEquals(400, status(request(server, Json.encodeToString(invalid))))
            assertEquals(409, status(request(server, Json.encodeToString(input.copy(project = "/wrong")))))
            assertTrue(calls.isEmpty())
            for (language in listOf("java", "go")) for (dojo in listOf("leetcodecn"))
                assertEquals(200, status(request(server, Json.encodeToString(input.copy(language = language, dojo = dojo)))))
            assertEquals(200, status(request(server, """{"project":"/project","slug":"two-sum","language":"java"}""")))
            assertEquals(3, calls.size)
            assertTrue(calls.all { it.dojo == "leetcodecn" })
            val rejected = client.send(request(server, Json.encodeToString(input.copy(dojo = "leetcode"))), HttpResponse.BodyHandlers.ofString())
            assertEquals(400, rejected.statusCode())
            assertTrue(rejected.body().contains("unsupported_dojo_only_leetcodecn"))
            assertEquals(3, calls.size)
        } }
    }

    @Test fun serializesInitializationAndProtectsDescriptor() {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val root = Files.createTempDirectory("challenge-bridge-state-")
        val backend = object : ChallengeBridgeBackend {
            override fun projects() = listOf("/project")
            override suspend fun initialize(request: InitializationRequest): InitializationResult {
                entered.complete(Unit); release.await(); return InitializationResult("created", associated = true)
            }
        }
        try {
            HttpClient.newHttpClient().use { client -> ChallengeBridgeServer(backend).use { server ->
                server.publish(root)
                val descriptor = root.resolve("${ProcessHandle.current().pid()}.json")
                assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(descriptor))
                assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(root))
                val body = Json.encodeToString(InitializationRequest("/project", "two-sum", "go"))
                val first = client.sendAsync(request(server, body), HttpResponse.BodyHandlers.ofString())
                runBlocking { kotlinx.coroutines.withTimeout(3_000) { entered.await() } }
                assertEquals(409, client.send(request(server, body), HttpResponse.BodyHandlers.ofString()).statusCode())
                release.complete(Unit); assertEquals(200, first.get(5, TimeUnit.SECONDS).statusCode())
            } }
            assertFalse(Files.exists(root.resolve("${ProcessHandle.current().pid()}.json")))
        } finally { release.complete(Unit); Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
}
