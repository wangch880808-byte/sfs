# 智能体工作规则（师砺平台主仓库）

本文件写给在本仓库工作的 AI 智能体（Claude Code、Codex、Cursor 等），人类开发者同样适用。
Claude Code 通过 `CLAUDE.md` 读取本文件。`NUS-backend/AGENTS.md`、`NUS-front/AGENTS.md` 是对应目录的补充规则，在那两个目录里工作时也要读。

## 1. 开始工作前先读

1. `README.md`：项目是什么、目录结构。
2. `docs/06-交接/当前状态.md`：当前版本、线上版本、进行中的工作、已知问题。**上一个智能体留下的交接记录在这里。**
3. 与任务相关的文档：
   - 架构：`docs/02-架构/`；
   - 开发规范与测试：`docs/03-开发/`；
   - 运维：`docs/04-运维/`；
   - 术语：`docs/01-产品/术语表.md`。界面上的叫法以术语表为准，例如“封存”不是“归档”，“班课”不是“班级课程”。

## 2. 仓库地图

| 目录 | 内容 | 入口 |
| --- | --- | --- |
| `NUS-backend/` | 后端，Spring Boot 3，JDK 17，Maven 多模块 | `nus-server` 模块的 `NusServerApplication` |
| `NUS-backend/nus-module-edu/` | 核心教学业务：班级、班课、学期、实训任务、批改、AI 初评、统计、留言、导入导出 | |
| `NUS-backend/nus-module-capacity/` | 课程（含公共课）、能力指标 | |
| `NUS-backend/nus-module-system/` | 用户、组织、角色权限、字典、通知、登录 | |
| `NUS-backend/nus-module-infra/`、`nus-module-ai/` | 参数、文件、定时任务、日志；AI 模型、提示词、调用日志 | |
| `NUS-front/` | 管理端与学生 Web 端，Vue 3 + vben5 + ant-design-vue 4，pnpm monorepo | `apps/web-antd/` |
| `NUS-doc/3-数据库文件/ry261-test.sql` | 数据库**基线**，首次安装用 | 文件头写有 Flyway 基线版本 |
| `deploy/database/migrations/` | Flyway 增量迁移 `V*.sql` | |
| `deploy/offline/` | 离线安装包、升级包的构建脚本，服务器上的 `nusctl` | `build-update.sh`、`nusctl` |
| `deploy/tests/` | e2e、性能对比、界面截图脚本 | `e2e/local-env.sh` |
| `docs/` | 全部文档 | `docs/README.md` |

小程序在另一个仓库 `shili-miniapp`。

## 3. 常用命令

在仓库根目录执行，详细说明见 `docs/03-开发/测试指南.md`。

```bash
# 一键校验（按改动范围自动选择；提交前必跑）
scripts/check-all.sh

# 后端单元测试（有些测试依赖时区）
cd NUS-backend && TZ=Asia/Shanghai mvn -q test

# 前端：安装依赖、类型检查、单元测试、生产构建
cd NUS-front && pnpm install && pnpm check:type && pnpm test:unit && pnpm build:production

# 本机一次性环境（Docker 里的 MySQL、Redis，加后端），给 e2e 和手工测试用
deploy/tests/e2e/local-env.sh up && deploy/tests/e2e/local-env.sh bootstrap && deploy/tests/e2e/local-env.sh backend
```

## 4. 不可违反的规则

每条后面是验证方法。

1. **已发布的迁移永远不改，只新增。**
   - V1–V21 已在线上执行，改它们会导致 Flyway 校验失败、服务起不来。
   - 新迁移必须幂等（可以重复执行）：建列、建索引前先查 `information_schema`；
   - 同步更新基线 SQL，并提供 check 脚本；
   - 写法见 `docs/03-开发/数据库迁移.md`。
   - 验证：`deploy/database/tests/check-v<N>.py`。
2. **手写 SQL 每张表都要加 `deleted = 0`。**
   - MyBatis-Plus 只对 Wrapper 查询自动加逻辑删除条件；
   - 数据权限拦截器会改写 `edu_class`、`system_users`、`system_dept`，子查询里也一样。
   - 每条新的 XML SQL 都要有一条走真实拦截器链的 H2 测试，参考 `EduClassDataPermissionSqlTest`。
3. **接口权限变化必须同步快照和权限矩阵。**
   - 新增、删除接口或改 `@PreAuthorize`，要更新 `NUS-backend/nus-server/src/test/resources/security/endpoint-permissions.tsv`，并重新生成权限矩阵。
   - 验证：`EndpointPermissionsSnapshotTest`、`deploy/database/tests/check-permissions.py`。
4. **不提交任何口令、密钥、真实学生数据。**
   - 配置一律走环境变量或服务器上的 `.env`；仓库里只放 `*.example` 和 `${...}` 占位。
   - 测试数据用假名字和假号码（如 13800001111、`xxx@example.com`）。
   - 验证：`gitleaks dir .` 无告警。
5. **对用户可见的文字用中文**，遵守 `NUS-front/docs/ui-copy.md` 和术语表。
   - 验证：前端 `tests/copy` 文案扫描。
6. **e2e、性能测试只能对本机一次性环境跑，严禁指向线上服务器。** 这些脚本会造数据、切换当前学期；脚本只接受 127.0.0.1 或 localhost。
7. **版本号只增不重复**：下一个版本是 V1.0.1.11，升级脚本会拒绝已安装过的版本号。

## 5. 完成标准

一个任务做完，需要同时满足：

- `scripts/check-all.sh` 通过；涉及数据库、权限、跨端流程时，按测试指南加跑 e2e。
- 新增或修改的逻辑有测试；修 bug 先写一个能复现的测试。
- 文档已同步：
  - 用户能看到的变化写进 `CHANGELOG.md` 的“未发布”一节；
  - 操作方式变了，更新 `docs/01-产品/用户操作手册/`；
  - 运维方式变了，更新 `docs/04-运维/`。
- 会话结束或提交 PR 时，更新 `docs/06-交接/当前状态.md`：做了什么、没做完什么、下一步、注意事项。

## 6. 必须先问人再做

以下操作，先说明要做什么、影响是什么，等人确认后再做：

- 连接或操作任何线上服务器（学校服务器、测试服务器），包括只读查询；
- 新增迁移、改动数据库结构，或删除数据、文件；
- 推送任何分支、创建 PR（先说明要推送什么、PR 目标分支是什么）；
- 打版本标签，构建正式升级包；
- 修改角色、权限点、菜单；
- 引入新的依赖或升级大版本；
- 回滚线上版本（回滚会丢失升级后的数据）。

## 7. 工作方式

- **先看再改**：修改前先读相关代码和测试，弄清现有做法；优先复用脚手架已有的能力（数据权限、字典、通知、导入导出框架）。
- **一次只做一件事**：一个分支或 PR 只解决一个问题，提交信息用中文写清楚改了什么、为什么。
- **分支与合并**（强制，详见 `docs/03-开发/开发流程.md`）：
  - 只从最新的 `origin/dev` 拉 `feature/简述`、`fix/简述` 或 `docs/简述`；
  - 只推送自己的功能分支。`dev`、`master` 是保护分支，智能体**永远不推送、不合并、不 force 推送**这两个分支；
  - 改动通过 PR 进入 `dev`，PR 目标只能是 `dev`。只有指导老师在本地测试通过后才能合并，智能体不能合并任何 PR；
  - 提 PR 前先 `git rebase origin/dev`，跑 `scripts/check-all.sh`。
- **说清楚没做完的部分**：测试没跑、没法验证、有疑问的地方，在回复和交接记录里明确写出来，不要省略。
