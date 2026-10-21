# 师砺平台离线部署实施记录（历史归档）

> 本文保留 2026-09-03 起的实施方案与验收记录，包含当时的版本号、路径、校验和及命令，不作为现行安装指引。当前操作以 [离线部署与升级操作手册](OFFLINE_DEPLOYMENT_OPERATIONS.md) 为准；首次安装基线版本由发布包 `release.env` 决定，禁止直接复用本文早期的 `baselineVersion=0`。

状态：P1-07 已完成
基线日期：2026-09-03
适用仓库：`NUS-backend`、`NUS-front`、`NUS-doc`、主仓库
目标平台：Ubuntu 24.04 LTS amd64、Docker Compose、16 vCPU、16 GB 内存

## 1. 目标与边界

本计划交付一套可在无互联网环境中完成首次安装、版本升级、备份恢复和故障定位的发布包。

范围包括：

1. 在 PVE `<内网地址>` 创建可重复克隆的干净 Ubuntu 24.04 模板。
2. 使用 Docker 部署前端、后端、MySQL、Redis、Elasticsearch、SkyWalking OAP、Horizon UI，以及后端依赖的 Java Agent、FFmpeg、Python、Vosk 中文模型和中文字体。
3. 生成 `linux/amd64` 离线发布包，学校服务器不需要 Maven、Node.js、JDK 或互联网。
4. 首次安装使用 `NUS-doc/3-数据库文件/ry261-test.sql` 初始化空库。
5. 后续升级支持前端、后端、配置、数据库增量迁移和离线资源更新。
6. 提供一键安装、升级、回滚、备份、恢复、状态检查、日志查看和诊断脚本。
7. 在两个独立的干净克隆中完成断网安装和升级全链路测试。

不在本计划内：

- 不把现有测试库或远程数据库业务数据复制到学校服务器。
- 不在学校服务器现场编译前后端。
- 不把本地配置文件、源码目录、历史数据库备份或真实凭据打入发布包。
- 不在没有备份和迁移记录的情况下执行数据库变更。
- 不重复导入 `ry261-test.sql` 覆盖已有数据库。

实施遵循最小化原则：优先使用 Docker Compose、Flyway、MySQL 官方工具和系统自带能力；不重复实现成熟工具已有的版本记录、校验、开机恢复或包索引功能。每增加一个脚本、服务或状态文件，都必须证明无法由现有入口完成。

## 2. 已冻结决策

| 项目 | 决策 |
| --- | --- |
| 后端 Java | 编译与运行统一 JDK/JRE 17；现有 Dockerfile 的 JRE 21 必须改为 17 |
| 本机构建命令 | 后端统一使用 `mvnd`，不使用 `mvn` |
| 前端工具链 | Node 22、pnpm 10.28.1，必须在干净环境中构建 |
| 目标架构 | `linux/amd64`；禁止直接导出本机 ARM64 默认镜像 |
| 操作系统 | Ubuntu 24.04 LTS amd64；安装脚本发现系统或架构不匹配时立即退出 |
| 数据库版本 | MySQL 8.4 LTS，实施时固定到精确版本和 amd64 digest，并重新执行基线导入测试 |
| 初始数据库 | 原样使用已提交的 `ry261-test.sql`，只允许导入空库 |
| 数据库升级 | 使用 Flyway 执行前向增量 SQL；首次导入基线后固定 baseline 为版本 0，关闭自动 baseline，后续迁移从 V1 开始；基线 SQL 不参与升级 |
| 持久化 | 使用 `/srv/nus` 下的显式绑定目录，不依赖匿名 Docker volume |
| 对外入口 | 暴露 Nginx 的 HTTP/HTTPS 与 Horizon UI 8082；MySQL、Redis、Elasticsearch、OAP、后端及 Actuator 默认不映射到宿主机 |
| 发布方式 | 每个发布版本不可变，镜像使用唯一版本 tag；`current` 只指向当前版本 |
| 回滚方式 | 维护窗口结束前可恢复升级前备份；恢复业务写入后禁止自动数据库回滚，只允许人工处置或前向修复 |
| 密钥 | 运行时从权限为 `0600` 的环境文件读取，禁止写入镜像、JAR、SQL 基线和 Git |

## 3. 实操前核验记录

### 3.1 已通过

- 本机 Java 为 17.0.20.1，后端 POM 的 `java.version` 为 17。
- 本机 `mvnd 1.0.5`、Maven 3.9.14 可用；后端 23 模块完整打包已通过。
- PVE SSH 密钥登录可用，PVE 版本为 9.2.3，宿主机架构为 x86_64。
- PVE 当前下一 VMID 为 106，Ubuntu 24.04 amd64 Server ISO 已存在。
- PVE `c2000pro` 可用约 1.70 TB，适合模板和测试虚拟机磁盘。
- PVE 已从模板 106 克隆 VMID 108，配置为 16 vCPU、16 GiB 内存、127.5 GiB 磁盘；用于本轮完整监控断网验收。
- `vmbr0` 为 `<内网地址>` 对外桥接，网关为 `<内网地址>`。
- `ry261-test.sql` 已被 Git 跟踪，工作区与提交版本 SHA-256 一致：
  `e6aaa4450d9c4b6760db84144c1ed8d9abc6b60df3ffd9e47cf81767fbe225b0`。
