# 师砺平台 · 本地运行指南（VS Code 终端 / IDEA）

给在本机（Windows）跑项目的同学：分别说明在 **VS Code 终端** 和 **IDEA（运行配置 + 终端）** 里怎么把后端、前端跑起来。
仓库整体介绍见 [README.md](README.md)，环境细节见 [docs/03-开发/环境搭建.md](docs/03-开发/环境搭建.md)。

## 0. 最快启动（日常就这两步）

一次性准备（第 2 节）**已于 2026-10-20 在本机全部完成**：jar 已打、依赖已装、`ry261-test` 库已导入（93 张表，MySQL 9.7 / 23307）、admin 口令已设置。开 **2 个终端**：

**终端 1 —— 后端：**

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
$env:NUS_LOCAL_DB_PORT = "23307"
$env:NUS_LOCAL_DB_PASSWORD = "123456"
# 6 个 AI key 任一为空都会启动失败（Moonshot 断言），本地填占位即可
$env:SPRING_AI_MOONSHOT_API_KEY = "local-placeholder"
$env:SPRING_AI_DEEPSEEK_API_KEY = "local-placeholder"
$env:SPRING_AI_MINIMAX_API_KEY = "local-placeholder"
$env:SPRING_AI_ZHIPUAI_API_KEY = "local-placeholder"
$env:SPRING_AI_OPENAI_API_KEY = "local-placeholder"
$env:SPRING_AI_ANTHROPIC_API_KEY = "local-placeholder"
& "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe" -jar "D:\sfs2026.10.15\shili-platform-dev\NUS-backend\nus-server\target\nus-server.jar" "--spring.config.additional-location=file:D:/sfs2026.10.15/shili-platform-dev/NUS-backend/nus-server/src/main/resources/"
```

两个坑必须知道，缺一个后端都起不来：

1. **必须加 `--spring.config.additional-location`**：pom 把 `application-local.yaml` 刻意排除出 jar，不加会报 `dynamic-datasource can not find primary datasource`；
2. **必须用 JDK 17 完整路径**：PATH 里的 `java` 是 JDK 26，不能直接写 `java -jar`。

**终端 2 —— 前端：**

```powershell
cd D:\sfs2026.10.15\shili-platform-dev\NUS-front
pnpm dev:antd
```

浏览器开 **http://localhost:5666**，用户名 `admin`，口令见根目录 [admin.pw](admin.pw)。停止：各终端 `Ctrl+C`。

> ⚠️ 本文件含本机口令，仅限本地使用；**提交前必须删除口令行**（AGENTS.md 禁止提交任何口令）。
> 没打过包或换了机器，先做第 2 节；起不来按第 5 节排查。

## 1. 环境要求与本机现状

| 工具 | 要求 | 本机现状（2026-10 实测） |
| --- | --- | --- |
| JDK | 17 | ✅ `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`。注意：`JAVA_HOME` 默认指向 JDK 26，**启动前必须切到 17** |
| Maven | 3.9 | ✅ 3.9.16 |
| Node / pnpm | ≥ 20.19 / 10 | ✅ Node v24 / pnpm 10 |
| MySQL | 8.x | ✅ 两个本机服务：**8.0 在 3306**、**9.7 在 23307**（`C:\mysql-9.7.1-winx64`），root 口令自行保管 |
| Redis | 7.x | ✅ 已用 winget 装在 `C:\Program Files\Redis`，端口 6379 |
| Docker | 可选 | ❌ 未安装：官方 `deploy/tests/e2e/local-env.sh` 一次性环境用不了，**本机走第 2 节的方式 B** |

起数据库的两种方式：

- **方式 A（Docker，官方推荐）**：`local-env.sh up → bootstrap → backend`，需要 Docker + Git Bash/WSL。装了 Docker 后可用，命令见 `README.md`。
- **方式 B（本机 MySQL + Redis，当前机器用这条）**：见下文第 2 节。

## 2. 一次性准备（只做一次）

### 2.1 打后端包

任一终端（VS Code 或 IDEA）里执行：

```powershell
cd D:\sfs2026.10.15\shili-platform-dev\NUS-backend

# 必须切到 JDK 17（JAVA_HOME 默认是 26，会编译失败或行为异常）
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

mvn -q -DskipTests package -pl nus-server -am
```

产物：`NUS-backend\nus-server\target\nus-server.jar`（约 3 分钟）。

### 2.2 建库并导入基线 ✅ 本机已完成（2026-10-20）

本机实际状态：库 `ry261-test` 已建在 **MySQL 9.7（23307）** 上，基线已导入（93 张表）。换机/换实例时按下面步骤重做：

基线文件：`NUS-doc\3-数据库文件\ry261-test.sql`（Flyway baseline V21），导入名为 `ry261-test` 的库（`application-local.yaml` 的默认库名）。

```powershell
# 本机用 9.7 实例：C:\mysql-9.7.1-winx64\bin\mysql.exe 加 -P 23307；8.0 实例用 3306
$mysql = "C:\mysql-9.7.1-winx64\bin\mysql.exe"

