package io.github.yungyu16.algostack.execution

import io.github.yungyu16.algostack.domain.ChallengeSourceException
import io.github.yungyu16.algostack.leetcode.LeetCodeClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import java.util.Collections
import java.util.IdentityHashMap

/** 只格式化受控信息；不调用 Throwable.toString，避免异常消息带出响应、代码或凭据。 */
internal fun initializationFailureDetails(failure: Throwable): String = buildString {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var current: Throwable? = failure
    var depth = 0
    while (current != null && seen.add(current) && depth++ < 8) {
        val error = current
        appendLine("${if (depth == 1) "exception" else "caused_by"}=${error.javaClass.name}: ${safeInitializationMessage(error)}")
        error.stackTrace.take(16).forEach { frame -> appendLine("  at $frame") }
        current = error.cause
    }
    if (current != null) appendLine("remaining_causes=omitted")
}

private fun safeInitializationMessage(error: Throwable): String = when (error) {
    is ChallengeSourceException -> error.reason.description
    is TimeoutCancellationException -> "初始化等待超时；检查网络及 IDEA 任务状态，确认目标文件后再决定是否重试"
    is CancellationException -> "初始化任务已取消；检查项目关闭、插件卸载或上游取消，目标文件可能已经写入"
    is LeetCodeClient.HttpException -> "力扣请求返回 HTTP ${error.status}；检查网络、代理或访问权限"
    is LeetCodeClient.InvalidResponse -> "力扣响应格式或 GraphQL 契约异常；需核对响应结构，原始响应不写入日志"
    is java.net.SocketTimeoutException -> "网络请求超时；检查 IDEA 代理与网络连通性"
    is javax.net.ssl.SSLException -> "TLS 连接失败；检查 IDEA JVM 信任链、代理证书与主机名校验"
    is java.net.UnknownHostException -> "域名解析失败；检查 DNS 与 IDEA 代理"
    is java.net.ConnectException -> "网络连接失败；检查服务可达性及 IDEA 代理"
    else -> knownInitializationMessages[error.message] ?: "异常原始消息已省略，避免泄露凭据或响应正文；结合阶段、异常链及代码位置排查"
}

private val knownInitializationMessages = mapOf(
    "Question identity mismatch" to "平台返回的题目 slug 与请求不一致",
    "File name template generated an empty path" to "内置命名模板未能生成有效文件名",
    "Template must provide a matching problem URL and one submission region" to "生成骨架的题目 URL 或唯一提交区域未通过身份检查",
    "Automation destination must stay inside the open project and conventional folder" to "生成路径越出当前项目或约定源码目录",
    "Project is not open" to "目标 IDEA 项目已关闭或正在释放",
    "Cannot refresh generated file" to "文件已生成，但 IDEA VFS 刷新失败；先检查目标文件",
    "Source identity verification failed" to "已生成文件的题目身份核验失败；先保留并检查目标文件",
    "Destination must be inside the open project" to "目标文件不在当前打开项目内部",
    "Symlink parents are not supported" to "目标路径包含不允许的软链接父目录",
    "Parent is not a directory" to "目标路径的父级不是目录"
)