- SQL 基线包含 81 张表、一个管理员账号和必要配置数据；P0 导入批次、批次明细和作业指派表为空。
- SQL 基线已真实导入一次性 MySQL 8.0 空库，81 张表全部创建成功，外键检查最终恢复开启。
- `ry261-test.sql` 未被密钥扫描命中。
- 前端实际产物目录为 `apps/web-antd/dist`。

### 3.2 已发现的阻断项

| 编号 | 问题 | 处理要求 |
| --- | --- | --- |
| B-01 | 后端 Dockerfile 使用 JRE 21 | 改为固定 digest 的 JRE 17 基础镜像 |
| B-02 | 后端镜像缺少 FFmpeg、Python、Vosk、模型和运行脚本 | 在在线构建阶段完整写入 amd64 运行镜像并做真实转写测试 |
| B-03 | 后端没有生产 profile，现有配置包含本机路径、远端地址和疑似真实凭据 | 新增生产配置并完全环境变量化；强制 prod profile，现有凭据完成轮换 |
| B-04 | 前端生产 API 域名写死 | 构建参数化，优先使用同源 `/admin-api`、`/app-api` |
| B-05 | 前端 Dockerfile 错误复制 `/app/playground/dist` | 改为 `/app/apps/web-antd/dist` |
| B-06 | 前端本地依赖目录损坏且 pnpm 子进程版本漂移 | 在固定 Node/pnpm 的干净容器中重新安装、检查和构建 |
| B-07 | 现有 Nginx 未代理后端、WebSocket，上传限制不足 | 增加反向代理、WebSocket、520 MB 请求限制、超时和访问日志 |
| B-08 | SQL 基线文件存储域名为 `127.0.0.1` | 不修改基线；首次安装后用独立环境初始化 SQL 写入 `SITE_URL` |
| B-09 | 仓库存在疑似真实凭据，部分已进入 JAR 资源 | 构建前外置并轮换；对源码、JAR、镜像和发布包逐层扫描 |
| B-10 | 当前没有数据库迁移历史机制 | 使用 Flyway 管理版本、checksum、历史记录和失败状态，不自研迁移框架 |
| B-11 | 当前没有 Compose、离线 Docker 安装包和统一运维入口 | 新建最小部署目录，只保留 `install.sh`、`upgrade.sh`、`nusctl` 三个用户入口 |
| B-12 | `local-lvm` 所在 NVMe 报告介质错误 | PVE 模板和测试盘优先放置到 `c2000pro`，不使用 `local-lvm` |
| B-13 | 基线管理员使用固定密码，原计划在服务开放后才提示修改 | 安装阶段强制设置新密码，完成前不得启动对外 Nginx |
| B-14 | FFmpeg/Vosk 通过 shell 拼接命令，未检查退出码且可能丢弃 stderr | 改为参数数组执行，检查退出码并记录限长脱敏错误信息 |
| B-15 | 开发配置开放全部 Actuator，默认 profile 为 local | prod 仅开放内部 health/info，关闭 Admin、Swagger、Druid 和测试能力，并拒绝 local/dev 启动 |

### 3.3 当前不应提交的工作区内容

- `NUS-backend/nus-server/src/main/resources/application-local.yaml` 的既有本地改动。
- `NUS-front/pnpm-workspace.yaml` 的既有本地改动。
- `NUS-doc` 中现有 README、未跟踪目录和其他 SQL 文件。

后续提交必须显式列出文件，不允许 `git add .`。

## 4. 目标运行架构

```text
客户端
  |
  v
Nginx :80/:443
  |-- 静态前端
  |-- /admin-api -> backend:48080
  |-- /app-api   -> backend:48080
  |-- /infra/ws  -> backend:48080 (WebSocket)
  |
  +-- backend (JRE 17 + SkyWalking Java Agent + FFmpeg + Python/Vosk)
        |-- mysql:3306 仅内部网络
        |-- redis:6379 仅内部网络
        |-- /app/uploads  -> /srv/nus/data/uploads
        |-- /app/logs     -> /srv/nus/logs/backend
        |-- /app/tmp      -> /srv/nus/data/tmp

SkyWalking Horizon UI :8082
  |-- skywalking-oap:12800/17128 仅监控内部网络
        |-- elasticsearch:9200 仅监控内部网络

Nginx
  |-- /druid/ -> backend:48080（SQL 与连接池监控）
  |-- /admin/ -> backend:48080（Spring Boot Admin JVM 监控）
```

宿主机目录：

```text
/srv/nus/
  current/                 当前发布版本软链接
  releases/<version>/      不可变发布文件
  config/.env              运行配置和密钥，0600
  data/mysql/              MySQL 数据
  data/redis/              Redis AOF 数据
  data/elasticsearch/      SkyWalking 指标与链路数据
  data/horizon/            Horizon UI 本地状态
  data/uploads/            业务上传文件
  data/tmp/                AI 审阅临时文件
  logs/backend/            后端主日志和错误日志
  backups/                 升级前和人工备份
```

Nginx、MySQL、Redis 使用 Docker 标准输出和统一日志轮转，不再额外维护第二套宿主机日志文件。后端保留文件日志，是因为需要按 `traceId` 快速检索并保留独立 ERROR 日志。

16C16G 默认资源预算：

