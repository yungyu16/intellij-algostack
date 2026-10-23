package com.wenjunhuang.codeepiphany.runtime

import com.wenjunhuang.codeepiphany.runtime.HttpClientFactory

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

class HttpTlsTest {
    private fun withServer(test: (Int, SSLContext, X509TrustManager) -> Unit) {
        val folder = Files.createTempDirectory("codeepiphany-tls-")
        val store = folder.resolve("server.p12")
        val output = folder.resolve("keytool.log")
        val password = "local-test-only"
        val process = ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
            "-genkeypair", "-alias", "localhost", "-keyalg", "RSA", "-keysize", "2048", "-storetype", "PKCS12",
            "-keystore", store.toString(), "-storepass", password, "-keypass", password, "-dname", "CN=localhost",
            "-ext", "SAN=dns:localhost", "-validity", "1").redirectErrorStream(true).redirectOutput(output.toFile()).start()
        var server: HttpsServer? = null
        try {
            assertTrue("keytool timed out", process.waitFor(20, TimeUnit.SECONDS))
            assertEquals(Files.readString(output), 0, process.exitValue())
            val keys = KeyStore.getInstance("PKCS12").apply { Files.newInputStream(store).use { load(it, password.toCharArray()) } }
            val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys, password.toCharArray()) }
            val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keys) }
            val manager = trust.trustManagers.filterIsInstance<X509TrustManager>().first()
            val context = SSLContext.getInstance("TLS").apply { init(managers.keyManagers, trust.trustManagers, null) }
            server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                httpsConfigurator = HttpsConfigurator(context)
                createContext("/") { exchange -> exchange.use { it.sendResponseHeaders(200, 2); it.responseBody.write("ok".toByteArray()) } }
                start()
            }
            test(server.address.port, context, manager)
        } finally {
            server?.stop(0)
            if (process.isAlive) process.destroyForcibly().waitFor()
            Files.walk(folder).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
    private fun client() = HttpClientFactory.newBuilder().dns { listOf(InetAddress.getByName("127.0.0.1")) }
        .proxy(Proxy.NO_PROXY).cache(null).callTimeout(5, TimeUnit.SECONDS).build()
    @Test fun rejectsUntrustedCertificates() = withServer { port, _, _ ->
        val http = client()
        try { assertThrows(SSLHandshakeException::class.java) { http.newCall(Request.Builder().url("https://localhost:$port/").build()).execute().use { } } }
        finally { http.connectionPool.evictAll() }
    }
    @Test fun explicitTrustStillChecksHostname() = withServer { port, context, trust ->
        val http = client().newBuilder().sslSocketFactory(context.socketFactory, trust).build()
        try {
            http.newCall(Request.Builder().url("https://localhost:$port/").build()).execute().use { assertEquals("ok", it.body.string()) }
            assertThrows(SSLPeerUnverifiedException::class.java) { http.newCall(Request.Builder().url("https://127.0.0.1:$port/").build()).execute().use { } }
        } finally { http.connectionPool.evictAll() }
    }
}
