# 历史噪音检查与定向清理清单

检查日期：2026-09-05。范围：父仓库、前端、后端、小程序、数据库文档和发布产物。

以下检查清单记录清理前状态；执行进展以本节为准。“未找到引用”限于已检查的源码、配置与数据库，不代表生产数据库菜单或外部调用方不存在引用。

## 本次执行结果

2026-09-05 已按用户要求开始清理，并确认测试服务器为 `<内网地址>`。用户要求菜单先保留，故 F03 的 9 个候选文件、所有菜单、路由和业务页面均未修改。

- 已删除：前端失效 `.gitpod.yml`、上游 `tea.yaml`、后端手工账号 `test.sql`、项目复制工具 `ProjectReactor.java`。
- 已归档：`todo_doc/admin_001.md` 移至后端 `docs/archive/admin_001.md`，原始需求内容保留并标明历史用途；部署实施计划增加历史记录声明。
- 已调整：前端根包标识为 `nus-front`，移除误导性的上游项目链接，保留作者、许可证和工作区包名。Docker 便捷脚本改为只构建镜像，支持显式标签与任意工作目录，失败即退出。
- 已调整：完整包、增量包和 Compose 均要求显式业务镜像，消除 cleanup 标签默认回退；部署手册改用版本参数，补充打包参数与基线规则。
- 已调整：小程序默认 API 地址为 `<内网地址>`，保留本地覆盖能力；停止保存无消费方的刷新令牌，保存会话/退出时清除旧缓存；更正文档中草稿能力描述。
- 已调整：父仓库与后端忽略本地上传目录，父仓库忽略 `.DS_Store`。上传内容未删除，后端原有两张已跟踪图片仍保留。

验证结果：前端 `check:type` 通过（命中 Turbo 缓存），`build:production` 通过（11 个任务成功，应用构建实际执行）；后端 `mvnd -pl nus-server -am -DskipTests compile` 通过（22 个模块）。部署脚本语法检查、两个打包入口的缺参提前失败检查、Docker 便捷脚本的路径/标签/失败传播检查、Compose 显式镜像解析及缺参拒绝检查均通过。小程序使用 Node VM 模拟微信存储，验证学生/非学生会话、退出清理、旧缓存移除、默认地址及覆盖地址通过。

运行边界：未执行安装、升级、业务 SQL、Docker 镜像构建或服务启停；未进行微信真机和线上业务回归。测试服务器 SSH 首次连接因主机身份尚未验证而未进入服务器，未改动远端。

剩余项：F03 按用户要求暂缓；历史 SQL/接口兼容层保留；发布包、备份、输出资料及上传文件保留，未进行磁盘空间回收；小程序独立仓库纳管方式未调整。所有改动留在本地工作区，未提交或推送。

## 1. 当前仓库边界

| 目录 | 状态 | 清理约束 |
| --- | --- | --- |
| 父仓库 | HEAD `16539cb`；仅忽略 `/releases/` | `.upload/`、`outputs/`、`NUS-miniapp/` 显示未跟踪，不代表垃圾 |
| NUS-front | 子模块 HEAD `8719aca`，工作区干净 | 删除源码应在前端仓库独立提交，再更新父仓库指针 |
| NUS-backend | 子模块 HEAD `3c862a5`；`.upload/edu/` 未跟踪 | `.upload/` 还有两张已跟踪图片，不能整目录强删 |
| NUS-doc | 子模块 HEAD `6d3ce32`；需求、架构、多个 SQL 未跟踪 | 保护这些本地资料，禁止用 `git clean` 处理 |
| NUS-miniapp | 有独立 `.git`，自身工作区干净 | 父仓库 `.gitmodules` 未登记；应先明确纳管方式，禁止当普通未跟踪目录删除 |

## 2. 优先定向处理

### F01：失效的 Gitpod 启动配置

- 文件：`NUS-front/.gitpod.yml:6`。
- 证据：启动命令为 `pnpm run dev:play`，根 `package.json` 无此脚本，当前应用为 `apps/web-antd`。
- 动作：不再使用 Gitpod 时删除该配置；继续使用则改为现有开发入口。属于已确认失效配置。

### F02：上游元数据与旧 Docker 便捷脚本

