package com.wenjunhuang.codeepiphany.activities

import com.wenjunhuang.codeepiphany.domain.CodeDojo
import com.wenjunhuang.codeepiphany.presentation.sidebar.SidebarPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 标记判定先于任何插件业务服务；恢复随项目启动任务取消，不依赖工具窗口是否创建。 */
internal suspend fun activateMarkedProject(
    root: String?, start: suspend () -> Unit, restore: suspend (CodeDojo) -> Boolean
) {
    if (!withContext(Dispatchers.IO) { SidebarPolicy.enabled(root) }) return
    start()
    coroutineScope {
        for (dojo in CodeDojo.entries) launch { restore(dojo) }
    }
}
