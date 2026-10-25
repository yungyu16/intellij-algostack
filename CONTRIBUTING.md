# 参与维护

题栈（AlgoStack）是仅支持力扣中国站 LeetCodeCN 的个人维护版 IDEA 插件。开发环境与命令见[开发指南](docs/开发指南.md)，目录边界与数据兼容约定见[工程结构](docs/工程结构.md)。

修改前检查当前工作区和分支，保留并行修改，默认在当前分支操作。插件业务、IDEA 集成与 Swing UI 使用 Kotlin，WebView 使用 TypeScript；不再维护 Scala、Java UI Designer 表单或 `.form` 文件。沿用项目现有组件与代码风格，不为局部 UI 修改引入新的工具链。

每个独立需求创建单独的 Conventional Commits 中文提交，只暂存本次任务的明确路径。提交前执行 `./gradlew check buildPlugin verifyPluginProjectConfiguration`，分别说明编译、测试、打包及真实 IDEA 验收结果；项目状态与默认测试用例的验证边界见[开发指南](docs/开发指南.md#默认用例与无项目状态)。

不提交个人 IDEA 配置、凭据、Cookie、本地 API 数据和构建产物。新题沿用 Java／Go 内置目录、命名与模板约定，不恢复设置页或读取旧的自定义模板配置。不引入本地数据库或数据迁移；不得打开、改写、迁移或删除用户历史数据库，已退休的 Flyway 迁移仅保留在 Git 历史。

仅在获得明确授权后推送到个人 fork，不发布 Marketplace。