- 文件：`NUS-front/tea.yaml`、`NUS-front/package.json:2`、`NUS-front/scripts/deploy/build-local-docker-image.sh`。
- 证据：tea 文件为上游地址声明；包名、homepage、bugs、repository 仍指向 vben；Docker 脚本仍操作 `vben-admin-local` 容器和 `vben-admin-pro` 镜像，且被 `build:docker` 引用。
- 动作：清理无用途的 tea 元数据，纠正本项目链接；Docker 脚本需连同命令入口一起修订或退役，不能单删后留下断链。
- 保留：LICENSE、第三方归属、`@vben/*` 工作区包名及内部依赖。当前 Dockerfile 已调用 `build:production`，不能把整个 `scripts/deploy/` 判断为废弃。
- 补充：前端 README 实际是 NUS 用户操作手册，应保留，不属于上游模板说明。

### F03：旧个人设置与演示图表，共 9 个候选文件

- `NUS-front/apps/web-antd/src/views/_core/profile/` 下的 `base-setting.vue`、`notification-setting.vue`、`password-setting.vue`、`security-setting.vue`。
- `NUS-front/apps/web-antd/src/views/dashboard/analytics/` 下的 5 个 `analytics-*.vue`。
- 证据：当前个人中心 `profile/index.vue` 使用 `modules/base-info.vue`、`modules/reset-pwd.vue`、`modules/profile-user.vue` 和 `modules/user-social.vue`；上述 4 个旧文件未找到静态调用。旧密码组件只弹出“密码修改成功”，不调用修改接口；旧安全组件有固定示例账号状态。5 个图表未找到静态引用，抽查图表使用硬编码数据，原 analytics 页面入口已不存在。
- 本地 `ry261-test.sql` 未命中这些路径；但 `router/access.ts:48` 用 `import.meta.glob('../views/**/*.vue')` 支持服务端菜单动态加载。
- 动作：核对实际部署数据库的 `system_menu.component` 后，按文件白名单删除这 9 个候选。不要顺带删除共享 `packages/effects/common-ui` 组件。
- 验证：前端类型检查、生产构建、教师/学生/管理员工作台和个人中心回归。

### B01：源码树里的手工账号 SQL

- 文件：`NUS-backend/nus-server/src/main/java/cn/nus/zknu/server/test.sql:1`，已跟踪。
- 证据：对固定 `NUS-prod.system_users` 执行多条账号插入，含姓名、账号、密码哈希；不是测试断言或 Flyway 迁移。在已检查的构建与部署入口中未发现引用。
- 动作：优先从源码树移除；确有留档需求则转为受控、脱敏资料。清单不复制原始账号数据。
- 验证：全库引用复核与后端编译。删除工作区文件不会删除 Git 历史，本轮不涉及历史改写。

### B02：初始化工具与早期任务草稿

- 文件：`NUS-backend/nus-server/src/test/java/cn/nus/zknu/ProjectReactor.java:18`、`NUS-backend/todo_doc/admin_001.md:1`。
- 证据：前者是批量重命名/复制项目的独立 main 工具，并非自动化测试；后者含早期菜单方案、空任务项与未收敛讨论。
- 动作：不用项目复制工具时删除；仍需要则移出测试树并明确用途。任务草稿归档并标记非现行规范，先核对未完成要求，不把历史需求直接丢弃。

### D01：部署文档与默认镜像标记未同步

- `docs/OFFLINE_DEPLOYMENT_EXECUTION_PLAN.md:203` 仍描述 `baselineVersion=0`；当前 `deploy/offline/build-release.sh:6` 和基线 SQL 第 24 行均为 13。
- `docs/OFFLINE_DEPLOYMENT_OPERATIONS.md:5` 等位置仍以 `cleanup-20260904.2` 目录作当前安装指引。
- `deploy/offline/build-release.sh:7`、`deploy/compose.yaml:176` 等位置仍默认 `cleanup-20260904.1` 镜像标签；本地已有 V1.0.0、V1.0.1 发布包，但不能仅凭包名推断其中镜像标签错误。
- 动作：执行计划标为历史记录；现行操作说明改成统一参数化版本；对照发布包清单与镜像摘要后统一默认镜像策略。不要批量字符串替换 SQL 基线版本。

## 3. 后端 SQL 与兼容层：归档或核实后处理

| 范围 | 证据 | 处理条件 |
| --- | --- | --- |
| `nus-module-edu/sql/sprint*.sql`、`nus-module-capacity/sql/sprint1_public_course_audit.sql` | 早期增量与回滚 SQL；现行完整包复制 `deploy/database/migrations/*.sql` 与 `ry261-test.sql` | 逐项比对当前结构、菜单、数据修正是否已覆盖，再归档；不能宣称内容已全部等价合并 |
| `nus-module-edu/src/main/resources/sql/edu_tables.sql` | 另一份建表入口，处于 Maven resources | 查明有无外部手工初始化用途后统一入口；不按同名表直接去重 |
| `NUS-doc/3-数据库文件/` 多个非基线 SQL | 包含未跟踪快照与备份 | 先确定来源、备份用途和保留周期，不删除 |
| `EduClassCourseServiceImpl` 旧班级维度绑定字段 | 源码明确标注兼容旧接口，当前前端 BindReqVO 使用 `classIds` | 需核对外部调用方、历史请求与部署版本，再同时清理 VO、服务分支和测试 |
| `EduHomeworkAiReviewController` 的 `/apply` | 标注“兼容接口：AI草案不得直接形成最终成绩” | 属于成绩流程保护边界，不能只凭“兼容”二字删除 |
| 旧模板等级、MIME 转短码、历史画像补建 | 服务代码有真实数据兼容逻辑 | 先证明存量数据与导入模板已完成迁移，否则保留 |

