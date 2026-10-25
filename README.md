# 题栈（AlgoStack）

面向 IntelliJ IDEA 的个人维护版刷题插件，仅支持[力扣中国站 LeetCodeCN](https://leetcode.cn)。基于 [WenjunHuang/CodeEpiphany](https://github.com/WenjunHuang/CodeEpiphany) 开发，采用 [Apache License 2.0](LICENSE)。

支持题目搜索与筛选、按内置约定生成 Java／Go 题目、使用平台默认用例测试、提交代码及平台题解阅读。不提供自定义用例编辑，不保存题目关联或用例状态，不读写 `.idea/CodeEpiphany/challenge.xml`。1.16.0 起不使用本地数据库，不提供本地 Notes/笔记和 Submissions 历史；提交结果仅输出到 Console，并提供平台详情链接。Description 实时从平台读取，Solutions 仍是平台题解文章。标记项目启动时静默恢复并校验已保存会话，本机 Agent 可初始化 Java／Go 骨架和语言工程，不实现解法或提交。仅支持力扣中国站，显式国际站输入会被拒绝，不自动转站。

当前开发版本为 **AlgoStack 1.0.3**，构建生成 `AlgoStack-1.0.3.zip`，Gradle 工程名与分发目录为 `AlgoStack`。版本号由 `gradle.properties` 的 `pluginVersion` 维护，后续独立交付递增版本并更新 [CHANGELOG](CHANGELOG.md)，不反复覆盖 `1.0.0`。本地 tag `v1.0.0` 仅标记版本初始化提交，不代表后续修改；[1.0.0 验证记录](docs/1.0.0验证记录.md)和[1.17.2 验证记录](docs/1.17.2验证记录.md)均保留对应批次的历史结论，不替代当前版本验收。

## 安装与使用

目标平台为 **IntelliJ IDEA 2026.2**。从本仓库 CI 构建产物下载插件 ZIP，或按[开发指南](docs/开发指南.md)在本地构建。

1. 打开 **Settings → Plugins → 齿轮 → Install Plugin from Disk**，选择构建出的 ZIP，按提示重启 IDE。
2. 无需设置语言、路径或模板。打开题目时选择 Java 或 Go（AnyVersion），中国站固定生成 `code/java/src/main/java/leetcodecn/P0001_TwoSum.java` 或 `code/go/leetcodecn/P0001_TwoSum/main.go`。仅在打开题目或显式初始化时创建所选语言的工程和题目：Java 创建 `code/java/pom.xml`（Java 8、标准 `src/main/java`），Go 创建 `code/go/go.mod`（`module leetcode-practice`，`go` 版本取本机 Go 的 major.minor）。已有工程描述文件与代码不覆盖，不预建另一语言。详见[初始化边界](docs/定制功能与自动初始化.md#语言工程初始化)。
3. 在刷题项目 root 放置 `.leetcode` 标记（文件或目录均可），重新打开项目即在后台恢复并校验对应站点已保存的登录态，无需先点击题库 tab。无凭据、失效或恢复失败时不自动打开登录窗口；需要时在站点题库面板点击 **Login**，或使用面板工具栏的登录入口。然后搜索题目、编码、测试和提交。无标记时不启动插件业务、登录恢复或本机初始化桥接，不展示工具窗口或题目编辑器增强。

内置模板包含纯文本题面，Java 使用 Java 8 兼容的外层类与平台原始代码，Go 每题独立目录；数字题号至少补齐四位，非数字题号沿用 slug 的驼峰类名。插件不再提供 Settings 页面，题面采用适配 IDE 主题的内置样式。供本机 Agent/脚本自动化创建题目的 HTTP 桥接接口见[挑战初始化桥接接口](docs/挑战初始化桥接接口.md)。

默认显示 **Console** 和 **Problems**。查询范围、搜索与筛选控件在英文和中文 IDE 中均显示中文，并配平台标准图标；不改变其他标签页或登录、执行菜单的语言。搜索首行是“全部题目 / 公司题单”范围选择、关键词与“搜索”按钮，Enter 与点击按钮等价；输入只保留草稿，不逐键查询。筛选可组合，修改筛选、排序或翻页立即按已提交关键词查询，不意外提交草稿；点击搜索把当前草稿和当前有效筛选一起提交并回首页。切换范围保留各自字段与草稿，回首页并恢复默认排序。短窗口下输入框收缩，筛选与账号动作单独排列。分类、题单、难度、状态与标签参与同一次请求；取消可选条件时保留其他筛选，公司时间范围必须选择一项。筛选问题的本地动作／HTTP 回归与诊断边界见[开发指南](docs/开发指南.md#筛选诊断)。

旧 `.idea/CodeEpiphany/leetcodecn.xml` 中的语言、路径、模板和 CSS 不再读取，也不删除、改写或迁移旧文件；已有源码不受影响。`.leetcode` 只用于启用。筛选、排序、分页、关键词等 queryCriteria 完全不持久化。切到非题目编辑器时只隐藏 Description/Solutions，不隐藏题库与 Console。

插件 ID 为 `com.yungyu.leetcode-editor`，与上游插件不可同时安装。升级前保存工作并自行保留旧数据。历史数据库与项目 XML 均不读取、改写、迁移或删除；旧自定义用例、笔记和提交历史不再展示。已有 Java/Go 题目仅通过源码真实注释中的可信力扣 URL、扩展名与唯一完整提交区域识别；URL 可位于 package/import 后或类、方法内部的注释中，字符串中的内容不作为身份依据（[支持边界](docs/工程结构.md#题目身份与旧文件)）。版本固定为 AnyVersion，不读取 language/version metadata 或 XML 身份。账号凭据仍由 IDEA PasswordSafe 管理，不应提交到 Git。

## 开发

使用 **JDK 25、Node.js 24** 和仓库自带的 **Gradle Wrapper**。在 IDEA 中打开仓库根目录，按 Gradle 工程导入；配置 Gradle JVM 为 JDK 25。不再使用 SBT。

```bash
./gradlew buildPlugin
```

构建自动安装锁定的 npm 依赖、打包 WebView，编译 Kotlin。安装包位于 `build/distributions/`。业务与 IDEA 集成、Swing UI 均使用 Kotlin，不再维护 Java UI Designer 表单；不再依赖 SQLite、Flyway、Scala 或 jOOQ。

- [开发指南](docs/开发指南.md)：构建、调试、测试与代码生成。
- [工程结构](docs/工程结构.md)：源码边界、数据兼容性和维护约定。
- [定制功能与自动初始化](docs/定制功能与自动初始化.md)：源码移植、CLI、权限及客户端验收边界。
- [更新记录](CHANGELOG.md) · [上游历史](docs/上游更新记录.md)。
