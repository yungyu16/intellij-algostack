# 参与维护

本仓库是仅支持 LeetCode / LeetCodeCN 的个人维护版。开发环境与命令见[开发指南](docs/开发指南.md)，目录边界与数据兼容约定见[工程结构](docs/工程结构.md)。

修改前检查当前工作区，保留并行修改。围绕实际需求做独立变更，遵循现有 Scala / Java / TypeScript 风格；提交使用 Conventional Commits。提交前执行 `./gradlew check buildPlugin`，并说明尚未完成的客户端验证。

不提交个人 IDEA 配置、凭据、Cookie、API 测试数据和构建产物。历史 Flyway 迁移不可改写。
