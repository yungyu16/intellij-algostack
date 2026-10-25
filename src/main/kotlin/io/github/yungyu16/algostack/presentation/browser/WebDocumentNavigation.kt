package io.github.yungyu16.algostack.presentation.browser

internal class WebDocumentNavigation(private val article: Boolean) {
    enum class Action { Allow, Block, OpenExternally }

    // 只放行由宿主生成的当前 bootstrap URL，不以 localhost 或路径前缀作为信任边界。
    @Volatile
    var bootstrapUrl: String = ""
    val blockNewWindow: Boolean get() = article

    fun beforeBrowse(url: String, mainFrame: Boolean, userGesture: Boolean, redirect: Boolean): Action {
        if (!article) return if (mainFrame && userGesture && !redirect) Action.OpenExternally else Action.Allow
        // 保留正文内嵌资源的自动加载，但不允许用户从子 frame 导航离开。
        if (!mainFrame) return if (userGesture) Action.Block else Action.Allow
        if (bootstrapUrl.isNotEmpty()) {
            if (url == bootstrapUrl && !userGesture && !redirect) return Action.Allow
            if (url.contains('#') && url.substringBefore('#') == bootstrapUrl) return Action.Allow
        }
        return Action.Block
    }
}