| 服务 | 建议上限 |
| --- | --- |
| backend | 2.5 GiB，JVM `-Xms512m -Xmx2g` |
| mysql | 2.5 GiB |
| redis | 512 MiB，配置淘汰策略和 AOF |
| nginx | 256 MiB |
| elasticsearch | 3 GiB，JVM `-Xms2g -Xmx2g` |
| skywalking-oap | 1.5 GiB，JVM 最大 1 GiB |
| skywalking-ui | 1 GiB |
| OS、Docker、页缓存与突发余量 | 约 3.7 GiB |

## 5. 离线发布包结构

```text
nus-offline-<version>-ubuntu24.04-amd64/
  release.env
  SHA256SUMS
  README.txt
  install.sh
  upgrade.sh
  nusctl
  lib/common.sh
  runtime/
    compose.yaml
    nginx.conf
    application-prod.yaml
    redis.conf
    mysql.cnf
  apt/*.deb
  images.tar
  database/
    baseline/ry261-test.sql
    migrations/V*.sql
  licenses/
```

只保留三个用户入口：

- `install.sh`：首次安装。
- `upgrade.sh`：升级当前安装。
- `nusctl`：`start`、`stop`、`restart`、`status`、`logs`、`doctor`、`backup`、`restore`、`rollback-app`、`cleanup`。

`release.env` 使用严格的 `KEY=VALUE` 白名单格式，只记录发布版本、构建时间、四个仓库 Git SHA、目标 OS/架构、镜像 tag 和 image ID、SQL 基线版本、Flyway 基线版本、Vosk 模型版本和最低资源要求。所有文件完整性统一由根目录 `SHA256SUMS` 负责，不再为镜像和数据库分别维护重复 manifest。

后端、前端、MySQL、Redis、Flyway、Elasticsearch、SkyWalking OAP 和 Horizon UI 镜像通过一个 `images.tar` 一次性导入。Flyway 只在安装和升级时运行，不作为常驻服务。Docker `.deb` 及其依赖直接放在 `apt/`，使用本地文件安装，不生成 `Packages.gz`。容器使用 Compose 的 `restart: unless-stopped` 随 Docker 自动恢复，不增加自定义 systemd 服务。

## 6. 安装与升级契约

### 6.1 `install.sh`

1. 校验根目录 `SHA256SUMS`，任何不一致立即退出。
2. 按固定白名单解析 `release.env`，拒绝未知键、重复键和非法值，不直接 `source` 文件。
3. 校验 Ubuntu 24.04、amd64、root/sudo、端口、时间和目录权限。磁盘要求为：
   `当前上传数据 + 2 × 数据库大小 + 发布包大小 + 20 GiB`；首次安装不得低于 100 GiB 可用空间，`DATA_ROOT` 可指向独立数据盘。
4. 使用 `apt install ./apt/*.deb` 安装包内固定版本 Docker Engine、CLI、containerd、Buildx、Compose 插件及完整依赖，不创建本地 APT 仓库。
5. 创建 `/srv/nus` 目录和权限，生成权限为 `0600` 的运行配置；数据库和 Redis 密码自动生成，不在终端回显。
6. 一次性 `docker load -i images.tar`，核对镜像 tag、架构和 image ID。
7. 只启动 MySQL 并等待健康检查。空库进入初始化；目标库非空且没有受信任的 Flyway 基线时立即拒绝；已有可信安装只验证并跳过基线导入。
8. 仅对空库原样导入 `ry261-test.sql`，核对 81 张表、管理员种子和关键空表，然后执行 Flyway `baseline -baselineVersion=0`；配置始终保持 `baselineOnMigrate=false`。
9. 运行独立、幂等的站点配置步骤，写入 `SITE_URL` 和本地文件存储路径；不修改原基线文件。
10. 安装器交互式读取两次管理员新密码，或读取权限为 `0600` 的密码文件。通过后端一次性 bootstrap 模式生成密码哈希并更新管理员；密码文件随后删除。此步骤完成前不得启动 Nginx。
11. 启动 Redis 和后端，只在 Docker 内部网络执行健康检查；确认 prod profile、Java 17、MySQL、Redis、FFmpeg 和 Vosk 就绪。
12. 最后启动 Nginx，验证登录页、后端健康接口和版本号，输出访问地址及 `nusctl` 常用命令。

脚本必须可重复执行：已完成步骤只验证，不破坏现有数据；参数变化必须明确展示并要求确认。

### 6.2 `upgrade.sh`

1. 校验 `SHA256SUMS`，解析当前和目标 `release.env`，拒绝降级、重复版本和不支持的跨版本升级。
2. 执行 Flyway `validate` 和 `info`，校验已执行迁移 checksum、目标迁移顺序及磁盘余量。
3. 自动备份数据库、运行配置和当前发布指针；数据库备份必须非空、可读取并包含关键表。
4. 导入新的 `images.tar`，保留旧版本目录和旧镜像 tag。
5. 停止 Nginx 和后端，进入明确的停写维护窗口；MySQL 保持运行。
6. 执行 Flyway `migrate`。任何迁移失败立即停止，不启动新应用，并保留 Flyway 错误状态供诊断。
7. 切换 `current` 到新版本，启动 Redis、后端并执行内部健康检查，最后启动 Nginx。
8. 验证登录、前端、API、WebSocket、上传下载、模板下载、版本号和 Flyway 版本。
9. 验证成功后结束维护窗口，保留上一版本和升级前备份。

