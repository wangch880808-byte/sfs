# 师砺平台（shili-platform）

周口师范学院的实训教学与能力评价平台：
- 教师建课程、布置实训任务、批改，可以用 AI 初评辅助；
- 学生在线提交；
- 平台按能力指标统计成绩，生成能力报告。

当前版本：**V1.0.1.10**（数据库迁移 V21）。各版本变化见 [CHANGELOG.md](CHANGELOG.md)。

## 目录

| 目录 | 内容 |
| --- | --- |
| [`NUS-backend/`](NUS-backend/README.md) | 后端：Spring Boot 3、JDK 17、MyBatis-Plus、MySQL 8、Redis |
| [`NUS-front/`](NUS-front/README.md) | 前端：管理端与学生 Web 端，Vue 3、vben5、ant-design-vue 4 |
| [`NUS-doc/`](NUS-doc/README.md) | 原始需求、ER 图、数据库基线 SQL |
| [`deploy/`](deploy/README.md) | 离线安装与升级、数据库迁移、e2e 与性能测试脚本 |
| [`docs/`](docs/README.md) | 全部文档：产品、架构、开发、运维、发布、交接 |
| `scripts/` | 开发辅助脚本，例如一键校验 `check-all.sh` |

微信小程序在另一个仓库 `shili-miniapp`，只依赖本仓库后端的接口。

## 我该从哪里看起

| 我是 | 先读 |
| --- | --- |
| 新加入的开发同学 | [新人上手](docs/06-交接/新人上手.md) → [环境搭建](docs/03-开发/环境搭建.md) → [开发流程](docs/03-开发/开发流程.md)（分支与 PR，强制） |
| 指导老师（验收合并 PR） | [PR 验收流程](docs/05-发布/PR验收流程.md) |
| 负责服务器的同学 | [运维手册](docs/04-运维/运维手册.md) |
| AI 智能体 | [AGENTS.md](AGENTS.md)，然后是 [当前状态](docs/06-交接/当前状态.md) |
| 老师、学生用户 | [用户操作手册](docs/01-产品/用户操作手册/用户操作手册.md) |

## 快速开始（本地开发）

需要：JDK 17、Maven 3.9、Node ≥ 20.19、pnpm 10、Docker。详细步骤见 [环境搭建](docs/03-开发/环境搭建.md)。

```bash
# 1. 先打后端 jar（约 3 分钟；只装了 mvnd 的把 mvn 换成 mvnd，参数相同）
cd NUS-backend && mvn -q -DskipTests package -pl nus-server -am && cd ..

# 2. 起本机一次性数据库、Redis 和后端
#    数据库按基线 21 初始化；口令随机生成，保存在 ~/.cache/nus-e2e
deploy/tests/e2e/local-env.sh up
deploy/tests/e2e/local-env.sh bootstrap
deploy/tests/e2e/local-env.sh backend

# 3. 起前端（另开一个终端），浏览器打开 http://localhost:5666
cd NUS-front && pnpm install && NUS_DEV_API_TARGET=http://127.0.0.1:48280/admin-api pnpm dev:antd

# 4. 改完代码后
scripts/check-all.sh
```

同一台机器上已经有人在用这套环境时，不要直接执行上面的 `up`、`down`（会删除同名容器），先看 [环境搭建](docs/03-开发/环境搭建.md) 的“同一台机器上再起一套环境”。

## 部署

学校服务器用离线升级包部署，不需要联网。安装、升级、备份、回滚见 [运维手册](docs/04-运维/运维手册.md)。

## 许可证

项目基于 ruoyi-vue-pro（后端）和 Vue Vben Admin（前端）改造，两者都是 MIT 许可证。第三方来源见 [NOTICE](NOTICE)。
