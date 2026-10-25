package com.wenjunhuang.algostack.presentation.auth

import com.wenjunhuang.algostack.runtime.*

import com.wenjunhuang.algostack.presentation.*

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.ui.tabs.*
import com.intellij.util.ui.JBUI
import com.wenjunhuang.algostack.PluginBundle
import com.wenjunhuang.algostack.domain.CodeDojo
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefCookieVisitor
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.net.HttpCookie
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.text.DefaultEditorKit
import kotlin.coroutines.resume

/** Modeless login keeps browser/cookie processing within the dialog and project lifetimes. */
class LoginDialog private constructor(
    private val ownerProject: Project,
    private val dojo: CodeDojo,
    private val completed: (Boolean) -> Unit
) : DialogWrapper(ownerProject, false, IdeModalityType.MODELESS) {
    private val finished = AtomicBoolean()
    private val logger = Logger.getInstance(LoginDialog::class.java)
    private val job = SupervisorJob(ProjectTasks.getInstance(ownerProject).scope.coroutineContext[Job])
    private val scope = CoroutineScope(ProjectTasks.getInstance(ownerProject).scope.coroutineContext + job)
    private val candidates = Channel<List<HttpCookie>>(Channel.CONFLATED)
    private val browserRetry = AuthenticationRetry()
    private val cookieText = JBTextArea(10, 20).apply {
        lineWrap = true; wrapStyleWord = true; border = JBUI.Borders.empty(5); font = JBUI.Fonts.label()
        componentPopupMenu = JPopupMenu().apply {
            add(JMenuItem(DefaultEditorKit.CutAction().apply { putValue(Action.NAME, "Cut") }))
            add(JMenuItem(DefaultEditorKit.CopyAction().apply { putValue(Action.NAME, "Copy") }))
            add(JMenuItem(DefaultEditorKit.PasteAction().apply { putValue(Action.NAME, "Paste") }))
        }
    }
    private val browserHost = JPanel(BorderLayout())
    private val cookieHost = JBScrollPane(cookieText)
    private val tabs = JBTabsFactory.createTabs(ownerProject, disposable) as JBTabsEx
    private val root = JPanel(BorderLayout()).apply { preferredSize = JBUI.size(900, 650) }
    private var browser: JBCefBrowser? = null
    private var loadHandler: CefLoadHandlerAdapter? = null
    private var validating = false

    init {
        ComponentValidator(disposable).installOn(cookieText)
        cookieText.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                ComponentValidator.getInstance(cookieText).ifPresent { it.updateInfo(null) }
                setOKActionEnabled(cookieText.text.isNotEmpty() && !validating)
            }
        })
        tabs.addTab(TabInfo(cookieHost).setText(PluginBundle.message("loginDialog.viaCookie")))
        tabs.addTab(TabInfo(browserHost).setText(PluginBundle.message("loginDialog.viaBrowser")))
        tabs.addListener(object : TabsListener {
            override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
                val isBrowser = newSelection?.component === browserHost
                getButton(okAction)?.isVisible = !isBrowser
                if (isBrowser) ensureBrowser()
            }
        }, disposable)
        root.add(tabs.component, BorderLayout.CENTER)
        Disposer.register(disposable, Disposable {
            finish(false)
            job.cancel(); candidates.close()
            browser?.let { current ->
                // Cookies for other sites may belong to other IDE windows/plugins.
                current.getJBCefCookieManager().getCefCookieManager().deleteCookies("https://${dojo.domain}/", null)
                loadHandler?.let { current.getJBCefClient().removeLoadHandler(it, current.getCefBrowser()) }
                Disposer.dispose(current)
            }
            cookieText.text = ""
        })
        scope.launch {
            for (values in candidates) {
                if (!CookieStore.hasSession(values)) continue
                val encoded = CookieStore.encode(values)
                if (!browserRetry.begin(encoded)) continue
                try {
                    if (AuthService.getInstance(ownerProject).validateCookies(dojo, values)) onEdt {
                        if (!finished.get()) { finish(true); close(OK_EXIT_CODE) }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (e: Exception) {
                    browserRetry.failed()
                    if (!finished.get()) logger.warn("Browser login validation failed for ${dojo.value}", e)
                }
            }
        }
        init()
        title = PluginBundle.message("loginDialog.title", dojo.show)
        setOKButtonText(PluginBundle.message("loginDialog.ok"))
        setOKActionEnabled(false)
    }

    override fun createCenterPanel(): JComponent = root
    override fun getPreferredFocusedComponent(): JComponent = cookieText
    override fun createActions(): Array<Action> = arrayOf(object : AbstractAction(PluginBundle.message("loginDialog.help")) {
        override fun actionPerformed(event: ActionEvent) = BrowserUtil.browse("https://github.com/WenjunHuang/CodeEpiphany/wiki/Log-in#${dojo.value}")
    }, okAction, cancelAction)

    override fun doOKAction() {
        if (validating) return
        val text = cookieText.text
        validating = true; cookieText.isEnabled = false; setOKActionEnabled(false)
        setOKButtonText(PluginBundle.message("loginDialog.validating"))
        scope.launch {
            try {
                val valid = AuthService.getInstance(ownerProject).validateCookies(dojo, text)
                onEdt {
                    if (finished.get()) return@onEdt
                    if (valid) { finish(true); close(OK_EXIT_CODE) }
                    else showCookieError(PluginBundle.message("loginDialog.cookie.error"))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { onEdt { if (!finished.get()) showCookieError(e.message.orEmpty()) } }
            finally { onEdt {
                if (!finished.get()) {
                    validating = false; cookieText.isEnabled = true
                    setOKButtonText(PluginBundle.message("loginDialog.ok")); setOKActionEnabled(cookieText.text.isNotEmpty())
                }
            } }
        }
    }
    private fun showCookieError(message: String) = ComponentValidator.getInstance(cookieText).ifPresent {
        it.updateInfo(ValidationInfo(message, cookieText)); cookieText.requestFocusInWindow()
    }
    override fun doCancelAction() { finish(false); super.doCancelAction() }
    private fun finish(success: Boolean) { if (finished.compareAndSet(false, true)) completed(success) }

    private fun ensureBrowser() {
        if (browser != null) return
        if (!JBCefApp.isSupported()) { browserHost.add(JLabel("JCEF is unavailable. Please use cookie login.")); return }
        val current = JBCefBrowserBuilder().setOffScreenRendering(true).setUrl(dojo.loginUrl).build()
        browser = current
        val handler = object : CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(browser: CefBrowser, isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean) {
                if (!isLoading) inspectCookies(current)
            }
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, status: Int) {
                if (frame.isMain) inspectCookies(current)
            }
        }
        loadHandler = handler
        current.getJBCefClient().addLoadHandler(handler, current.getCefBrowser())
        browserHost.add(current.component, BorderLayout.CENTER); browserHost.revalidate(); browserHost.repaint()
        scope.launch {
            while (isActive) { delay(1_000); inspectCookies(current) }
        }
    }
    private fun inspectCookies(current: JBCefBrowser) {
        if (finished.get()) return
        val values = arrayListOf<HttpCookie>()
        current.getJBCefCookieManager().getCefCookieManager().visitAllCookies(CefCookieVisitor { cookie, count, total, _ ->
            if (CodeDojo.fromHostname(cookie.domain) == dojo) values += HttpCookie(cookie.name, cookie.value).apply {
                domain = cookie.domain; path = cookie.path; secure = cookie.secure; isHttpOnly = cookie.httponly
            }
            if (count == total - 1) candidates.trySend(values.toList())
            true
        })
    }
    companion object {
        suspend fun login(project: Project, dojo: CodeDojo): Boolean = withContext(Dispatchers.EDT) {
            suspendCancellableCoroutine { continuation ->
                val dialog = LoginDialog(project, dojo) { if (continuation.isActive) continuation.resume(it) }
                continuation.invokeOnCancellation {
                    ApplicationManager.getApplication().invokeLater({
                        if (!dialog.finished.get()) dialog.close(CANCEL_EXIT_CODE)
                    }, ModalityState.any())
                }
                dialog.show()
            }
        }
    }
}
