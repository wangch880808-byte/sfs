# deploy：部署、迁移与测试脚本

运维操作的完整说明在 [`docs/04-运维/运维手册.md`](../docs/04-运维/运维手册.md)，测试工具的说明在 [`docs/03-开发/测试指南.md`](../docs/03-开发/测试指南.md)。本文件只说明各目录放什么。

## 目录

| 路径 | 内容 | 在哪里运行 |
| --- | --- | --- |
| `compose.yaml` | 服务器上的 Docker Compose 编排：前端、后端、MySQL、Redis、ES、SkyWalking | 服务器（由安装、升级脚本使用） |
| `database/migrations/V*.sql` | Flyway 增量迁移，**已发布的不能改** | 升级时自动执行 |
| `database/flyway.sh`、`baseline-gate.sh` | 执行迁移、检查基线版本 | 服务器 |
| `database/backup.sh`、`restore.sh` | 数据库备份与恢复（通常通过 `nusctl backup/restore` 调用） | 服务器 |
| `database/configure-site.sh` | 写入站点地址等配置 | 服务器 |
| `database/checks/` | 线上只读检查 SQL（历史版本用） | 服务器，只读 |
| `database/tests/` | 迁移检查 `check-v*.py`、权限矩阵 `check-permissions.py` 等 | 开发机，需要 Docker |
| `offline/build-release.sh` | 构建**全量安装包**（新服务器首次安装） | 开发机 |
| `offline/build-update.sh` | 构建**升级包**（已安装的服务器升级） | 开发机 |
| `offline/install.sh`、`upgrade.sh` | 安装包、升级包里的安装和升级入口 | 服务器 |
| `offline/nusctl` | 服务器日常管理命令：状态、日志、备份、恢复、回滚等 | 服务器 |
| `offline/configure-domain.sh` | 更换站点域名或地址 | 服务器 |
| `offline/lib/common.sh` | 上面这些脚本共用的函数，包括升级时清缓存 | — |
| `offline/update/` | 升级包里构建镜像用的 Dockerfile | — |
| `offline/tests/` | 打包、升级脚本的回归测试 | 开发机 |
| `tests/e2e/` | 本机一次性环境 `local-env.sh` 与端到端测试 `e2e_v*.py` | 开发机，**只能对本机跑** |
| `tests/perf/` | 性能对比工具 | 开发机，只能对本机跑 |
| `tests/ui/` | 界面截图与演示数据 | 开发机 |

## 规则

- **脚本要幂等**：重复执行不出错、不重复写数据。服务器上的升级可能中途失败后重跑。
- **测试脚本只对本机跑**：`tests/` 下的脚本只接受 `127.0.0.1` 或 `localhost`，不要改掉这个限制。
- **改了打包或升级脚本**，必须跑 `offline/tests/` 下的回归测试，并在测试服务器上演练一次升级和回滚。
