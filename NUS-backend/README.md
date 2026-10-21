# NUS-backend：师砺平台后端

> 面向：改后端代码的开发同学和 AI 智能体。
> 适用版本：V1.0.1.10（数据库迁移 V21）。
> 最后核对日期：2026-10-14。

技术栈是 Spring Boot 3、JDK 17、Maven 多模块、MyBatis-Plus、MySQL 8.4、Redis 7.4，基于 ruoyi-vue-pro 改造。规则先读 [AGENTS.md](AGENTS.md)（本目录）和根目录 [AGENTS.md](../AGENTS.md)。

## 模块

| 模块 | 内容 |
| --- | --- |
| `nus-dependencies` | 统一管理依赖版本（BOM） |
| `nus-framework` | 公共框架：Web、安全、MyBatis、数据权限、Redis、Excel、定时任务、防重复提交、监控、测试支持（`nus-common`、`nus-spring-boot-starter-*`） |
| `nus-module-system` | 用户、组织、角色权限、字典、通知、登录与社交登录 |
| `nus-module-infra` | 参数配置、文件、定时任务、日志、监控、Redis 管理 |
| `nus-module-edu` | 核心教学业务：班级、班课、学期、实训任务、批改、AI 初评、统计、留言、导入导出 |
| `nus-module-capacity` | 课程（含公共课）、能力指标 |
| `nus-module-ai` | AI 模型、提示词、调用日志、对话 |
| `nus-server` | 启动模块：主类 `cn.nus.zknu.server.NusServerApplication`、配置文件、接口权限快照测试；打包产物 `nus-server/target/nus-server.jar` |

各模块的包结构和命名见 [编码规范](../docs/03-开发/编码规范.md)，模块之间怎么配合见 [架构概览](../docs/02-架构/架构概览.md)。

## 运行

完整步骤见 [环境搭建](../docs/03-开发/环境搭建.md)。简要说明：

```bash
# 打包（本机一次性环境要用这个 jar）
mvn -q -DskipTests package -pl nus-server -am
```

- **方式一**：用 `deploy/tests/e2e/local-env.sh` 在 Docker 里起 MySQL、Redis，再以 `prod` 配置运行 jar，端口 48280。
- **方式二**：在 IDE 里启动。设置环境变量 `SPRING_PROFILES_ACTIVE=local`，主类是 `NusServerApplication`，工作目录设为 `NUS-backend`，端口 48080。

`application.yaml` 不会默认激活任何 profile，本地启动必须显式指定 `local`。

## 测试

```bash
TZ=Asia/Shanghai mvn -q test                                    # 全部单元测试（mvn 可换成 mvnd）
TZ=Asia/Shanghai mvn -q test -pl nus-module-edu -am             # 只跑一个模块
TZ=Asia/Shanghai mvn -q test -pl nus-module-edu -am -Pdb-it \
  -Dnus.it.baseline-sql=$PWD/../NUS-doc/3-数据库文件/ry261-test.sql   # 真实 MySQL 集成测试，需要 Docker 和 mysql:8.4.6 镜像
```

什么时候跑哪个测试、接口权限快照怎么更新，见 [测试指南](../docs/03-开发/测试指南.md)。提交前在仓库根目录跑 `scripts/check-all.sh`。

## 配置

| 文件 | 用途 |
| --- | --- |
| `application.yaml` | 公共配置 |
| `application-local.yaml` | 只用于本机。打包时由 `maven-jar-plugin` 排除，不会进入 `nus-server.jar` |
| `application-prod.yaml` | 生产配置，值全部来自环境变量。`nus-server/Dockerfile` 设置了 `SPRING_PROFILES_ACTIVE=prod`。服务器上的变量见 [运维手册](../docs/04-运维/运维手册.md) |

任何 yaml 里都不要写真实口令。

### local 配置的环境变量

默认值来自 `application-local.yaml`：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `NUS_LOCAL_DB_HOST` / `NUS_LOCAL_DB_PORT` | `127.0.0.1` / `3306` | 本地数据库地址 |
| `NUS_LOCAL_DB_NAME` | `ry261-test` | 本地数据库名 |
| `NUS_LOCAL_DB_USERNAME` / `NUS_LOCAL_DB_PASSWORD` | `root` / 空 | 本地数据库账号，口令只通过环境变量提供 |
| `NUS_LOCAL_REDIS_HOST` / `NUS_LOCAL_REDIS_PORT` | `127.0.0.1` / `6379` | 本地 Redis 地址 |
| `NUS_LOCAL_REDIS_DATABASE` | `0` | Redis 库编号 |
| `SPRING_DATA_REDIS_PASSWORD` | 未设置 | Redis 需要口令时再设置 |
| `NUS_SECURITY_MOCK_ENABLE` | `false` | 是否开启模拟登录，只在本地调试时临时开启 |
| `NUS_MONITOR_USERNAME` / `NUS_MONITOR_PASSWORD` | 见 yaml | 本机 Spring Boot Admin 客户端账号，只用于本机 |
| `NUS_AI_REVIEW_ENABLED` | `true` | AI 初评开关（`prod` 配置里默认 `false`） |
| `NUS_FFMPEG_COMMAND` / `NUS_FFPROBE_COMMAND` | `/opt/homebrew/bin/ffmpeg` / `/opt/homebrew/bin/ffprobe` | 音频转码、取时长 |
| `NUS_VOSK_PYTHON` | `.venv-vosk/bin/python` | 装了 vosk 的 Python，路径相对工作目录 |
| `NUS_VOSK_SCRIPT` | `scripts/vosk_transcribe.py` | 转写脚本 |
| `NUS_VOSK_MODEL_DIR` | `~/.local/share/nus-ai/vosk-model-small-cn-0.22` | 中文语音模型，用 `scripts/install_vosk_model.sh` 下载 |
| `NUS_AI_REVIEW_WORK_DIR` | `/tmp/nus-ai-review` | AI 初评临时目录 |
| `NUS_TENCENT_LBS_KEY` | 空 | QQ 地图密钥，一般不用 |
| `JUSTAUTH_DINGTALK_CLIENT_ID` / `JUSTAUTH_DINGTALK_CLIENT_SECRET` | 空 | 钉钉登录，本地一般不配 |

local 配置还做了这些设置：

- 关闭图片验证码和访问日志；
- 开启 Druid 监控页；
- 慢 SQL 日志（超过 100 ms）。

不装 ffmpeg、vosk 也能正常运行，只是音频附件没有转写文字，详见 [环境搭建](../docs/03-开发/环境搭建.md) 第 6 节。

## 相关文档

- [编码规范](../docs/03-开发/编码规范.md)：包结构、接口与 VO、错误码号段、提交前自查
- [数据库迁移](../docs/03-开发/数据库迁移.md)：怎么新增 V22
- [测试指南](../docs/03-开发/测试指南.md)
- [权限与数据范围](../docs/02-架构/权限与数据范围.md)、[数据模型](../docs/02-架构/数据模型.md)
- 旧资料在本目录的 `docs/archive/`，只读，内容可能过时
