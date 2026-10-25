# leetcode-editor

面向 IntelliJ IDEA 的个人维护版刷题插件，仅支持 [LeetCode](https://leetcode.com) 和[力扣中国站](https://leetcode.cn)。基于 [WenjunHuang/CodeEpiphany](https://github.com/WenjunHuang/CodeEpiphany) 开发，采用 [Apache License 2.0](LICENSE)。

支持题目搜索与筛选、自定义语言模板、测试和提交代码及平台题解阅读。1.16.0 起不使用本地数据库，不提供本地 Notes/笔记和 Submissions 历史；提交结果仅输出到 Console，并提供平台详情链接。Description 实时从平台读取，Solutions 仍是平台题解文章。在标记项目启动时静默恢复并校验已保存会话，以及供本机 Agent 调用的 Java／Go Challenge 初始化入口；初始化只生成骨架与关联，不实现解法或提交。两个站点的账号与项目配置独立保存。

## 安装与使用

目标平台为 **IntelliJ IDEA 2026.2**。从本仓库 CI 构建产物下载插件 ZIP，或按[开发指南](docs/开发指南.md)在本地构建。

1. 打开 **Settings → Plugins → 齿轮 → Install Plugin from Disk**，选择构建出的 ZIP，按提示重启 IDE。
2. 新项目默认配置 Java 和 Go（AnyVersion），无需先填写模板。可在 **Settings → Tools → leetcode-editor** 中选择 LeetCode 或 LeetCodeCN 调整语言、路径和模板。中国站默认生成 `code/java/src/main/java/leetcodecn/P0001_TwoSum.java` 或 `code/go/leetcodecn/P0001_TwoSum/main.go`；国际站目录与 Java 包名使用 `leetcode`。仅在打开题目或显式初始化时创建所选语言的工程和题目：Java 创建 `code/java/pom.xml`（Java 8、标准 `src/main/java`），Go 创建 `code/go/go.mod`（`module leetcode-practice`，`go` 版本取本机 Go 的 major.minor）。已有工程描述文件与代码不覆盖，不预建另一语言。自定义路径须先自行建立 Maven/Go 工程，详见[初始化边界](docs/定制功能与自动初始化.md#语言工程初始化)。
3. 在刷题项目 root 放置 `.leetcode` 标记（文件或目录均可），重新打开项目即在后台恢复并校验对应站点已保存的登录态，无需先点击题库 tab。无凭据、失效或恢复失败时不自动打开登录窗口；需要时在站点题库面板点击 **Login**，或使用面板工具栏的登录入口。然后搜索题目、编码、测试和提交。无标记时不启动插件业务、登录恢复或本机初始化桥接，不展示工具窗口或题目编辑器增强。

默认模板包含纯文本题面，Java 使用 Java 8 兼容的外层类与平台原始代码，Go 每题独立目录；数字题号至少补齐四位，非数字题号沿用 slug 的驼峰类名。已有非空语言设置（包括仅 Java）原样保留；旧空配置首次加载时初始化，之后在设置中删除全部语言并保存会保持为空，不自动补回。

语言、模板和 CSS 保存在 `.idea/CodeEpiphany/leetcode.xml`、`leetcodecn.xml`，可按明确路径加入 Git；`.leetcode` 只用于启用，不存放或自动搬迁配置。筛选、排序、分页、关键词等 queryCriteria 完全不持久化。切到非题目编辑器时只隐藏 Description/Solutions，不隐藏题库与 Console。

插件 ID 为 `com.yungyu.leetcode-editor`，与上游插件不可同时安装。升级前保存工作并自行保留旧数据。新版本不打开、迁移、删除或改写任何历史数据库，旧库中的笔记和提交历史不再展示。已有 Java/Go 题目可通过头部可信力扣 URL 与提交区域识别；1.16.1 支持 package 声明在题面注释之前的旧文件，URL 仍须位于 import 和实现代码之前的注释中（[支持边界](docs/工程结构.md#题目身份与旧文件)）。旧 XML 中匹配的自定义用例仍按需读取；没有身份信息的文件不会靠旧数字 ID 猜测题目。账号凭据不应提交到 Git。

## 开发

使用 **JDK 25、Node.js 24** 和仓库自带的 **Gradle Wrapper**。在 IDEA 中打开仓库根目录，按 Gradle 工程导入；配置 Gradle JVM 为 JDK 25。不再使用 SBT。

```bash
./gradlew buildPlugin
```

构建自动安装锁定的 npm 依赖、打包 WebView、生成词法分析器，编译 Kotlin / Java 并处理 UI Designer 表单。安装包位于 `build/distributions/`。业务与 IDEA 集成使用 Kotlin，保留 Java 表单；不再依赖 SQLite、Flyway、Scala 或 jOOQ。

- [开发指南](docs/开发指南.md)：构建、调试、测试与代码生成。
- [工程结构](docs/工程结构.md)：源码边界、数据兼容性和维护约定。
- [定制功能与自动初始化](docs/定制功能与自动初始化.md)：源码移植、CLI、权限及客户端验收边界。
- [更新记录](CHANGELOG.md) · [上游历史](docs/上游更新记录.md)。
