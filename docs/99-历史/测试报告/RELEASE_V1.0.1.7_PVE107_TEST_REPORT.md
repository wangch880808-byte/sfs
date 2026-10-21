# V1.0.1.7 升级与 PVE 107 测试报告

日期：2026-10-07

## 发布结果

- **目标**：PVE `<内网地址>` 上的 VM 107 `nus-clean-deploy-test`，eth0 `<测试服务器>`。
- **原版本**：V1.0.1.6。镜像为 `nus-backend:V1.0.1.6`、`nus-front:V1.0.1.6`，Flyway 17，七项服务均 healthy。
- **当前版本**：V1.0.1.7。镜像为 `nus-backend:V1.0.1.7`、`nus-front:V1.0.1.7`，Flyway 18，七项服务均 healthy；`/srv/nus/current` 指向 `releases/V1.0.1.7`。
- **升级包**：`/home/ubuntu/nus-update-V1.0.1.7-ubuntu24.04-amd64.tar.gz`，在 tmux 会话中执行 `sudo ./upgrade.sh "$PWD"`。
- **日志**：
  - 升级日志：`/srv/nus/logs/upgrade/20261007T130606Z-V1.0.1.7.log`
  - 屏幕输出：`/home/ubuntu/nus-upgrade-V1.0.1.7.log`
- **新升级脚本自动完成的备份**：
  - `.env` → `/srv/nus/config/.env.before-V1.0.1.7-20261007T130639Z`
  - 数据库（停机后备份）→ `/srv/nus/backups/db-nus-20261007T130646Z.sql.gz`
  - 保留上一版本：`releases/V1.0.1.6` 及其镜像，可执行 `sudo nusctl rollback previous` 回退。
- **用时**：13:06:06 开始，13:07:33 结束，共约 1.5 分钟。其中停机约 54 秒（13:06:39 停止前后端，13:07:33 前端健康）。
- **验收方式**：免登录验收，未在 107 上创建账号或业务数据。

## 升级包

- **文件**：`nus-update-V1.0.1.7-ubuntu24.04-amd64.tar.gz`，151531528 字节。
- **SHA256**：`0b677491806e34737af03177e97b42a95f182a691aa6896266c100c0a0c2f700`
- **校验**：包内 680 个文件，`SHA256SUMS` 在本机与 107 上均校验通过。
- **BUILD-METADATA**：
  - 构建来源：父仓库 `d9b0e56`、NUS-backend `08ceba0`、NUS-front `0b97ed8`、NUS-doc `718ced1`，均无未提交改动；
  - 后端为干净构建（`mvnd clean package`，含 745 个单测）。
- 包内 `更新说明.md` 与 `docs/RELEASE_NOTES_V1.0.1.7.md` 一致。

## 本地验证

- **后端**：
  - 单测 745 个全部通过；
  - 连接真实 MySQL 的集成测试（`-Pdb-it`，Testcontainers）27 个全部通过，覆盖提交后派发 AI 初评、并发批改与打回、重复提交、撤销、闯关快照、个别延期、公共课补发与关键 SQL；
  - 接口权限快照通过。
- **前端**：`vue-tsc` 0 错误；单测 538 个通过，样式指标与文案扫描均为 0；`build:production` 通过。
- **迁移**：
  - `check-v18.py`：up17、rerun（再次执行全部表无变化）、customized（学校自定义值保留）、fly17（Flyway 17→18）、fresh18（基线 18 与“基线 17 + V18”逐表一致），全部通过；
  - `check-v15.py`、`check-v16.py`、`check-v17.py` 回归通过。
- **权限矩阵**：`check-permissions.py` 与审查过的预期一致。
  - 新增 18 个接口：教师可用 10 个，其余仅超级管理员可用；
  - 学校教务管理员获得公共课审批及审批所需的课程查看接口；
  - 学院教务管理员与学生无变化。
- **端到端**：`e2e_v1015.py` 10 个场景、`e2e_v1017.py` 16 个场景全部通过。
- **部署脚本**：`check-upgrade-flow.sh`、`check-upgrade-helpers.sh`、`check-backup-restore.sh` 全部通过。
- **代码审查**：Codex 独立审查发现 10 个问题，端到端测试另发现 2 个问题，均已修复并补测试。前者包括并发、事务、补发恢复、汇总漏通知等，后者是切换学期与审批公共课耗时过长、提示词历史打开出错。

## PVE 107 免登录验收

- **服务**：七项服务 healthy；后端、前端为 V1.0.1.7，其余镜像未变。
- **Flyway**：`nusctl doctor` 显示 schema 版本 18，没有近期应用错误。
- **后端启动**：
  - 定时任务已同步到 Quartz；
  - 启动时续跑中断的 AI 初评（0 条）；
  - 无新增错误。
- **V18 只读核查**：
  - 新表 10 张齐全；
  - 提示词启用版本 5 条；
  - 新参数 30 项；
  - 新定时任务 3 个（截止前提醒、教师汇总、AI 调用日志清理）；
  - 原“AI 自动批改”任务已全部改为“AI 初评 + 教师复核”（剩余 0 条）；
  - 学校教务管理员已获“公共课审批”；
  - `edu_homework_record.update_time` 为毫秒精度。
- **匿名探测**（在 107 本机）：
  - 首页 200；
  - 批改队列、提示词、个别延期、学期收尾预览等新接口均返回“账号未登录”；
  - `/admin-api/system/auth/social-login-config` 只返回 `{"dingtalkEnabled": false}`。
- **说明**：开发机经本机 HTTP 代理访问 107 会得到 502，绕过代理后正常。浏览器若走代理，请把 `<测试服务器>` 加入代理例外。

## 未做

- 升级失败自动回滚与 `nusctl rollback previous` 的真机演练：脚本测试已覆盖，等登录验收完成后再在 107 上演练。
