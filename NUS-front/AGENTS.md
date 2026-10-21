# 前端补充规则（NUS-front）

先读根目录 `AGENTS.md`。本文件只写前端特有的规则和踩过的坑。

## 运行与测试

- Node ≥ 20.19（推荐 `.node-version` 里的 22.12），pnpm 10（`packageManager` 指定 10.28.1）。
- 安装依赖：`pnpm install`。
- 本地开发：`pnpm dev:antd`，访问 http://localhost:5666。接口默认代理到 `http://localhost:48080`；后端在别的端口时，设置环境变量 `NUS_DEV_API_TARGET`。
- 提交前必须通过：
  ```bash
  pnpm check:type                         # 类型检查
  pnpm test:unit                          # vitest，全部单元测试
  npx eslint <改过的文件> && npx prettier --check <改过的文件>
  ```
  - `pnpm build:production` 在发版或改了构建配置时跑。
  - 提交时 lefthook 会自动跑 lint，不要用 `--no-verify` 跳过。
- eslint 要在 `NUS-front/` 根目录执行，在 `apps/web-antd` 下执行会报 tsconfig 解析错误。

## 代码位置

- 业务页面：`apps/web-antd/src/views/`。
  - 教学业务在 `edu/`；
  - 课程在 `capacity/`；
  - 学生端在 `edu/student/`。
- 接口：`apps/web-antd/src/api/`，按后端模块分目录。
- 公共组件：`apps/web-antd/src/components/`。例如：
  - `rich-html` 显示用户填写的富文本，带图片缩放和预览；
  - `tinymce` 是富文本编辑器。
- 菜单与路由来自数据库，不要在前端再写一份静态路由。

## 踩过的坑

1. **vben 表单的 `handleValuesChange` 是异步执行的**（在 watch 里调用）。
   - 不能用“设值前把标志置 true、设值后置 false”来跳过级联，回调执行时标志已经复位了。
   - 课程固定的场景（课程详情新建、复制任务）直接按场景判断不做级联，见 `HomeworkForm.vue`。
2. **下拉框的值类型要始终一致**。V1.0.1.10 之前，同一个班级下拉一会儿用班课编号、一会儿用班级编号，编号撞上时任务建到了别的班。现在：
   - 班级多选一律用 `classCourseId`；
   - 解析写在 `views/edu/homework/class-relations.ts`。
3. **ant-design-vue 4 的 Modal**：`afterClose` 是属性，不是事件，`@after-close` 不会触发。有单元测试扫描这个写法。
4. **级联清空字段时不要立即校验**：`setFieldValue(field, undefined, false)`，否则弹窗一打开就标红。
5. **用户填写的 HTML 一律用 `RichHtml` 组件显示**，它负责 XSS 过滤和图片缩放。不要直接 `v-html`。
6. **列表删除后的页码回退**由 `adapter/vxe-table.ts` 统一处理，不要在页面里再写一遍。

## 文案与界面

- 规范：`docs/ui-guidelines.md`（布局、颜色、交互）、`docs/ui-copy.md`（用词、提示语）。
- 术语以 `docs/01-产品/术语表.md`（仓库根目录）为准。例如：
  - 封存（不是“归档”）、恢复；
  - 删除只用于真正移除；
  - 新建（不是“新增”）。
- `tests/copy/` 的文案扫描会拦截不规范的用词。要新增或调整规则，改 `tests/copy/copyRules.ts`，并同步 `ui-copy.md`。
- 颜色只用 `--nus-*` CSS 变量，要同时适配浅色和深色模式。

## 改动检查清单

- [ ] 新页面或新按钮：权限码（`auth`）与后端一致。
- [ ] 文字：通过 `tests/copy` 扫描；术语与术语表一致。
- [ ] 表单：级联、校验时机、编辑回显都在浏览器里实测过。
- [ ] 纯逻辑抽到 `.ts` 文件并写 vitest 测试。
