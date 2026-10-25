# WebView 前端

两个 JCEF 渲染页：`challengeDescription/`（题面）与 `leetCodeSolutionArticle/`（题解文章）。vite 构建产物输出到 `build/webviewResources/webview`，随插件 JAR 分发；由 Kotlin 侧 `WebResources`（本地 HttpServer）与 `WebDocumentView` 装载。

## 页面结构

| 文件 | 职责 |
|---|---|
| `challengeDescription/index.html` | 题面骨架；Kotlin 侧将题面 HTML 直接替换进 `<!--descriptionContent-->` 占位符（不转义，内容由 flexmark 渲染） |
| `leetCodeSolutionArticle/index.html` + `app.ts` + `katex_extension.ts` | 题解渲染；内容以 base64 替换进 `{{articleContent}}`、站点进 `{{codeDojo}}`（防注入，见 `WebResources.articlePage`） |
| `app.scss` | 按主题变量 `data-theme` 切换 highlight.js a11y 明暗主题 |
| `plugin/runtime.ts` | 共享运行时：normalize/滚动条样式、OverlayScrollbars、宿主控制的缩放；**每个页面入口显式 import 一次** |
| `plugin/intellijStylePlugin.ts` | 构建期注入 `intellijStyle.css` link 与同步主题回调（保证回调先定义；改为模块监听时须兼顾 CSS 已加载的情况） |

## Kotlin ↔ JS 契约（改动需双侧同步）

| 项 | Kotlin 侧 | JS 侧 |
|---|---|---|
| 缩放调用 | `WebDocumentView#zoom/actualZoom` → `executeJavaScript("window.setZoom(<n>)")` | `runtime.ts` 仅暴露 `window.setZoom` |
| 缩放范围 | `coerceIn(25.0, 500.0)` | `MIN_ZOOM/MAX_ZOOM = 25/500` |
| 缩放状态 | Kotlin 持有缩放值并限制范围；`UiDataProvider` 只提供视图实例，Action 按实例是否存在启用 | 只应用绝对缩放值，不维护相对缩放状态或向宿主回传；初始化不覆盖宿主提前设置的比例 |
| 主题注入 | `WebDocumentView#style()` 生成 `intellijStyle.css`（含 `--darkMode`、编辑器字体配色） | link onload → `onIntellijStyleLoaded()` → 设置 `data-theme` |
| 内容替换 | `WebResources.descriptionPage/articlePage` | 页面占位符（见上表） |

`manualChunks` 用于显式命名共享运行时，方便核对两页的资源依赖，不代表默认分包会丢弃模块。核对压缩产物时应追踪页面入口到共享 chunk 的依赖，不能用库函数原名是否存在作判据。类型检查和替身环境测试不覆盖真实 JCEF 的布局、主题时序、公式及代码高亮效果。

Description 保留 KaTeX 样式以兼容已有 HTML，但不启用 auto-render。题面先经过 flexmark，直接对结果启用公式解析可能改变 LaTeX 转义含义；新增公式支持必须先验证这条完整转换链。Solutions 的 Markdown／KaTeX 渲染不受此限制。

## 题解预览导航

Solutions 的正文只在面板内预览。Kotlin `WebDocumentNavigation` 与 `WebDocumentView` 负责限制跳转：允许当前宿主页面及片内锚点，阻止外链、文件跳转、新标签页和弹窗；不改变 Description 页面原有链接行为。导航策略测试不启动 JCEF，安装后仍需检查正文可正常加载、锚点可用，以及作者双击和正文外链不会打开其他页面。

## 命令

以下命令从仓库的 `webview/` 目录执行；完整插件验证与人工检查见[开发指南](../docs/开发指南.md#webview-类型检查与运行时回归)。

```bash
npm run build        # 产出 build/webviewResources/webview
npm run typecheck    # tsc --noEmit，包含共享 runtime.ts 的 DOM 与宿主全局接口
npm test             # 真实运行时源码的缩放与初始化时序回归，DOM/滚动条库使用替身
npm run check        # 类型检查与运行时回归，已接入 Gradle check 任务
npm run dev          # 本地调试（一般不需要，调试走 IDEA 内 JCEF）
```