# 1) 建库（单引号保证反引号原样传给 MySQL）
& $mysql -h "127.0.0.1" -P "23307" -uroot -p"你的root口令" -e 'CREATE DATABASE IF NOT EXISTS `ry261-test` DEFAULT CHARSET utf8mb4;'

# 2) 导入基线（cmd 重定向，避免 PowerShell 管道改编码；在自己的终端里执行没问题）
cmd /c "`"$mysql`" -h127.0.0.1 -P23307 -uroot -p`"你的root口令`" --default-character-set=utf8mb4 ry261-test < `"D:\sfs2026.10.15\shili-platform-dev\NUS-doc\3-数据库文件\ry261-test.sql`""
```

### 2.3 首次引导管理员口令 ✅ 本机已完成（2026-10-20）

⚠️ **官方引导在 Windows 上必失败**：[AdminPasswordBootstrapRunner.java:81](file:///d:/sfs2026.10.15/shili-platform-dev/NUS-backend/nus-server/src/main/java/cn/nus/zknu/server/config/AdminPasswordBootstrapRunner.java) 用 `Files.getPosixFilePermissions` 校验口令文件 0600 权限，Windows 不支持 POSIX 权限，直接抛 `UnsupportedOperationException`。

本机采用**等效引导**（与 bootstrap 逻辑完全相同：仅当口令仍是基线哈希时才替换）：生成新口令的 BCrypt 哈希后条件更新 `system_users` 表 admin 行，已验证登录成功。当前 admin 口令记录在根目录 [admin.pw](admin.pw)。

换到 Linux/macOS 时用官方方式：

```powershell
# 口令文件：12～16 位，含大小写字母、数字、特殊字符，且不含账号名
Set-Content -Path "$env:TEMP\nus-admin.pw" -Value "换成符合要求的口令" -NoNewline
icacls "$env:TEMP\nus-admin.pw" /inheritance:r /grant:r "$env:USERNAME:R"

# 启动时加两个参数（引导成功后去掉，引导只能成功一次）
java -jar nus-server\target\nus-server.jar `
  --nus.admin-bootstrap.enabled=true `
  --nus.admin-bootstrap.password-file="$env:TEMP\nus-admin.pw"
```

日志出现 `Administrator bootstrap completed` 即成功。

### 2.4 装前端依赖 + 确认 Redis 在跑

```powershell
cd D:\sfs2026.10.15\shili-platform-dev\NUS-front
pnpm install        # 只能用 pnpm，npm/yarn 会被 preinstall 拦截

# Redis 没起就启动（装在 C:\Program Files\Redis）
Start-Process "C:\Program Files\Redis\redis-server.exe" -WindowStyle Hidden
& "C:\Program Files\Redis\redis-cli.exe" ping    # 期望输出 PONG
```

## 3. VS Code 终端怎么运行

VS Code 打开 `D:\sfs2026.10.15\shili-platform-dev`，`Ctrl+`` ` 开终端，底部 `+` 可开多个标签页。**开 3 个终端**：

### 终端 1 —— 后端（保持运行）

命令与**第 0 节终端 1 完全相同**（env 变量 + JDK 17 路径 + `--spring.config.additional-location`，一个都不能少，直接整块复制执行）。

后端端口 **48080**。改了后端代码要重新执行 2.1 的 `mvn package` 再启动。

### 终端 2 —— 前端（保持运行）

```powershell
cd D:\sfs2026.10.15\shili-platform-dev\NUS-front
pnpm dev:antd
```

代理默认指向 `http://localhost:48080/admin-api`，与上面方式匹配，**不用设变量**。只有后端跑在 48280（Docker 方式 A）时才需要：

```powershell
$env:NUS_DEV_API_TARGET = "http://127.0.0.1:48280/admin-api"
pnpm dev:antd
```

### 终端 3 —— 随时查状态（可选）

```powershell
& "C:\Program Files\Redis\redis-cli.exe" ping      # Redis
Invoke-RestMethod http://127.0.0.1:48080/actuator/health   # 后端健康检查
```

### 打开验证

浏览器访问 **http://localhost:5666**，用户名 `admin`，口令见引导时的文件或仓库根目录 `admin.pw`。要演示数据可另开终端跑 `python deploy\tests\ui\seed_demo.py`（账号写入 `~/.cache/nus-e2e/demo-accounts.json`）。