升级包允许只更新前端、只更新后端、只更新数据库或组合更新，但仍必须生成完整 `release.env` 和 `SHA256SUMS` 并通过同一套门禁。

### 6.3 回滚边界

- 未执行数据库迁移，或本次发布不含数据库迁移：使用 `nusctl rollback-app` 切回上一版本并健康检查。
- 已执行数据库迁移但尚未重新开放 Nginx：升级脚本可恢复升级前备份，再切回旧应用。
- Nginx 已恢复且业务重新产生写入：禁止脚本自动恢复旧数据库，只允许停机后的人工恢复决策或前向修复，避免丢失新数据。
- 回滚前保留失败现场的日志、Compose 状态、Flyway 信息和数据库备份。
- 失败版本和备份不自动删除；清理由 `nusctl cleanup` 按保留策略执行。

## 7. 运维与日志要求

只提供一个日常运维入口：

```text
./nusctl start|stop|restart|status
./nusctl logs backend [--error] [--follow] [--since 30m] [--trace-id ID]
./nusctl logs nginx|mysql|redis [--follow]
./nusctl logs elasticsearch|skywalking-oap|skywalking-ui [--follow]
./nusctl doctor
./nusctl monitor-status|monitor-restart|monitor-credentials
./nusctl backup [--with-uploads]
./nusctl restore <backup>
./nusctl rollback-app
./nusctl cleanup
```

运维要求：

- 后端日志写入宿主机，主日志和 ERROR 日志可直接 grep，保留现有 `traceId`。
- Docker `json-file` 日志统一设置 `max-size` 和 `max-file`，防止容器日志占满磁盘。
- Nginx 访问日志写入标准输出，包含请求时间、状态码、上游耗时和 `X-Trace-Id`。
- `nusctl doctor` 收集版本、磁盘、内存、容器状态、健康检查、最近错误和 Flyway 版本，不收集密钥。
- 普通备份包含数据库和配置；`--with-uploads` 生成完整备份。默认只保留最近 7 份，清理前至少保留一个已验证可恢复的完整备份。
- 提供 70%/85% 磁盘占用提示；低于升级所需空间时拒绝升级。
- MySQL、Redis 不暴露宿主机端口；运维通过 `nusctl` 进入容器。
- 三套监控共用安装器生成的独立监控账号；执行 `nusctl monitor-credentials` 查看。Horizon 因官方安全响应头禁止 iframe，管理端使用外部新窗口打开。
- SkyWalking 数据默认保留 7 天链路记录和 15 天指标；业务容器在 OAP 或 Elasticsearch 故障时继续提供核心服务。

## 8. 分阶段任务清单

状态只允许：`待开始`、`进行中`、`已完成`、`阻断`。后续对话先读取本文件，再只推进一个“进行中”任务。

| ID | 任务 | 依赖 | 状态 | 完成证据 |
| --- | --- | --- | --- | --- |
| P0-01 | 固化本计划、当前 Git SHA、工作区保护清单 | 无 | 已完成 | 本计划文件和独立主仓库提交 |
| P0-02 | 后端生产安全：prod 配置、凭据外置、管理员 bootstrap、外部命令执行修复 | P0-01 | 已完成 | 后端提交、prod 真实启动、一次性密码 bootstrap、命令安全测试、源码/JAR 凭据扫描均通过 |
| P0-03 | 后端 JRE 17 amd64 镜像，集成 FFmpeg、Python、Vosk 和中文字体 | P0-02 | 已完成 | 后端提交 `327f5d2`；镜像 `sha256:f63e915e3bb92a0091575bace2f67ab3bfc29d3c1b7d48887a46950c7a0c9839`，`linux/amd64`；容器内 Java 17、FFmpeg/ffprobe、Python/Vosk、Noto CJK 和 Vosk 中文模型检查通过；官方 WAV 实际转写和 FFmpeg 归一化通过 |
| P0-04 | 前端固定 Node/pnpm 干净构建，修复 Dockerfile 和生产 Nginx | P0-01 | 已完成 | 前端提交 `34fd849`；镜像 `sha256:4cc1172a1c518cbddf8b03348bf067cdd807e18e929bbcc732e9ba5061c5310b`，`linux/amd64`；Node 22/pnpm 10.28.1 干净安装，Turbo 生产构建、vue-tsc 类型检查、Nginx 配置、同源静态页/API/WebSocket 代理和 520 MB 上传限制检查通过 |
| P0-05 | 最小 Compose：持久化、内部网络、健康检查、资源和日志轮转 | P0-03、P0-04 | 已完成 | 主仓库 `deploy/compose.yaml`；`docker compose config` 通过；本地临时 MySQL 导入 81 表后四服务健康、宿主 Nginx/API 访问和全栈重启恢复通过；edge/internal 双网络和日志轮转配置已验证 |
| P0-06 | 数据库：原始基线保护、站点配置、Flyway、备份与恢复 | P0-05 | 已完成 | `deploy/database` 迁移、门禁、站点配置、备份恢复脚本；MySQL 8.4 空库基线 81 表、Flyway v0→v1、站点配置、压缩备份恢复均通过 |
| P0-07 | 最小离线包：本地 `.deb`、单一 `images.tar`、安装升级和 `nusctl` | P0-06 | 已完成 | Release A/B 构建脚本；最终包 72 项文件（含 56 个 `.deb`）全部纳入 `SHA256SUMS`，amd64 `images.tar`、基线/迁移、安装升级/nusctl 校验通过；ShellCheck 无阻断警告 |
| P1-01 | 创建 Ubuntu 24.04 干净 PVE 模板 | P0-07 | 已完成 | PVE VMID 106 `nus-ubuntu2404-clean-template`；4 vCPU/8 GB、c2000pro 63.5 GB、Ubuntu 24.04 amd64、DHCP、qemu-guest-agent；Docker/项目缺失，cloud-init/machine-id 已清理 |
| P1-02 | 创建联网 amd64 构建克隆并生成 Release A | P0-07、P1-01 | 已完成 | PVE VMID 107 `<内网地址>` 安装 Docker/Compose；Release A 生成、传输和 SHA256 校验通过 |
| P1-03 | 干净克隆 A 断网首次安装验收 | P1-02 | 已完成 | PVE VMID 108 `<内网地址>`；删除默认路由后仅使用包内 `.deb`/`images.tar` 安装，四服务健康、首页/API/Flyway v1 通过 |
| P1-04 | 生成含前端、后端和 DB 增量的 Release B | P1-03 | 已完成 | 新增 V2 `release_marker` 迁移并生成 `/tmp/nus-release-b-20260902`，包内 SHA256 全部通过 |
| P1-05 | 干净克隆 B 安装 A 后离线升级 B | P1-03 | 已完成 | VMID 107 执行 Release B 升级，Flyway v1→v2、`release_marker` 列、升级前备份、服务健康和 API 访问通过 |
| P1-06 | 独立复核发布包并交付运行和应急手册 | P1-05 | 已完成 | VMID 109（<内网地址>）由模板 106 全新克隆；最终包断网首次安装、管理员 bootstrap、重复安装、离线升级、备份、整机重启恢复和持久化 `nusctl` 全部通过 |
| P1-07 | 完整生产监控离线交付与 16C16G 真机验收 | P1-06 | 已完成 | 最终包 `observability-20260903.11` 在模板 106 的全新 16C16G 克隆 VMID 108 完成断网首次安装、统一鉴权、深浅主题、链路采集、故障隔离、整机重启和离线升级验证 |

