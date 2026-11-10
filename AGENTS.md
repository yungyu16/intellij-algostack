# 题栈（AlgoStack）

- 本仓库是个人维护的 IDEA 插件，仅支持 LeetCodeCN。默认中文沟通。
- 修改前检查工作区、分支及真实调用链，保留用户并行修改。默认在当前分支操作，不创建 worktree。
- 工程入口为 Gradle Wrapper。`docs/` 的核心说明按三份维护：`开发指南.md` 放构建、测试、验收、排障与安装操作，`工程结构.md` 放实现和兼容契约，`挑战初始化桥接接口.md` 放外部协议、CLI 与失败处理；WebView 局部契约维护在 `webview/README.md`。
- 业务与 IDEA 集成使用 Kotlin，源码放 `src/main/kotlin/`，资源放 `src/main/resources/`。UI 使用 Kotlin 构建 Swing 组件，不维护 Java UI Designer 表单或 `.form` 插桩配置。构建已移除 Scala、jOOQ、SQLite 和 Flyway，不引入本地数据库或数据迁移。
- 目标为 IDEA 2026.3，使用 JDK 25 与 Node.js 24。不要恢复旧 SBT 构建或旧平台分支。
- 保留插件 ID、LeetCodeCN 站点标识及配置名。不得打开、改写、迁移或删除用户历史数据库；已退休的数据库迁移仅保留在 Git 历史。
- 新题固定使用 Java／Go、内置目录和模板，不提供设置页或自定义用例，不持久化题目关联、用例或 queryCriteria；旧 XML 不读取、改写、迁移或删除。root `.leetcode` 仅作启用标记，支持文件或目录。
- 保留提交区域、平台类名和函数签名契约。新增功能不引入其他刷题平台。
- 验证使用 `./gradlew check buildPlugin`，配置检查使用 `./gradlew verifyPluginProjectConfiguration`。默认测试不访问真实站点；编译、测试、插件打包、真实 IDEA 验收分别报告。
- 每个独立需求创建单独 Conventional Commits 中文提交。涉及插件行为的独立交付递增 `gradle.properties` 的 `pluginVersion` 并新增对应 CHANGELOG 条目，不反复覆盖已交付版本；同一交付内的修正及纯文档变更不机械升版，不移动已有 tag。只暂存明确路径，不混入用户改动；仅获明确授权才推送到个人 fork。Agent 不主动发布 Marketplace；发布由用户推送 `v*` tag 经 publish 工作流执行（构建、上传、Release），凭据为仓库 secret `JETBRAINS_TOKEN`。
- 不提交 `.idea/`、`.iml`、凭据、Cookie、本地 API 数据、备份和构建输出。
