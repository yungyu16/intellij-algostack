package io.github.yungyu16.algostack.presentation.browser

import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.AppExecutorUtil
import com.sun.net.httpserver.HttpServer
import org.jsoup.Jsoup
import java.net.InetSocketAddress
import java.net.URI
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/** 只服务插件 WebView 资源；远端 HTML 不能借此读取其他 classpath 文件。 */
class WebResources : Disposable {
    data class Content(val bytes: ByteArray, val type: String, val policy: String? = null)

    private val pages = ConcurrentHashMap<String, Content>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.executor = AppExecutorUtil.getAppExecutorService()
        server.createContext("/") { exchange ->
            exchange.use {
                val path = exchange.requestURI.path.removePrefix("/")
                val content = if (validResourcePath(path)) pages[path] ?: resource(path) else null
                if (content == null) {
                    exchange.sendResponseHeaders(404, -1); return@use
                }
                exchange.responseHeaders.set("Content-Type", content.type)
                exchange.responseHeaders.set("X-Content-Type-Options", "nosniff")
                content.policy?.let { exchange.responseHeaders.set("Content-Security-Policy", it) }
                exchange.sendResponseHeaders(200, content.bytes.size.toLong())
                exchange.responseBody.write(content.bytes)
            }
        }
        server.start()
    }

    fun set(path: String, text: String, type: String, policy: String? = null) {
        pages[path] = Content(text.toByteArray(Charsets.UTF_8), type, policy)
    }

    fun set(path: String, content: Content) {
        pages[path] = content
    }

    fun template(path: String): String =
        requireNotNull(resource(path)) { "Missing WebView resource: $path" }.bytes.toString(Charsets.UTF_8)

    private fun resource(path: String): Content? = javaClass.getResourceAsStream("/webview/$path")?.use { stream ->
        val type = when (path.substringAfterLast('.')) {
            "html" -> "text/html; charset=utf-8"; "js" -> "text/javascript; charset=utf-8"; "css" -> "text/css; charset=utf-8"
            "svg" -> "image/svg+xml"; "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"
            "woff" -> "font/woff"; "woff2" -> "font/woff2"; "ttf" -> "font/ttf"; else -> "application/octet-stream"
        }
        Content(stream.readAllBytes(), type)
    }

    override fun dispose() {
        server.stop(0); pages.clear()
    }

    companion object {
        fun validResourcePath(path: String): Boolean =
            path.isNotBlank() && '\\' !in path && '\u0000' !in path && path.split('/').none { it == ".." || it == "." }

        fun trustedFrame(url: String): Boolean = runCatching {
            val uri = URI(url)
            uri.scheme.equals("https", true) && uri.userInfo == null && uri.port in listOf(-1, 443) &&
                io.github.yungyu16.algostack.domain.CodeDojo.fromHostname(uri.host.orEmpty()) != null
        }.getOrDefault(false)

        fun descriptionPage(template: String, content: String): Content = Content(
            template.replace("<!--descriptionContent-->", content).toByteArray(Charsets.UTF_8),
            "text/html; charset=utf-8", contentPolicy(template)
        )

        fun articlePage(template: String, content: String, dojo: String): Content {
            fun encode(value: String) = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
            val html = template.replace("{{articleContent}}", encode(content)).replace("{{codeDojo}}", encode(dojo))
            return Content(html.toByteArray(Charsets.UTF_8), "text/html; charset=utf-8", contentPolicy(html))
        }

        /** 仅允许构建模板中的内联脚本，题面/题解里的脚本与事件处理器不能加入许可列表。 */
        fun contentPolicy(trustedTemplate: String): String {
            fun hash(text: String) = "'sha256-${
                Base64.getEncoder()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))
            }'"

            val document = Jsoup.parse(trustedTemplate)
            val hashes = document.select("script:not([src])").map { hash(it.data()) } + document.select("[onload]")
                .map { hash(it.attr("onload")) }
            return "default-src 'none'; script-src 'self' 'unsafe-hashes' ${hashes.joinToString(" ")}; " +
                "style-src 'self' 'unsafe-inline'; img-src https: data:; font-src 'self' data:; " +
                "frame-src https://leetcode.cn https://*.leetcode.cn; " +
                "base-uri 'none'; form-action 'none'; connect-src 'none'"
        }
    }
}