### 8.1 P0-02 完成记录

后端功能提交：

- `58ec912` `[fix] 外置后端生产配置与敏感凭据`
- `d3c38ba` `[fix] 修复AI外部命令执行安全`
- `b865574` `[add] 增加管理员安全初始化能力`
- `f531929` `[fix] 关闭生产环境AI模型自动配置`

验收证据：

- `mvnd clean package` 全部 23 个模块通过；`nus-module-edu` 35 项测试、`nus-server` 11 项测试全部通过。
- 外部命令使用参数数组执行；带空格和 shell 元字符参数、超时、非零退出及超长 stderr 测试通过。
- 冻结基线 `ry261-test.sql` 按 SHA-256 `35522a9224cc152387aef01a60eb16dc95b6312ffc24ef310793fbceb632611c` 导入一次性 MySQL 8.4 空库，创建 81 张表，管理员初始哈希与受信基线一致。
- 管理员 bootstrap 使用 `0600` 密码文件真实运行，以退出码 0 结束；数据库哈希更新为 BCrypt cost 12，使用项目 Spring Security 编码器验证密码匹配；第二次运行以退出码 1 拒绝重复修改。
- 普通模式以 Java 17.0.20.1 和唯一 `prod` profile 启动，管理端 `/actuator/health` 返回 `UP`，根链接仅包含 health/info；env、主端口 Actuator、Swagger 和 Druid 返回业务 404，Boot Admin 未启用。
- 生产环境关闭 Spring AI 厂商模型的启动期自动配置，业务仍由 `AiModelFactory` 按数据库配置动态创建客户端；无厂商 API Key 时 prod 可正常启动。
- 对外置前的 14 个已知敏感值逐项扫描，当前源码和最终 JAR 配置均为 0 命中；高置信云凭据和私钥规则无真实命中。
- 全部数据库和 Redis 验收只使用本机专用 Docker 容器，未连接或修改远端数据库；验收容器和临时密码文件在任务结束时删除。

### 8.2 P0-03 完成记录

实现提交：

- `327f5d2` `[update] 完善后端JRE17离线运行镜像`

镜像和运行时证据：

- 使用固定 digest 的 `eclipse-temurin:17-jre-jammy` 构建，镜像架构为 `amd64/linux`，镜像 ID/tag 摘要为 `sha256:f63e915e3bb92a0091575bace2f67ab3bfc29d3c1b7d48887a46950c7a0c9839`。
- 容器默认用户为 UID/GID `10001` 的 `nus`，入口脚本使用 `exec java`，运行目录和上传、日志、AI 临时目录已创建并归属非 root 用户。
- 容器内 `java -version` 为 Temurin 17.0.20，`ffmpeg`/`ffprobe` 可执行，Python 3.10 虚拟环境成功导入 `vosk==0.3.45`，中文 Noto 字体可枚举，模型目录存在且构建时 SHA-256 校验通过。
- 下载 Vosk 官方 `test.wav`（16 kHz 单声道，8.308 秒）后，在 amd64 镜像中真实加载中文模型并输出转写文本；FFmpeg 成功生成 16 kHz 单声道归一化 WAV。
- 本地宿主为 ARM64，验收命令显示平台提示但容器内容和退出码均正常；发布目标固定为 `linux/amd64`。

