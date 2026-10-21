# 师砺平台界面规范（V1.0.1.6）

参照 ScholarOS 科研外壳的视觉与交互标准，以 Linear 为参考。改页面前先读这一页；拿不准的地方以这里和 `apps/web-antd/src/styles/theme/` 为准。

## 一、原则

1. **用面分层，不用线分层**：画布、面板、凹陷、悬停、选中五档相近的灰；卡片不加阴影，只用 1px 淡边框。
2. **颜色只给需要注意的东西**：主色只用于主操作、选中和链接；状态色只出现在圆点或图标上；常规信息一律中性灰。数字用正文色，不做彩色大数字。
3. **层级靠字号与字重**：页面标题 20/600，区块标题 15/600，正文 14，密集表格 13，辅助说明 12；字重只用 400、500、600。
4. **紧凑但有呼吸**：4px 栅格；表格行高 40；区块间距 24；页面内边距 24（1024 宽以下 16，`Page` 已处理）。
5. **状态变化有过渡**：120ms（颜色、底色）与 180ms（展开、面板），已尊重“减少动态效果”。
6. **只改呈现，不改业务含义**：接口、权限、路由、数据流、事件处理一律不动。

## 二、Token（只用变量，不写颜色值）

颜色 token 定义在 `styles/theme/palette.ts`（深浅两套，启动时写进页面），非颜色 token 在 `styles/theme/tokens.css`。

| 用途 | CSS 变量 | 对应的 Tailwind 类 |
| --- | --- | --- |
| 页面画布 | `--nus-bg-canvas` | `bg-background-deep` |
| 面板、卡片、表格 | `--nus-bg-surface` | `bg-card` / `bg-background` |
| 凹陷（表头、分组底） | `--nus-bg-sunken` | `bg-muted` |
| 悬停 | `--nus-bg-hover` | `bg-accent` |
| 选中 | `--nus-bg-selected` | — |
| 淡边框（卡片、分隔线） | `--nus-border-subtle` | `border-border` |
| 边框（输入框等明确边界） | `--nus-border` | `border-input` |
| 正文 / 次要 / 辅助文字 | `--nus-text` / `--nus-text-secondary` / `--nus-text-tertiary` | `text-foreground` / `text-muted-foreground` / — |
| 主色（文字、链接、选中） | `--nus-primary`、`--nus-primary-hover` | `text-primary` |
| 状态色（圆点、图标） | `--nus-success`、`--nus-warning`、`--nus-danger`、`--nus-info` | — |
| 强调浅底 | `--nus-<tone>-tint` | — |
| 图表分类色 | `--nus-chart-1` … `--nus-chart-6` | — |

- **字号**：`--nus-fs-display`(24) / `--nus-fs-title`(20) / `--nus-fs-heading`(15) / `--nus-fs-body`(14) / `--nus-fs-dense`(13) / `--nus-fs-caption`(12)，配套行高 `--nus-lh-*`；字重 `--nus-fw-regular/medium/semibold`。Tailwind 的 `text-xs/sm/base/xl/2xl`（12/14/16/20/24）可以用，不用任意值类（`text-[13px]`）。
- **圆角**：`--nus-radius-sm`(4，标签) / `--nus-radius`(6，按钮输入框) / `--nus-radius-lg`(8，卡片表格) / `--nus-radius-xl`(12，对话框)；Tailwind `rounded-sm/md/lg/xl` 正好是 4/6/8/12。`rounded-full` 只给头像和圆点。
- **阴影**：卡片不用；浮层（下拉、弹窗）由组件自带。不要再写 `box-shadow`。
- **动效**：`transition: var(--nus-transition-colors)`；时长用 `--nus-dur-fast` / `--nus-dur`。
- **禁止**：渐变、毛玻璃、写死的 `#hex`/`rgb()`/`hsl(数字)`、`!important`、`font-size: Npx`（用变量）、彩色 `<Tag color>` 表示状态。

## 三、共用组件与工具

