package com.wenjunhuang.algostack.presentation.browser

import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.*
import com.intellij.util.ui.UIUtil
import com.wenjunhuang.algostack.domain.CodeDojo
import com.wenjunhuang.algostack.runtime.ProjectTasks
import kotlinx.coroutines.*
import org.cef.browser.*
import org.cef.handler.*
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import java.awt.BorderLayout
import javax.swing.JPanel

class WebDocumentView(private val project: Project, private val article: Boolean = false) : JPanel(BorderLayout()), Disposable, UiDataProvider {
    private val childJob = SupervisorJob(ProjectTasks.getInstance(project).scope.coroutineContext[Job])
    private val scope = CoroutineScope(ProjectTasks.getInstance(project).scope.coroutineContext + childJob)
    private val browser = try { JBCefBrowser.createBuilder().setOffScreenRendering(true).build() }
        catch (failure: Throwable) { childJob.cancel(); throw failure }
    private val server = try { WebResources() }
        catch (failure: Throwable) { childJob.cancel(); Disposer.dispose(browser); throw failure }
    private val index = if (article) "leetCodeSolutionArticle/index.html" else "challengeDescription/index.html"
    private var content = ""
    private var dojo = CodeDojo.LeetCodeCN
    private var zoom = 100.0
    private val requestHandler = object : CefRequestHandlerAdapter() {
        override fun getResourceRequestHandler(browser: CefBrowser, frame: CefFrame, request: CefRequest, navigation: Boolean,
            download: Boolean, initiator: String, disable: BoolRef): CefResourceRequestHandler? =
            if (!frame.isMain && WebResources.trustedFrame(request.url)) LeetCodeFrameHandler(scope) else null
        override fun onBeforeBrowse(browser: CefBrowser, frame: CefFrame, request: CefRequest, userGesture: Boolean, redirect: Boolean): Boolean {
            if (frame.isMain && userGesture && !redirect) {
                val uri = runCatching { java.net.URI(request.url) }.getOrNull() ?: return true
                if (uri.scheme in listOf("http", "https")) BrowserUtil.browse(uri.toString())
                if (uri.scheme == "file") {
                    val file = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(uri.path)
                    if (file != null && !file.isDirectory) ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed) com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(file, true)
                    }
                }
                return true
            }
            return false
        }
    }
    private val loadHandler = object : CefLoadHandlerAdapter() {
        override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, status: Int) { if (frame.isMain) execute("window.setZoom && window.setZoom($zoom)") }
    }
    init {
        try {
            browser.jbCefClient.addRequestHandler(requestHandler, browser.cefBrowser)
            browser.jbCefClient.addLoadHandler(loadHandler, browser.cefBrowser)
            add(browser.component, BorderLayout.CENTER)
            val toolbar = ActionManager.getInstance().createActionToolbar("AlgoStack.Sidebar.Description",
                ActionManager.getInstance().getAction("AlgoStack.Sidebar.Webview.Toolbar") as ActionGroup, true)
            toolbar.targetComponent = this; add(toolbar.component, BorderLayout.NORTH)
            ApplicationManager.getApplication().messageBus.connect(this).apply {
                subscribe(LafManagerListener.TOPIC, LafManagerListener { reload() })
                subscribe(EditorColorsManager.TOPIC, com.intellij.openapi.editor.colors.EditorColorsListener { reload() })
            }
            reload()
        } catch (failure: Throwable) {
            runCatching { Disposer.dispose(this) }.onFailure(failure::addSuppressed)
            throw failure
        }
    }
    fun setContent(text: String, site: CodeDojo) { content = text; dojo = site; reload() }
    private fun reload() {
        val template = server.template(index)
        server.set(index, if (article) WebResources.articlePage(template, content, dojo.value) else WebResources.descriptionPage(template, content))
        server.set("intellijStyle.css", style(), "text/css; charset=utf-8")
        browser.loadURL("${server.baseUrl}/$index?reload=${System.nanoTime()}")
    }
    private fun style(): String {
        val scheme = EditorColorsManager.getInstance().globalScheme
        fun color(value: java.awt.Color) = "rgb(${value.red},${value.green},${value.blue})"
        val background = UIUtil.getPanelBackground()
        val dark = background.red + background.green + background.blue < 384
        return """:root { --darkMode:${if (dark) 1 else 0}; }
            body { background:${color(background)}; color:${color(scheme.defaultForeground)}; font-family:'${scheme.editorFontName}',sans-serif;
                font-size:${scheme.editorFontSize}px; line-height:1.6; padding:12px; }
            a { color:${color(com.intellij.ui.JBColor.BLUE)}; } pre,code,blockquote { overflow:auto; }
            table td,table th { border:1px solid ${color(UIUtil.getLabelDisabledForeground())}; }
            #container pre,blockquote { border-left:2px solid ${color(UIUtil.getLabelDisabledForeground())}; padding-left:1rem; }
            #container p,#container ul { margin-bottom:1rem; } img,video { max-width:100%; height:auto; }
            ${JBCefScrollbarsHelper.getOverlayScrollbarStyle()}
        """.trimIndent()
    }
    fun zoom(multiplier: Double) { zoom = (zoom * multiplier).coerceIn(25.0, 500.0); execute("window.setZoom($zoom)") }
    fun actualZoom() { zoom = 100.0; execute("window.setZoom(100)") }
    private fun execute(script: String) { browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url, 0) }
    override fun uiDataSnapshot(sink: DataSink) { sink[KEY] = this }
    override fun dispose() {
        childJob.cancel()
        try {
            browser.jbCefClient.removeRequestHandler(requestHandler, browser.cefBrowser)
            browser.jbCefClient.removeLoadHandler(loadHandler, browser.cefBrowser)
        } finally {
            try { Disposer.dispose(browser) } finally { server.dispose() }
        }
    }
    companion object { val KEY = DataKey.create<WebDocumentView>("AlgoStack.WebDocument") }
}
