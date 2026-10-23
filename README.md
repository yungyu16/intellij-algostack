# LeetCode Epiphany

面向 IntelliJ IDEA 的个人维护版刷题插件，仅支持 [LeetCode](https://leetcode.com) 和[力扣中国站](https://leetcode.cn)。基于 [WenjunHuang/CodeEpiphany](https://github.com/WenjunHuang/CodeEpiphany) 开发，采用 [Apache License 2.0](LICENSE)。

支持题目搜索与筛选、自定义语言模板、测试和提交代码、提交记录、多解法管理及题解阅读。提供已保存会话的静默恢复，以及供本机 Agent 调用的 Java／Go Challenge 初始化入口；初始化只生成骨架与关联，不实现解法或提交。两个站点的账号与项目配置独立保存。

## 安装与使用

目标平台为 **IntelliJ IDEA 2026.2**。从本仓库 CI 构建产物下载插件 ZIP，或按[开发指南](docs/开发指南.md)在本地构建。

1. 打开 **Settings → Plugins → 齿轮 → Install Plugin from Disk**，选择构建出的 ZIP，按提示重启 IDE。
2. 在 **Settings → Tools → LeetCode Epiphany** 中选择 LeetCode 或 LeetCodeCN，配置编程语言、文件保存路径和代码模板。
3. 打开插件工具窗口，登录对应站点，然后搜索题目、编码、测试和提交。

此 fork 保留原插件 ID，与上游插件互相替换，不可同时安装。安装前保存工作；已有项目的 `.idea/CodeEpiphany/` 包含本机配置与刷题数据库，建议自行备份。账号凭据不应提交到 Git。

## 开发

使用 **JDK 25、Node.js 24** 和仓库自带的 **Gradle Wrapper**。在 IDEA 中打开仓库根目录，按 Gradle 工程导入；配置 Gradle JVM 为 JDK 25。不再使用 SBT。

```bash
./gradlew buildPlugin
```

构建自动安装锁定的 npm 依赖、打包 WebView、生成词法分析器，编译 Kotlin / Java 并处理 UI Designer 表单。安装包位于 `build/distributions/`。业务与 IDEA 集成已迁移到 Kotlin，保留 Java 表单；持久化使用 SQLite JDBC，不再需要 Scala 或 jOOQ。

- [开发指南](docs/开发指南.md)：构建、调试、测试与代码生成。
- [工程结构](docs/工程结构.md)：源码边界、数据兼容性和维护约定。
- [定制功能与自动初始化](docs/定制功能与自动初始化.md)：源码移植、CLI、权限及客户端验收边界。
- [更新记录](CHANGELOG.md) · [上游历史](docs/上游更新记录.md)。