### 8.3 P0-04 完成记录

实现提交：

- `34fd849` `[fix] 修复前端生产镜像构建与代理配置`

验收证据：

- Docker 构建固定 `node:22-bookworm-slim` amd64 digest 和 `nginx:stable-alpine` amd64 digest，使用 Corepack 激活 `pnpm@10.28.1`，在干净容器中执行 `pnpm install --frozen-lockfile`。
- Turbo 按工作区依赖顺序构建 `@vben/web-antd`，最终生成 `/app/apps/web-antd/dist`；构建共 11 个任务成功，Vite 生产构建和归档产物生成通过。
- 同一 builder 镜像内执行 `vue-tsc --noEmit --skipLibCheck`，退出码 0。
- `.env.production` 改为同源 `/admin-api`，移除 API 公网域名；Nginx 配置反向代理 `/admin-api/`、`/app-api/` 和 `/infra/ws`，设置 WebSocket Upgrade、300 秒 API 超时、3600 秒 WebSocket 超时以及 520 MB 请求上限。
- Nginx `nginx -t` 在提供 `backend` 网络别名时通过；容器健康页 `/nginx-health`、静态首页和 API 代理请求均已实测，后端未启动时 API 按预期返回 502，访问日志包含请求耗时、上游耗时和 trace ID。

### 8.4 P0-05 完成记录

实现资产：

- `deploy/compose.yaml`：单一 Compose 编排 MySQL、Redis、backend、frontend，镜像固定到 amd64 digest/tag。

验收证据：

- 使用临时数据根目录和本地 `ry261-test.sql`，MySQL 8.4.6 空库成功导入 81 张表；Redis 7.4.6 健康检查通过。
- backend 以 `prod` profile 启动并通过内部 Actuator 健康检查；frontend 等待 backend healthy 后启动，宿主端口映射、`/nginx-health`、静态首页和 `/admin-api` 代理均实测通过。
- frontend 同时接入可发布端口的 `nus-edge` 与仅内部通信的 `nus-internal` 网络；MySQL、Redis、backend 未发布宿主端口。
- 四服务执行 Compose 重启后健康状态恢复，数据目录保持绑定挂载；容器日志统一使用 `json-file` 的 `max-size`/`max-file` 轮转配置。
- 所有验收数据仅存在本机临时目录，测试结束后已停止并移除 Compose 容器、网络和临时数据目录。

### 8.5 P0-06 完成记录

实现资产：

- `deploy/database/migrations/V1__offline_deployment_metadata.sql`
- `deploy/database/flyway.sh`
- `deploy/database/baseline-gate.sh`
- `deploy/database/configure-site.sh`
- `deploy/database/backup.sh`
- `deploy/database/restore.sh`

验收证据：

- 空库门禁允许初始化；导入冻结 `ry261-test.sql` 后核对 81 张表，非空库不再满足空库条件。
- 使用固定 amd64 `flyway/flyway:10.20.1-alpine` 镜像，对基线库执行 `baseline -baselineVersion=0`，再执行 `migrate` 成功应用 V1；重复迁移通过校验且无重复写入。
- 站点配置脚本只更新运行库中主本地存储配置和部署元数据，不修改 Git 中的基线 SQL；URL、文件路径更新可重复执行。
- `backup.sh` 生成 gzip SQL 和 SHA-256 校验文件；`restore.sh` 先验证 gzip，再导入并核对表数量，真实恢复通过。
- 测试使用本机临时 MySQL/数据目录，结束后已清理容器和临时数据。

### 8.6 P0-07 完成记录

实现资产：

- `deploy/offline/build-release.sh`
- `deploy/offline/install.sh`
- `deploy/offline/upgrade.sh`
- `deploy/offline/nusctl`
- `deploy/offline/lib/common.sh`

验收证据：

- Release A 已生成到 `/tmp/nus-release-a-20260902`；最终 Release B 产物位于仓库 `releases/nus-offline-release-b-20260902-ubuntu24.04-amd64/`，包含前端、后端、MySQL、Redis、Flyway 的单一 amd64 `images.tar`（约 1.3 GB）、冻结基线 SQL、V1/V2 迁移、Compose 和三个运维入口。
- 运行 `sha256sum --check SHA256SUMS` 对包内全部 72 项文件（含 56 个离线 `.deb`）校验通过；镜像保存使用 `docker save --platform linux/amd64`，避免混入宿主 ARM64 变体。
- `shellcheck` 对新增数据库和离线脚本无阻断级问题；安装脚本在缺少 Docker 且无本地 `.deb` 时明确失败，避免联网回退。
- 安装脚本创建权限受控的 `/srv/nus` 数据/日志/配置目录，生成随机密钥，空库才导入基线；管理员密码强制使用 12-16 位复杂 ASCII 密码并通过一次性容器完成 bootstrap，密码文件和临时开关不残留；升级脚本先备份再加载镜像、迁移并等待健康；`nusctl` 覆盖状态、日志、诊断、备份和恢复。
- 安装或升级成功后仅复制小型运行资产到 `/srv/nus/releases/<version>`，维护 `/srv/nus/current` 和 `/usr/local/bin/nusctl`；因此 `/tmp` 中的发布包被清理或主机重启不影响日常运维。

