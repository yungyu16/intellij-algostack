package io.github.yungyu16.algostack.runtime

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.net.*
import io.github.yungyu16.algostack.domain.CodeDojo
import io.github.yungyu16.algostack.leetcode.LeetCodeClient
import okhttp3.Cache
import okhttp3.Credentials
import okhttp3.OkHttpClient

@Service
class HttpService : Disposable {
    private val logger = Logger.getInstance(HttpService::class.java)
    val cookies = CookieStore { dojo, values ->
        try {
            CredentialsStore.write(dojo, values.takeIf { it.isNotEmpty() }?.let(CookieStore::encode))
        } catch (e: Exception) {
            logger.warn("Failed to persist ${dojo.value} cookies", e)
        }
    }
    private val cacheFolder = FileUtil.createTempDirectory("algostack-", "http", true)
    private val cache = Cache(cacheFolder, 10L * 1024 * 1024)
    private val clientDelegate = lazy {
        HttpClientFactory.newBuilder().cookieJar(cookies).cache(cache)
            .proxySelector(IdeProxySelector(ProxyConfigurationProvider {
                ProxySettings.getInstance().getProxyConfiguration()
            }))
            .proxyAuthenticator { _, response ->
                val credentials = ProxySettings.getInstance().getStaticProxyCredentials(
                    ProxyCredentialStore.getInstance().asProxyCredentialProvider()
                )
                if (credentials == null || response.challenges()
                        .none { it.scheme.equals("OkHttp-Preemptive", true) }
                ) null
                else response.request.newBuilder().header(
                    "Proxy-Authorization",
                    Credentials.basic(credentials.userName.orEmpty(), credentials.getPasswordAsString().orEmpty())
                ).build()
            }
            .build()
    }
    val client: OkHttpClient get() = clientDelegate.value
    fun api(dojo: CodeDojo) = LeetCodeClient(dojo.domain, client)
    override fun dispose() {
        if (clientDelegate.isInitialized()) {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
        cache.close()
        FileUtil.delete(cacheFolder)
    }

    companion object {
        @JvmStatic
        fun getInstance(): HttpService = ApplicationManager.getApplication().getService(HttpService::class.java)
    }
}
