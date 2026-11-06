# AlgoStack

AlgoStack（题栈）是面向 IntelliJ IDEA 的个人维护版刷题插件，仅支持[力扣中国站 LeetCodeCN](https://leetcode.cn)。基于 [WenjunHuang/CodeEpiphany](https://github.com/WenjunHuang/CodeEpiphany) 开发，采用 [Apache License 2.0](LICENSE)。

支持题目搜索与筛选、Java／Go 骨架和语言工程生成、平台默认用例测试、代码提交及平台题解阅读。没有设置页、自定义用例、本地笔记或提交历史；结果输出到 Console，并提供平台详情链接。本机 Agent／脚本可以初始化骨架，但不能通过桥接执行解法、测试或提交。

## 安装与使用

目标为 IntelliJ IDEA 2026.3。源码版本与目标平台以 [gradle.properties](gradle.properties) 为准，安装包名为 `AlgoStack-<pluginVersion>.zip`；构建包版本不代表本机正在运行的版本。

1. 按[开发指南](docs/开发指南.md)构建 ZIP。通过 **Settings → Plugins → 齿轮 → Install Plugin from Disk** 安装，按提示重启。
2. 在刷题项目根目录放置 `.leetcode` 标记（文件或目录均可），重新打开项目。无标记时不显示插件工具窗口、不恢复登录或启动桥接。
3. 插件在后台静默恢复已保存的中国站会话；无凭据或恢复失败时不会自动弹窗。需要时点击题库面板的 **Login**，然后搜索题目、选择 Java 或 Go、编码、测试和提交。

新题使用固定的 Java／Go AnyVersion 约定，不读取自定义路径或模板：

| 语言 | 题目路径示例 | 缺失时创建的工程 |
|---|---|---|
| Java | `code/java/src/main/java/leetcodecn/P0001_TwoSum.java` | `code/java/pom.xml`，Java 8 Maven |
| Go | `code/go/leetcodecn/p0001_two_sum/main.go` | `code/go/go.mod`，版本取本机 Go 的 major.minor |

仅初始化所选语言，不覆盖已有源码或工程描述文件。Java 空非构造方法填入 `UnsupportedOperationException("Not implemented")` 占位，仍需自己实现；Go 空函数／方法填入 `panic("Not implemented")`。两种语言的题面注释统一包含题号、标题、原题 URL 和正文；已有题解不批量改写。工程复用和重名另存限制见[语言工程初始化](docs/工程结构.md#语言工程初始化)。

默认显示 **Console** 和 **Problems**；选中可识别的题目文件后显示 **Description**（实时题面）和 **Solutions**（平台题解）。搜索输入只保存草稿，Enter／搜索按钮才提交；筛选、排序和翻页使用已提交关键词。切换查询范围保留各自条件、回首页并恢复默认排序；公司范围未选公司时不查询。

**取消提交只停止本地跟踪，不撤回平台已受理的提交。** 取消或网络异常后先通过 Console 链接或平台记录核对，不要直接重复提交；尚未收到 ID 时可能没有链接。

**Surround region** 用于给选区添加提交标记，不是运行按钮。内置模板已有标记，正常刷题无需操作，也不要重复包围已有区域。选区／索引期间异常及筛选排障见[开发指南](docs/开发指南.md)。

## 数据与兼容边界

- 插件 ID 为 `com.yungyu.leetcode-editor`，不能与同 ID 的上游插件同时安装。
- **历史数据库和项目 XML 不读取、改写、迁移或删除。** 旧语言、路径、模板、CSS、自定义用例、笔记和提交历史不再生效或展示；queryCriteria 只保留在内存。
- 已有题目按源码真实注释中的可信中国站 URL、扩展名和唯一提交区域识别，不依赖文件路径关联。无 URL 不从 XML 兜底，显式国际站输入不自动转站。详细规则见[题目身份与旧文件](docs/工程结构.md#题目身份与旧文件)。
- 凭据由 IDEA PasswordSafe 管理，不应提交到 Git。升级或回滚只处理插件程序，不操作题解和旧数据。

## 开发与文档

开发使用 JDK 25、Node.js 24 和 Gradle Wrapper。业务与 Swing UI 使用 Kotlin，WebView 使用 TypeScript，不维护 Scala、UI Designer 表单或本地数据库。

```bash
./gradlew check buildPlugin verifyPluginProjectConfiguration
```

- [开发指南](docs/开发指南.md)：构建、测试、客户端验收、排障、安装与回滚。
- [工程结构](docs/工程结构.md)：调用链、生成与识别规则、状态及兼容契约。
- [挑战初始化桥接接口](docs/挑战初始化桥接接口.md)：外部协议、CLI、鉴权和失败处理。
- [WebView 前端说明](webview/README.md)：前端源码与 Kotlin／JS 契约。
- [参与维护](CONTRIBUTING.md) · [更新记录](CHANGELOG.md) · [上游发布](https://github.com/WenjunHuang/CodeEpiphany/releases)。