### 8.7 P1-07 完成记录

最终交付：

- 发布包：`releases/nus-offline-observability-20260903.11-ubuntu24.04-amd64/`，大小约 2.4 GiB，共 80 个文件、79 条 `SHA256SUMS` 记录，本地和 VM 108 均完成全量校验。
- `SHA256SUMS` SHA-256：`cdb2faff3c7049743379c650b26119b52ed7ff8921546cf45886d7f8b40c244d`。
- `images.tar` SHA-256：`4f425d218a532a50c57eddbe15f8364aba7a517c006a5a175349951c7ecf004a`；前端、后端及第三方镜像均固定为 `linux/amd64`。
- `ry261-test.sql` SHA-256：`e6aaa4450d9c4b6760db84144c1ed8d9abc6b60df3ffd9e47cf81767fbe225b0`；发布包包含冻结基线和 V1-V9 迁移。
- 最终镜像：后端 `nus-backend:observability-20260903.5`，前端 `nus-front:observability-20260903.5`；前端镜像 ID 为 `sha256:630e7dc84f52681c3f41bdcb593ed2a1fd127a5f3a9493925705dbefe0bf5d05`。

PVE 实测：

- 删除原 VMID 108 后，从保留的干净模板 106 重新完整克隆 VMID 108 `nus-observability-final-clean`；配置为 16 vCPU、16384 MiB 内存、127.5 GiB 磁盘，Ubuntu 24.04 amd64，DHCP 地址 `<内网地址>`。
- 新机确认 Docker 和 `/srv/nus` 均不存在。发布包经局域网上传并验证 78 项校验后删除默认路由，公网探测失败；随后只用包内 `.deb` 和 `images.tar` 完成无人值守首次安装。
- 空库导入后 Flyway 从 baseline 0 顺序应用 V1-V8，共 9 条成功历史；重复升级明确报告 V8 已是最新且不重复执行。系统用户保持 1 条，校园资讯与学期任务各 1 条，没有重复任务数据。
- 七个常驻容器全部健康，宿主只发布 80 和 8082 业务端口；后端为 Java 17、prod profile。整机重启后七个容器自动恢复，数据库、Release 指针、日志和 SkyWalking 索引均保持。
- 真实浏览器登录 Horizon 后可见一个应用服务以及 MySQL、Redis 虚拟服务和实时 RPM/延迟/SLA；Spring Boot Admin 显示 `nus-server` 单实例 `UP`；Druid 显示 Java 17.0.20、禁止 Reset，并有 Quartz 与配置查询等真实 SQL。
- `.10` 到最终 `.11` 的离线升级自动生成数据库备份；Flyway 成功校验 10 条迁移，明确报告当前 schema 为 V9、无需执行迁移。升级后 `/srv/nus/current` 指向 `observability-20260903.11`，七个容器全部健康。
- admin 有效会话可签发严格绑定 Druid、Java、SkyWalking 的一次性票据，三套监控均无需二次登录并返回真实页面；未携带监控会话访问 `/druid/`、`/admin/` 和 `:8082/` 均返回 HTTP 401。
- SkyWalking 真实浏览器完成 `Daybreak`（亮色）→ `Horizon`（深色）→ `Daybreak` 双向主题验证；票据、消费和 `/__nus_theme` 请求均进入前端访问日志，页面在两种主题下均正常展示 `nus-server`、MySQL、Redis 拓扑。
- 停止 Elasticsearch 和 OAP 后，首页与业务 API 仍可访问，前后端持续健康且重启次数为 0；`nusctl monitor-restart` 按 Elasticsearch、OAP、Horizon 顺序恢复全部监控。
- 恢复默认路由后，后端容器访问 `https://www.zknu.edu.cn/` 返回 HTTP 200，校园资讯外网出口有效。
- `.7` 到 `.8` 的初始离线升级前自动生成非空数据库备份；`nusctl logs` 的时间、错误和 trace ID 过滤均实测通过，`doctor` 在 Elasticsearch 不可用时仍继续报告 Flyway 和其他诊断信息。

### 9.1 P1-01 完成记录

- PVE `<内网地址>` 创建 VMID 106 `nus-ubuntu2404-clean-template`，资源为 4 vCPU、8192 MiB 内存、`c2000pro` 约 63.5 GiB 磁盘，网卡接入 `vmbr0`，模板克隆默认 DHCP。
- 通过 SSH 验证 Ubuntu 24.04 amd64、内核 6.8、项目目录和 Docker 均不存在；仅安装 qemu-guest-agent 方便 PVE 获取状态。
- 执行 `cloud-init clean --logs`、清空 `/etc/machine-id` 并删除 dbus machine-id，关机后转换为 PVE 模板；模板配置未包含业务数据、镜像或源码。

## 9. PVE 模板与测试方案

模板建议参数：

- 名称：`nus-ubuntu2404-clean-template`
- VMID：执行时从 `pvesh get /cluster/nextid` 动态获取，不硬编码 106。
- CPU：4 vCPU，类型 `host`。
- 内存：8192 MiB，不启用不可控的过量 balloon。
- 系统盘：100 GiB，存储 `c2000pro`，启用 discard 和 SSD 标记。
- 固件：UEFI，机器类型 q35。
- 网络：VirtIO，默认 `vmbr0`。
- 系统：Ubuntu 24.04 LTS amd64，安装 qemu-guest-agent。
- 模板中不得预装 Docker、项目文件、数据库或发布包。
- 转模板前清理机器标识、cloud-init 状态和 SSH host key，首次启动重新生成。

