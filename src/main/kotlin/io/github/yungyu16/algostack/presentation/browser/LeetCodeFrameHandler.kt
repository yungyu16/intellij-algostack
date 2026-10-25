package io.github.yungyu16.algostack.presentation.browser

import io.github.yungyu16.algostack.domain.CodeDojo
import io.github.yungyu16.algostack.runtime.HttpService
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefCallback
import org.cef.handler.CefResourceHandler
import org.cef.handler.CefResourceHandlerAdapter
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefPostDataElement
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 仅代理可信力扣 iframe，复用 IDE 代理和已验证的登录 Cookie。 */
class LeetCodeFrameHandler(private val scope: CoroutineScope) : CefResourceRequestHandlerAdapter() {
    override fun getResourceHandler(browser: CefBrowser, frame: CefFrame, request: CefRequest): CefResourceHandler =
        object : CefResourceHandlerAdapter() {
            @Volatile
            private var job: Job? = null
            @Volatile
            private var call: Call? = null
            @Volatile
            private var headers: Headers = Headers.EMPTY
            @Volatile
            private var status = 200
            @Volatile
            private var length = -1
            @Volatile
            private var finished = false
            @Volatile
            private var closed = false
            @Volatile
            private var responseStarted = false
            private val chunks = ArrayBlockingQueue<ByteArray>(8)
            private val lock = Any()
            private var current = ByteArray(0)
            private var position = 0
            private var waiting: CefCallback? = null
            private fun signalReader(cancel: Boolean = false) {
                val callback = synchronized(lock) { waiting.also { waiting = null } }
                if (cancel) callback?.cancel() else callback?.Continue()
            }

            override fun processRequest(request: CefRequest, callback: CefCallback): Boolean {
                if (!WebResources.trustedFrame(request.url)) return false
                job = scope.launch {
                    try {
                        val http = HttpService.getInstance()
                        val builder = Request.Builder().url(request.url)
                        val sourceHeaders = hashMapOf<String, String>(); request.getHeaderMap(sourceHeaders)
                        sourceHeaders.filterKeys {
                            it.lowercase() !in setOf(
                                "host",
                                "cookie",
                                "content-length",
                                "connection"
                            )
                        }
                            .forEach { (name, value) -> builder.header(name, value) }
                        val dojo = CodeDojo.fromHostname(java.net.URI(request.url).host) ?: return@launch
                        builder.header("Referer", "https://${dojo.domain}/")
                        if ("graphql" in java.net.URI(request.url).path) builder.header(
                            "X-CSRFToken",
                            http.api(dojo).getCSRFToken()
                        )
                        val bytes = ByteArrayOutputStream().use { output ->
                            request.postData?.let { data ->
                                val elements = Vector<CefPostDataElement>(); data.getElements(elements)
                                elements.forEach { element ->
                                    val value = ByteArray(element.bytesCount); element.getBytes(
                                    value.size,
                                    value
                                ); output.write(
                                    value
                                )
                                }
                            }
                            output.toByteArray()
                        }
                        builder.method(
                            request.method,
                            if (request.method in listOf("GET", "HEAD")) null else bytes.toRequestBody()
                        )
                        val activeCall = http.client.newCall(builder.build()); call = activeCall
                        suspendCancellableCoroutine<Unit> { continuation ->
                            continuation.invokeOnCancellation { closed = true; activeCall.cancel(); signalReader(true) }
                            activeCall.enqueue(object : Callback {
                                override fun onFailure(call: Call, e: IOException) {
                                    if (continuation.isActive) continuation.resumeWithException(e)
                                }

                                override fun onResponse(call: Call, response: Response) {
                                    response.use {
                                        try {
                                            status = it.code; headers = it.headers
                                            length = it.body.contentLength()
                                                .takeIf { size -> size in 0..Int.MAX_VALUE.toLong() }?.toInt() ?: -1
                                            if (!continuation.isActive) return
                                            responseStarted = true; callback.Continue()
                                            // 有界队列保留背压，视频等大资源不整段读入内存。
                                            val input = it.body.byteStream()
                                            val buffer = ByteArray(8192)
                                            while (!closed && !call.isCanceled()) {
                                                val count = input.read(buffer)
                                                if (count < 0) break
                                                if (count == 0) continue
                                                val chunk = buffer.copyOf(count)
                                                while (!closed && !call.isCanceled() && !chunks.offer(
                                                        chunk,
                                                        100,
                                                        TimeUnit.MILLISECONDS
                                                    )
                                                ) {
                                                }
                                                signalReader()
                                            }
                                            finished = true; signalReader()
                                            if (continuation.isActive) continuation.resume(Unit)
                                        } catch (failure: Exception) {
                                            finished = true; signalReader(true)
                                            if (continuation.isActive) continuation.resumeWithException(failure)
                                        }
                                    }
                                }
                            })
                        }
                    } catch (cancelled: CancellationException) {
                        if (!responseStarted) callback.cancel()
                        throw cancelled
                    } catch (_: Exception) {
                        if (!responseStarted) callback.cancel(); signalReader(true)
                    }
                }
                return true
            }

            override fun getResponseHeaders(response: CefResponse, length: IntRef, redirect: StringRef) {
                response.status = status
                // 仅在明确允许的站点 iframe 中解除嵌入限制；主文档仍受自己的 CSP 限制。
                for ((name, value) in headers) if (name.lowercase() !in setOf(
                        "content-security-policy",
                        "x-frame-options"
                    )
                ) response.setHeaderByName(name, value, false)
                response.mimeType = headers["Content-Type"]?.substringBefore(';') ?: "text/html"
                length.set(this.length)
            }

            override fun readResponse(output: ByteArray, requested: Int, read: IntRef, callback: CefCallback): Boolean =
                synchronized(lock) {
                    if (position == current.size) {
                        current = chunks.poll() ?: ByteArray(0); position = 0
                    }
                    if (current.isEmpty()) {
                        read.set(0)
                        if (finished || closed) false else {
                            waiting = callback; true
                        }
                    } else {
                        val count = minOf(requested, current.size - position)
                        current.copyInto(output, 0, position, position + count); position += count; read.set(count)
                        true
                    }
                }

            override fun cancel() {
                closed = true; call?.cancel(); job?.cancel(); chunks.clear(); signalReader(true)
            }
        }
}
