# NUS-front：师砺平台前端

> 面向：改前端代码的开发同学和 AI 智能体。
> 适用版本：V1.0.1.10。
> 最后核对日期：2026-10-14。

管理端和学生 Web 端是同一个应用 `apps/web-antd`，技术栈为 Vue 3、Vue Vben Admin 5、ant-design-vue 4、vxe-table，用 pnpm monorepo 管理。规则先读 [AGENTS.md](AGENTS.md)（本目录）和根目录 [AGENTS.md](../AGENTS.md)。

## 目录

```
NUS-front/
├── apps/web-antd/              本项目的应用（其余目录是 vben 框架）
│   ├── src/
│   │   ├── views/              页面
│   │   ├── api/                接口函数与类型
│   │   ├── components/         公共组件
│   │   ├── adapter/            vben 表单、表格、组件适配
│   │   ├── router/             路由守卫、导航组合（菜单来自数据库）
│   │   ├── utils/              工具，状态映射在 status*.ts
│   │   └── styles/theme/       颜色与样式 token
│   ├── tests/                  copy（文案扫描）、style（样式基线）、antd（写法扫描）
│   └── .env*                   各模式的环境变量
├── packages/、internal/        vben 框架包与构建配置，一般不改
├── docs/                       ui-guidelines.md、ui-copy.md
└── scripts/deploy/             前端镜像的 Dockerfile 与 nginx.conf
```

### views 业务目录

| 目录 | 内容 |
| --- | --- |
| `edu/class`、`edu/dept`、`edu/teacher` | 班级、教务组织、教师 |
| `edu/homework` | 实训任务：新建与复制（`HomeworkForm.vue`）、批改（`correct.vue`）、班级选项解析（`class-relations.ts`） |
| `edu/semester`、`edu/statistics`、`edu/message` | 学期与收尾、统计分析、留言 |
| `edu/certificate`、`edu/courseresource`、`edu/questionbank` | 证书、课程资源、题库 |
| `edu/common` | 用户管理等共用页面 |
| `edu/student/` | 学生端：`home`、`course`、`homework`、`capacity`、`message`、`news`、`profile` |
| `capacity/course`、`capacity/indicator` | 课程（含公共课、认领）、能力指标 |
| `dashboard/` | 各角色的工作台 |
| `system/`、`infra/`、`ai/` | 脚手架的系统管理、基础设施、AI 模型与提示词 |
| `_core/` | 登录、个人中心、错误页 |

每个业务目录通常有：

- `index.vue`：列表页；
- `data.ts`：表单 schema、表格列；
- `modules/`：弹窗、抽屉；
- 纯逻辑 `xxx.ts`，配套测试 `xxx.test.ts`。

### api、components、adapter

- `src/api/<后端模块>/<业务>/index.ts`：按后端模块分为 `edu`、`capacity`、`system`、`infra`、`ai`、`core`。统一用 `src/api/request.ts` 的 `requestClient`，写操作会自动带幂等键 `X-Idempotency-Key`。
- `src/components/`：
  - `rich-html`：显示用户填写的富文本，负责 XSS 过滤和图片缩放预览；
  - `tinymce`：富文本编辑器；
  - `status-tag`、`empty-state`：状态标签、空状态；
  - `upload`、`data-exchange`：上传、导入导出；
  - 其他：`table-action`、`dict-tag`、`description` 等。
- `src/adapter/`：
  - `form.ts`：vben 表单；
  - `vxe-table.ts`：表格，包括删除后翻页回退等统一行为；
  - `component/`：组件注册。

写法约定见 [编码规范](../docs/03-开发/编码规范.md) 第 2 节。

## 运行

需要 Node ≥ 20.19（推荐 `.node-version` 里的 22.12.0）和 pnpm 10.28.1。

```bash
pnpm install
pnpm dev:antd                                                        # 后端在 48080
NUS_DEV_API_TARGET=http://127.0.0.1:48280/admin-api pnpm dev:antd    # 后端是本机一次性环境（48280）
```

- 浏览器打开 http://localhost:5666。端口来自 `apps/web-antd/.env.development` 的 `VITE_PORT`。
- `/admin-api` 的代理写在 `apps/web-antd/vite.config.mts`，目标地址要带 `/admin-api`。
- 后端怎么起、管理员口令在哪里，见 [环境搭建](../docs/03-开发/环境搭建.md)。

## 测试

```bash
pnpm check:type                                     # 类型检查（vue-tsc，经 turbo）
pnpm test:unit                                      # vitest，全部单元测试，含文案扫描，约 2 分钟
pnpm test:unit apps/web-antd/tests/copy             # 只跑文案扫描
npx eslint <改过的文件> && npx prettier --check <改过的文件>   # 必须在 NUS-front/ 根目录执行
```

提交前在仓库根目录跑 `scripts/check-all.sh`，它会自动跑上面几项。详见 [测试指南](../docs/03-开发/测试指南.md)。

## 构建

```bash
pnpm build:production     # 生产构建，产物在 apps/web-antd/dist
pnpm build:docker         # 构建前端镜像，默认标签 nus-front:local
FRONTEND_IMAGE=nus-front:<版本标签> pnpm build:docker
```

- 生产构建在 `NUS-front/` 根目录用 `pnpm build:production`。它会先构建 `web-antd` 依赖的工作区包，再构建应用。
- 不要直接用 `pnpm -F @vben/web-antd run build`：安装后生成的工作区 stub 只供开发解析，不能作为生产依赖产物。
- 生产模式用 hash 路由、同源接口路径（见 `.env.production`），由离线包里的 nginx 统一对外。
- `build:docker` 只构建镜像，不动已有容器。镜像用 `scripts/deploy/Dockerfile`，依赖安装和生产构建都在 Dockerfile 里完成。
- 正式升级包由 `deploy/offline/build-update.sh` 打包，它读取 `apps/web-antd/dist`。发版流程见 [发版流程](../docs/05-发布/发版流程.md)。跨平台离线发布需要 `linux/amd64` 镜像，做法见运维和发版文档。

## UI 与文案规范

- [docs/ui-guidelines.md](docs/ui-guidelines.md)：布局、颜色 token、交互、深浅色主题。
- [docs/ui-copy.md](docs/ui-copy.md)：用词和提示语。扫描规则在 `apps/web-antd/tests/copy/copyRules.ts`，改规则时同步这份文档。
- 术语以根目录的 [术语表](../docs/01-产品/术语表.md) 为准，例如“封存”不是“归档”，“新建”不是“新增”。

旧版构建说明已移到 `docs/99-历史/前端构建说明-旧.md`，有用的内容已吸收到上面的“构建”一节。