测试机：

- `nus-offline-install-a`：验证 Release A 首次安装和重复执行。
- `nus-offline-upgrade-b`：先安装 A，再升级 B，验证数据保留和回滚。
- 离线测试时保留局域网地址但删除默认网关和 DNS，确保可从本地访问且不能连接互联网。
- 发布包在断网前复制到测试机，测试开始后用 `curl` 明确证明公网不可达。

## 10. 验收门禁

### 10.1 构建门禁

- 后端单元测试通过，`mvnd` 全量打包成功，镜像内 `java -version` 为 17。
- 后端必须以 prod profile 启动；local/dev profile、全部 Actuator、Spring Boot Admin、Swagger 和 Druid 不得对外开放。
- 管理员 bootstrap 在 Nginx 启动前完成，默认密码、空密码和弱密码均被拒绝。
- FFmpeg/Vosk 使用参数数组执行；非零退出、超时、带空格路径和恶意文件名均有自动测试。
- 前端使用 Node 22、pnpm 10.28.1 干净安装，类型检查和生产构建通过。
- 所有最终镜像架构为 `linux/amd64`，禁止 mixed/arm64 漂移。
- Vosk 中文模型离线加载，真实 WAV 转写成功；FFmpeg/ffprobe 可用。
- PDF 中文字体嵌入和现有导入导出模板下载测试通过。
- JAR、镜像文件系统和最终发布包密钥扫描无未豁免命中。
- 最终发布包只包含 `release.env` 描述的发布资产和 `SHA256SUMS` 列出的白名单文件。

### 10.2 安装门禁

- 无网络、无 Docker 的干净 Ubuntu 24.04 amd64 可一键安装。
- 发布包只保留 `install.sh`、`upgrade.sh` 和 `nusctl` 三个用户入口，镜像通过一个 `images.tar` 导入。
- `install.sh` 重复执行不重建数据库、不覆盖上传文件、不更换密钥。
- 非空未知数据库会被拒绝，`ry261-test.sql` 不会被误执行。
- 原始 SQL 导入后 Flyway baseline 正确，后续只执行版本高于 baseline 的迁移。
- 浏览器可登录，前端、接口、WebSocket、上传下载、模板下载、导入预检和导出可用。
- 重启宿主机后所有服务自动恢复，数据、上传文件和日志保留。
- 安装成功后 `/usr/local/bin/nusctl` 在发布包目录被删除、主机重启后仍可执行。

### 10.3 升级与恢复门禁

- Release A 到 B 可同时更新前端、后端和数据库，业务种子数据与测试回填数据保留。
- 同一迁移不重复执行；修改已执行迁移 checksum 会被拒绝。
- 注入 SQL 失败、后端不健康、磁盘不足和镜像损坏时均能停止且给出明确日志。
- 维护窗口结束前的应用回滚和数据库恢复完成真实演练；恢复业务写入后脚本必须拒绝自动数据库回滚。
- 备份恢复后表数量、关键记录数、上传文件 checksum 与升级前一致。

## 11. 提交颗粒与顺序

各子模块独立提交，根仓库只提交计划、部署资产和子模块指针。建议顺序：

1. `[add] 固化离线部署实施基线`（主仓库）
2. `[fix] 外置后端生产配置与敏感凭据`（后端）
3. `[fix] 修复AI外部命令执行安全`（后端）
4. `[add] 增加管理员安全初始化能力`（后端）
5. `[fix] 关闭生产环境AI模型自动配置`（后端）
6. `[update] 完善后端JRE17离线运行镜像`（后端）
7. `[fix] 修复前端生产镜像构建与代理配置`（前端）
8. `[add] 增加最小化Docker运行编排`（主仓库部署目录）
9. `[add] 增加Flyway迁移与备份恢复能力`（主仓库部署目录）
10. `[add] 增加离线安装升级与统一运维入口`（主仓库部署目录）
11. `[test] 完成离线安装升级全链路验收`（主仓库报告）
12. `[update] 完成离线部署交付`（主仓库子模块指针与文档）

每次提交前必须执行：显式暂存、`git diff --cached --check`、暂存文件清单核对和对应测试。不得混入第 3.3 节列出的既有改动。

## 12. 后续对话续接规则

1. 每次开始先读取本文件和三个仓库 `git status`。
2. 核对当前任务 ID、依赖和完成证据，只允许一个任务为“进行中”。
3. 未通过当前任务门禁，不进入下一任务。
4. 发现新风险时先补充到“阻断项”或任务清单，再实施；不得绕过基线临时扩展范围。
5. 每完成一个任务立即更新状态、证据、Git SHA 和验证命令，不集中到最后补写。
6. 数据库基线 SHA、目标架构、JDK 17、空库初始化原则、Flyway 升级和升级前备份原则属于不可变约束，变更必须得到用户明确批准。

当前执行任务：`全部 P0/P1 离线部署任务已完成，进入交付维护`。后续变更必须新建版本并重新生成 release.env、SHA256SUMS 和升级验收记录。
