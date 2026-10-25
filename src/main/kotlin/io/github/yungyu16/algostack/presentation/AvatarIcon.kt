package io.github.yungyu16.algostack.presentation

import com.intellij.icons.AllIcons
import io.github.yungyu16.algostack.runtime.HttpService
import io.github.yungyu16.algostack.runtime.onEdt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.awt.Component
import java.awt.Graphics
import java.awt.image.BufferedImage
import java.io.IOException
import java.lang.ref.WeakReference
import javax.imageio.ImageIO
import javax.swing.Icon
import kotlin.coroutines.resume

class AvatarIcon(private val scope: CoroutineScope, private val url: String, private val size: Int) : Icon {
    @Volatile
    private var image: BufferedImage? = null
    private var started = false
    private var component = WeakReference<Component>(null)
    override fun getIconWidth(): Int = size
    override fun getIconHeight(): Int = size
    override fun paintIcon(c: Component?, graphics: Graphics, x: Int, y: Int) {
        image?.let { graphics.drawImage(it, x, y, size, size, c) } ?: AllIcons.General.User.paintIcon(c, graphics, x, y)
        component = WeakReference(c)
        if (!started && url.isNotBlank()) {
            started = true
            scope.launch {
                val loaded = try {
                    load()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                onEdt { image = loaded; component.get()?.repaint() }
            }
        }
    }

    private suspend fun load(): BufferedImage? = suspendCancellableCoroutine { continuation ->
        val call = HttpService.getInstance().client.newCall(Request.Builder().url(url).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val result = runCatching { ImageIO.read(it.body.byteStream()) }.getOrNull()
                    if (continuation.isActive) continuation.resume(result)
                }
            }
        })
    }
}