- **页面骨架**：`<Page title="…" description="…">`，操作放 `#extra` 插槽；标题与说明直接在画布上，不再自己做“英雄卡片”、kicker 小标题、营销胶囊（如“支持 AI 辅助”）。说明一句话，不超过 60 字。
- **面板**：`.surface-panel`（或 antd `Card`，已是淡边框、无阴影、圆角 8）。**不套卡片**：卡片里要分组时用分隔线或 `--nus-bg-sunken` 的凹陷块。
- **指标**：`.metric-grid` + `.metric-card`（`__label` 13 次要色、`__value` 24/600 正文色、`__meta` 12 辅助色），一行最多 4 个。
- **状态**：`<StatusTag :tone :label />`（`#/components/status-tag`），圆点＋文字；需要强调时加 `emphasis`。状态与文案的对应用 `#/utils/status`：
  - `getHomeworkRecordStatusMeta(status, 'teacher' | 'student')` 提交记录；
  - `getAiPreReviewStatusMeta(status)` AI 初评；
  - `getTaskProgressMeta(task)` 实训任务进度；
  - 字典值用 `DictTag`（状态类字典自动显示圆点，其余显示文字）。
  - 页面里不要再写 `getStatusTag` 一类函数；缺的状态映射在自己负责的范围内新建 `#/utils/status-<范围>.ts`（带单测），不要改 `utils/status.ts`。
- **空状态**：`<EmptyState kind="empty | filtered | blocked" title description>`（`#/components/empty-state`），操作放 `#action` 插槽。三种：还没有数据、筛选没有结果、前置条件不满足（例如等级未解锁：写明原因和怎么解锁，而不是空白页加一条提示）。
- **横幅**：一页最多一条（antd `Alert`，带图标），只放最需要处理的事。
- **按钮**：一个区域最多一个主按钮（`type="primary"`），其余默认按钮；表格行内操作用链接按钮。
- **表格**：vxe-table 默认已左对齐、表头凹陷底、行高 40；数字列右对齐、操作列在列定义里单独指定。
- **确认框**：高影响操作（封存、删除、停用、打回、关闭会话、批量操作）要确认：标题写对象和动作，正文写后果和怎么恢复；确认按钮重复动作名，取消按钮写“返回”。低风险操作不加确认。
- **加载与出错**：加载写明在读什么；出错按“什么没成功—原因—下一步”写，保留用户已填内容；部分失败只让失败的那块降级。

## 四、文案（完整的术语表与文案规范见 docs/ui-copy.md）

- 称呼用“你”，不用“您”；不用感叹号、宣传腔（“开启……之旅”“一站式”）和工程行话。
- 中文句子用全角标点（，。：；？（）），中英文之间不强求空格，但同一页保持一致。
- 界面上不出现内部编号、字段名、枚举原值、开发占位（如“趋势图待接入后端数据”）。
- 按钮 2～8 字；常驻说明不超过 60 字。
- 业务术语（任务/作业、打回/退回、留言/消息等）**先不改**，等术语表确认后统一；状态文字以 `utils/status.ts` 为准。

## 五、自查

```bash
# 样式指标：渐变、阴影、写死颜色、非规范圆角、任意值字号、自写状态函数、彩色标签应为 0
node apps/web-antd/scripts/style-metrics.mjs <文件或目录> --detail

# 格式与 lint（只查自己改的文件）
pnpm exec prettier --write <文件>
pnpm exec eslint <文件>
pnpm exec stylelint <vue/css 文件>
```

注意 stylelint 的 `order/order` 规则要求同一层里 `@media` 写在普通规则之前，`--fix` 会把后写的 `@media` 挪到前面，后面的普通规则就会盖过媒体查询里的同名属性。写响应式样式时用互不重叠的区间（`(width < 768px)` 与 `(width >= 768px)`），基础规则里不要再写会被媒体查询改动的属性。

截图（父仓库）：`node deploy/tests/ui/shots.mjs --out <目录> --roles teacher --pages correct,名字=/路径 --themes dark,light --widths 1440,1024`，深色与浅色都要看，确认没有看不清的文字。