停止：各终端 `Ctrl+C`。

## 4. IDEA 里怎么运行

### 4.1 打开与 SDK

- `File → Open` 选 `D:\sfs2026.10.15\shili-platform-dev\NUS-backend`（只改后端时）或仓库根目录。
- `File → Project Structure → Project SDK` 选 **JDK 17**（没有就 Add SDK 指向 `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`）。

### 4.2 用运行配置启动后端（推荐，可断点）

`Run → Edit Configurations → + → Application`：

| 项 | 值 |
| --- | --- |
| Name | NusServerApplication |
| Main class | `cn.nus.zknu.server.NusServerApplication` |
| Working directory | `D:\sfs2026.10.15\shili-platform-dev\NUS-backend`（AI 初评的相对路径基于它） |
| Environment variables | `SPRING_PROFILES_ACTIVE=local; NUS_LOCAL_DB_PORT=23307; NUS_LOCAL_DB_PASSWORD=123456; SPRING_AI_MOONSHOT_API_KEY=local-placeholder; SPRING_AI_DEEPSEEK_API_KEY=local-placeholder; SPRING_AI_MINIMAX_API_KEY=local-placeholder; SPRING_AI_ZHIPUAI_API_KEY=local-placeholder; SPRING_AI_OPENAI_API_KEY=local-placeholder; SPRING_AI_ANTHROPIC_API_KEY=local-placeholder`。IDE 从 `target/classes` 运行，自带 local 配置，**无需** additional-location 参数 |
| Use classpath of module | nus-server |

点 ▶ 启动，端口 **48080**。打包可用 IDEA 右侧 **Maven 面板 → Lifecycle → package**（等价 2.1 命令）。

> 也可以不走运行配置、直接在 IDEA 终端里 `java -jar`，命令与第 3 节完全相同。

### 4.3 IDEA 终端跑前端

底部工具栏 `Terminal` 标签（`Alt+F12`），命令与 VS Code 完全一致：

```powershell
cd D:\sfs2026.10.15\shili-platform-dev\NUS-front
pnpm dev:antd
```

### 4.4 打开验证

浏览器访问 **http://localhost:5666** 登录 `admin`。IDEA 里断点调试后端时，前端照常访问 5666 即可（代理指向 48080）。

## 5. 常见问题

| 现象 | 处理 |
| --- | --- |
| 启动报类版本/编译错误 | `java -version` 不是 17 → 按 2.1/3 节切换 `JAVA_HOME` 到 JDK 17 |
| 后端起不来，日志报 DB 连接失败 | 库名不是 `ry261-test`、口令不对或没导基线；看 2.2；9.7 实例记得 `NUS_LOCAL_DB_PORT=23307` |
| 启动报 `dynamic-datasource can not find primary datasource` | local 配置没加载：`java -jar` 必须加 `--spring.config.additional-location` 且 `SPRING_PROFILES_ACTIVE=local`（见第 0 节）；IDE 运行则检查 env |
| 启动报 `Moonshot API key must be set` | 任一 AI key 为空会触发断言，按第 0 节设置 6 个占位 key |
| 引导报 `UnsupportedOperationException ... getPosixFilePermissions` | Windows 固有缺陷（见 2.3），改用等效 DB 更新方式 |
| 日志报 Redis 连接失败 | Redis 没启动，按 2.4 启动并 `ping` 验证 |
| 登录提示口令错误 | 用户名是 `admin`；没引导过先做 2.3，或用根目录 `admin.pw` 里的口令 |
| 前端请求 404 / 502 | `NUS_DEV_API_TARGET` 没带 `/admin-api`，或后端端口与代理目标不一致 |
| `pnpm install` 提示只能用 pnpm | 不要用 npm、yarn；网络慢可多试，仍失败删 `node_modules` 后重装 |
| 想用官方 `local-env.sh` | 它需要 Docker + bash，本机未装 Docker 时不可用，改走本文方式 B |
| 端口 48080 / 5666 被占用 | 后端可改 `server.port` 环境变量 `SERVER_PORT`；前端占用会自动换端口，看终端输出 |

## 6. 相关文档

- 分支、PR、提交规范：[AGENTS.md](AGENTS.md)、[docs/03-开发/开发流程.md](docs/03-开发/开发流程.md)
- 提交前必跑：`scripts/check-all.sh`（Windows 下用 Git Bash 跑）
- 后端测试：`cd NUS-backend && TZ=Asia/Shanghai mvn -q test`
- 当前状态与交接：[docs/06-交接/当前状态.md](docs/06-交接/当前状态.md)
