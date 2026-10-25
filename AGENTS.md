# 题栈（AlgoStack）

- 本仓库是个人维护的 IDEA 插件，仅支持 LeetCodeCN。默认中文沟通。
- 修改前检查工作区、分支及真实调用链，保留用户并行修改。默认在当前分支操作，不创建 worktree。
- 工程入口为 Gradle Wrapper；环境和命令见 `docs/开发指南.md`，目录和兼容边界见 `docs/工程结构.md`。
- 业务与 IDEA 集成使用 Kotlin，源码放 `src/main/kotlin/`。Java 与 `.form` 放 `src/main/java/`，资源放 `src/main/resources/`。构建已移除 Scala、jOOQ、SQLite 和 Flyway，不引入本地数据库或数据迁移。
- 目标为 IDEA 2026.2，使用 JDK 25 与 Node.js 24。不要恢复旧 SBT 构建或旧平台分支。
- 保留插件 ID、LeetCodeCN 站点标识及配置名。不得打开、改写、迁移或删除用户历史数据库；已退休的数据库迁移仅保留在 Git 历史。
- 新题固定使用 Java／Go AnyVersion、内置目录和模板，不提供设置页或读取旧语言、路径、模板及 CSS 配置；不删除、改写或迁移旧配置。题目用例关联仍保留 `.idea/CodeEpiphany/`，queryCriteria 不持久化；root `.leetcode` 仅作启用标记，支持文件或目录。
- 保留提交区域、平台类名和函数签名契约。新增功能不引入其他刷题平台。
- 验证使用 `./gradlew check buildPlugin`，配置检查使用 `./gradlew verifyPluginProjectConfiguration`。默认测试不访问真实站点；编译、测试、插件打包、真实 IDEA 验收分别报告。
- 每个独立需求创建单独 Conventional Commits 中文提交。只暂存明确路径，不混入用户改动；仅获明确授权才推送到个人 fork，不发布 Marketplace。
- 不提交 `.idea/`、`.iml`、凭据、Cookie、本地 API 数据、备份和构建输出。
