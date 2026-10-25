package io.github.yungyu16.algostack.runtime

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** 网络默认策略不依赖 IDE；代理、缓存和执行器由运行时装配。 */
object HttpClientFactory {
    @JvmStatic
    fun newBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        // 保留 OkHttp 默认的证书链与主机名校验。
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0"
            ).build())
        }
}
