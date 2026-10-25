package io.github.yungyu16.algostack.runtime

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import io.github.yungyu16.algostack.domain.CodeDojo
import io.github.yungyu16.algostack.leetcode.LeetCodeClient
import io.github.yungyu16.algostack.leetcode.model.LeetCodeUserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.net.HttpCookie

@Service
class AuthService {
    private data class Accepted(val cookies: List<HttpCookie>, val info: LeetCodeUserInfo)

    private val logger = Logger.getInstance(AuthService::class.java)
    private val profiles = MutableStateFlow<Map<CodeDojo, LeetCodeUserInfo>>(emptyMap())
    val states = profiles.asStateFlow()
    private val sessions = CodeDojo.entries.associateWith { dojo ->
        AuthenticationSession(
            read = { withContext(Dispatchers.IO) { CredentialsStore.read(dojo) } },
            validate = { value -> validateCandidate(dojo, CookieStore.parse(value)) },
            persist = { accepted ->
                withContext(Dispatchers.IO) {
                    CredentialsStore.write(dojo, accepted?.cookies?.let(CookieStore::encode))
                }
            },
            publish = { accepted ->
                // restore(emptyList) 不触发 CookieStore 的额外持久化回调。
                HttpService.getInstance().cookies.restore(dojo, accepted?.cookies.orEmpty())
                profiles.update { if (accepted == null) it - dojo else it + (dojo to accepted.info) }
            },
            authenticated = { isLoggedIn(dojo) },
            restoreFailed = { logger.warn("Saved authentication restore failed for ${dojo.value}", it) }
        )
    }

    fun isLoggedIn(dojo: CodeDojo): Boolean = profiles.value[dojo]?.isSignedIn == true
    fun userInfo(dojo: CodeDojo): LeetCodeUserInfo? = profiles.value[dojo]
    fun sessionRevision(dojo: CodeDojo): Long = sessions.getValue(dojo).revision

    private suspend fun validateCandidate(dojo: CodeDojo, values: List<HttpCookie>): Accepted? {
        val candidate = CookieStore().apply { restore(dojo, values) }
        val client = HttpService.getInstance().client.newBuilder().cookieJar(candidate).build()
        val info = try {
            LeetCodeClient(dojo.domain, client).getUserInfo()
        } catch (failure: LeetCodeClient.HttpException) {
            if (failure.status == 401 || failure.status == 403) return null else throw failure
        }
        return if (info.isSignedIn) Accepted(candidate.get(dojo), info) else null
    }

    // 保留旧 ensureLoggedIn 的短路调用：恢复异常不应拦住后续手动登录。
    suspend fun loadAuthentication(dojo: CodeDojo): Boolean = ensureAuthenticated(dojo)
    suspend fun ensureAuthenticated(dojo: CodeDojo, interactive: (suspend () -> Boolean)? = null): Boolean =
        sessions.getValue(dojo).ensureAuthenticated(interactive)

    suspend fun validateCookies(dojo: CodeDojo, values: List<HttpCookie>): Boolean =
        sessions.getValue(dojo).accept { validateCandidate(dojo, values) }

    suspend fun validateCookies(dojo: CodeDojo, value: String): Boolean = sessions.getValue(dojo).accept(value)
    suspend fun logout(dojo: CodeDojo) = sessions.getValue(dojo).logout()

    companion object {
        @JvmStatic
        fun getInstance(project: Project): AuthService =
            ApplicationManager.getApplication().getService(AuthService::class.java)
    }
}