`deploy/database/migrations/V1__*.sql` 至 `V13__*.sql` 和 `ry261-test.sql` 是当前部署链路，明确排除出历史噪音删除范围。

## 4. 小程序

- `app.json` 注册 3 个主包页面和 3 个学生分包页面，页面文件齐全；services、utils、config 均可见业务引用。本轮没有发现可直接删除的废弃页面。
- `utils/storage.js:3` 的 refreshToken 仅保存和清空，没有读取或刷新请求：可列为冗余存储候选，但要先决定会话续期是否需要实现。后续若停止写入，应继续清除已有 `nus_refresh_token` 键。
- `config/index.js:11` 默认是局域网 HTTP 地址，并支持 `nus_api_base_url` 覆盖；这是当前有效开发配置，不是废弃文件。正式发布时需独立整理环境规则，不能简单删掉配置文件。
- README 关于本地草稿的文字属于后续建议；本轮未发现草稿实现，不应误写成已完成能力。
- `project.private.config.json` 已忽略，是开发者工具个人设置，不纳入业务源码删除清单。

## 5. 本地产物与可回收空间

空间来自 `du -sh`，为近似磁盘占用，不保证删除后等额释放。

| 路径 | 占用 | 建议 |
| --- | --- | --- |
| `releases/` 合计 | 约 16 GB | 最大空间来源，先对照部署版本、回滚版本和离线依赖缓存 |
| `releases/nus-offline-cleanup-20260904.1-ubuntu24.04-amd64` | 约 2.7 GB | 旧包候选；未见同名压缩包，删除前确认内容另有可用副本 |
| `releases/nus-offline-cleanup-20260904.2-ubuntu24.04-amd64` | 约 2.7 GB | 操作手册仍引用它，先修订手册并核对回滚用途 |
| V1.0.0 解包目录 / 压缩包 | 各约 2.4 GB | 验证归档完整性和校验和后可考虑只保留归档，不能假定两者相同 |
| V1.0.1 解包目录 / 压缩包 | 各约 3.0 GB | 最新发布材料优先保留，验证后再去除重复展开副本 |
| 微信 update 展开目录 / 归档 | 177 MB / 152 MB | 核对是否仍需增量升级与回滚 |
| `NUS-front/node_modules` | 约 1.3 GB | 开发依赖，不是历史源码噪音，不建议本轮清理 |
| `outputs/` | 约 4.6 MB | 含测试报告、可复用 E2E 脚本、表格与本地 DB 备份；按用途归档，不整目录删除 |
| 根 `.upload/` / 后端 `.upload/` | 约 4 MB / 44 KB | 可能被业务记录引用；需数据库引用核对后才可处理 |

`.DS_Store`、`.eslintcache`、`.flattened-pom.xml`、各模块 `target/` 是可再生产物候选，但先排除运行中的构建/本地服务依赖；不要连带删除 IDE 设置。`NUS-backend/nested:` 未发现文件，不是明显空间来源。

## 6. 建议执行批次与验收

1. 先做文档与配置修正：F01、F02、D01，明确权威构建入口、现行部署说明和历史归档说明。
2. 再做源码白名单删除：B01、B02；F03 在实际菜单核对后执行。各子仓库独立提交，父仓库记录对应指针。
3. SQL 与接口兼容清理由单独变更承载，先完成存量数据、调用方与迁移覆盖核对。
4. 发布物空间清理单独执行，保存当前可用包和回滚包，验证归档、校验和及恢复路径后处理旧副本。
5. 前端运行现有 `check:type` 和 `build:production`；后端按涉及模块运行现有编译/测试；小程序涉及会话修改时验证微信登录、扫码确认、学生附件提交和非学生权限。

本轮没有运行构建、业务测试、微信开发者工具或连接线上数据库；未完成全仓依赖图分析。清单中高置信静态遗留与待验证候选已分别注明，不能把整张清单当作批量删除脚本。
