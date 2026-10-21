# V1.0.1.9 计划：列表刷新体验与查询性能

日期：2026-10-10。状态：**已按 Fable 审阅修订，待用户确认**（确认前不动代码）。审阅记录见第七节。

## 一、背景

用户反馈两个问题：

1. 课程详情里“添加班级”点确定后，下面的班级列表没有马上出现新班级。
2. 学生多了以后查询变慢，怀疑是关联表的问题。

本计划基于三方面排查：

- 前端：全应用排查“操作成功后页面不刷新”；
- 后端：慢 SQL 静态审查，覆盖索引、N+1、整行读取和缓存；
- 本机实测：一次性环境造数后，按管理员、教师、学生三种身份调用 55 个查询接口。每个接口预热后测 3 次，同时用 performance_schema 统计 SQL 条数、耗时和扫描行数。

### 实测数据规模

- 6000 名学生、120 个班、20 门课，每门课绑 6 个班；
- 2400 个任务（其中 9/10 为模拟的往期学期）、12 万条任务分配、8.4 万条提交、18 万条站内信。

### 结论

- **关联表本身不是瓶颈**：`edu_class_student`、`edu_class_course`、`edu_homework_assignee` 的索引基本够用。
- **慢的根源是三类问题**：
  1. 按任务、按行重复查询（N+1）；
  2. 把整门课、整个任务的全部提交记录（含大文本字段）读进内存再统计；
  3. 用户表、用户角色表、参数表缺索引，系统参数也没有缓存。
- **数据越多越慢**：本机（M 系列芯片、Docker MySQL）单接口目前最慢 231 ms。但 SQL 条数和扫描行数随学生数、任务数线性增长，配置较弱的服务器上会明显变慢。
- **“任务越多越慢”在放大 10 倍后最明显**：例如课程详情的 SQL 从 26 条涨到 133 条。

### 典型接口实测（数据放大 10 倍后，3 次中位数）

| 接口 | 耗时 | SQL 条数/次 | 扫描行数/次 | 问题 |
| --- | ---: | ---: | ---: | --- |
| 课程详情 `/edu/course-manage/detail` | 231 ms | 133 | 16,817 | 每个任务单独 COUNT 分配人数；整门课提交记录整行读入 |
| 提交记录分页 `/edu/homework-record/page`（每页 10 条） | 157 ms | 97 | 206 | 每行 6–9 条（AI 评阅、权限、课程、班课） |
| 班课详情 `/edu/course-manage/class-detail` | 150 ms | 100 | 4,251 | 每个任务单独查分配学生 |
| 批改分页 `/edu/homework-record/correct-page` | 146 ms | 97 | 206 | 同提交记录分页 |
| 管理员看板 | 72 ms | 10 | 46,731 | 全表扫描提交表、用户表，每行执行可见范围子查询 |
| 学生管理 `/edu/user/page` | 18–87 ms | 5 | 约 12,000 | `system_users` 没有用户类型、组织索引，COUNT 全表扫描 |
| 学生“我的任务” `/edu/student/homework/list` | 29 ms | 5 | 10,407 | 随该学生历史任务增长 |
| 权限信息（每次刷新页面都调用） | 21 ms | 6 | 6,474 | `system_user_role` 没有 `user_id` 索引，按用户查角色全表扫描 |

### 一轮测试中执行次数最多的 SQL

- 按任务计数分配人数：960 次；
- 按 key 读 `infra_config` 参数：382 次。参数没有缓存，`config_key` 也没有索引；
- 按 ID 逐行读用户、课程、任务、提交、班课：各数十到数百次。

## 二、修改内容

### A. 前端：操作后刷新（S-A，Claude）

| # | 位置 | 问题 | 修改 |
| --- | --- | --- | --- |
| A1 | 课程详情 → 班级 → 添加班级（`views/capacity/course/detail.vue:716-733,1195`） | **用户反馈的 bug。** 在 V1.0.1.8 修“下拉浮层残留”时引入：刷新挂在 `@after-close` 上，但 antd 4 的 Modal 里 `afterClose` 是 prop、不是事件，回调从不执行。班级列表、计数、指标卡、任务 tab 的“覆盖 N 个班级”都不更新 | 添加成功、关闭弹窗后直接 `await loadDetail()`（浮层挂在弹窗内部，不会残留），并把 `@after-close` 改为 `:after-close`。新班级不在当前筛选（默认“进行中”）下时，提示“已添加，切换筛选可查看” |
| A2 | 我的通知：标记已读、批量已读、全部已读（`views/system/notify/my/index.vue`） | 列表会刷新，但右上角铃铛的未读数最多要等 30 秒轮询 | 成功后调用 `emitNotifyUnreadChanged()` |
| A3 | 学生首页点通知标记已读（`views/edu/student/home/index.vue:126-137`） | 同上 | 同上 |
| A4 | 题库删除某页最后一题（`views/edu/questionbank/index.vue`） | 表格变空白，分页器却停在原页 | 删除后列表为空且不在第 1 页时，回退一页重新读取 |
| A5 | 班级管理 → 学生管理弹窗（`views/edu/class/index.vue:213`） | 父页面没监听 `success`。目前没有可见影响 | 补 `@success` 刷新 |
| A6 | 回归检查 | — | 全仓库禁止在 antd 组件上用 `@after-*` 写法，加一条单元测试扫描 |

### B. 数据库索引（新增迁移 V20，S-B，负责人）

只改索引，不改数据。

**幂等写法**：MySQL 没有 `ADD INDEX IF NOT EXISTS`，沿用 `V16__v1_0_1_5_business_completion.sql:60-66` 的 `information_schema.statistics` + `PREPARE` 模式，每个 ADD、DROP 各自先判断索引是否存在。

| 表 | 新增索引 | 服务的查询 |
| --- | --- | --- |
| `system_users` | `(user_type, status, deleted, id)` | 学生/教师管理分页与 COUNT、系统用户分页、看板总人数、可加入班级的学生分页 |
| `system_users` | `(dept_id, user_type, status, deleted)` | 按组织筛选；学院教务管理员的数据权限 `dept_id IN (...)` |
| `system_users` | `(mobile)`、`(email)` | 新建、导入时查重。目前全表扫描 |
| `system_user_role` | `(user_id, role_id)`、`(role_id)` | 登录与鉴权时查角色、按角色筛选用户。目前全表扫描 |
| `system_user_profile` | `(user_type, grade_year, user_id)` | 按年级筛学生 |
| `infra_config` | `(config_key)`（普通索引，避免现场有重复 key 时迁移失败） | 参数读取。D1 加缓存后查询量本就很低，优先级低 |
| `edu_homework_record` | `(homework_id, status, score)` | 排名、top5/last5、按任务统计 |
| `edu_homework_record` | `(status, homework_id)` | 看板待批改数、AI 批改数 |
| `edu_homework_record` | `(student_id, status, correct_time)` | 学生首页最近成绩 |
| `edu_homework` | 把 `idx_teacher(teacher_id)` 换成 `(teacher_id, status, id)` | 教师任务分页、最近任务 |
| `edu_message` | 未读留言数用的索引，列序以 EXPLAIN 为准：候选 `(class_id, course_id, type, read_status)` 与 `(type, read_status, class_id, course_id)` | 教师看板未读留言数 |

**删除被新索引完全覆盖的旧索引**（待用户确认，见第五节）：

- `edu_homework_record` 的 `idx_edu_homework_record_homework_status`、`idx_student`；
- `edu_message` 的 `idx_type`（如果新索引以 `type` 开头）。

**更正（实施时核对）**：提交表新增 3 个、删除 2 个，二级索引（含唯一键）从 4 个变为 5 个，净增 1 个；七张表合计净增 9 个索引。

**升级耗时**：InnoDB 在线建索引，在升级停机窗口内执行。十万级提交约数秒；百万级每个索引约十几秒到几十秒。按学校服务器的实际数据量评估（见第五节）。

### C. 后端：消除 N+1、改为 SQL 聚合

**通用约束**：

- **手写 SQL 要自己加 `deleted = 0`**：MyBatis-Plus 只对 Wrapper 查询自动拼逻辑删除条件。
- **数据权限拦截器会改写子查询**：拦截器会给 `system_users`、`system_dept`、`edu_class` 追加 dept 条件，派生表和 EXISTS 子查询里的这些表也会被改写。每条新的 XML SQL 都要有一条集成测试，验证在拦截器开启时能被 JSqlParser 解析（含窗口函数），并且结果正确。

| # | 接口 | 修改 | 必须保持的原有语义 |
| --- | --- | --- | --- |
| C1 | 课程详情 `getCourseDetail` | 已提交、已批改、平均分改为一条按 `homework_id` 分组的 SQL；分配人数也按 `homework_id` 分组；班级人数按 `class_id` 分组。不再整行读取提交记录 | ① 只统计**仍在 `edu_homework_assignee` 里**的学生的提交（JOIN 分配表，两表都加 `deleted = 0`）；② 已提交 = `status >= 1`（含打回 3、重交 4），已批改 = `status = 2`，平均分 = 所有非空分数（不限状态）；③ 排除归档任务；非负责人只看自己布置的任务（`EduCourseManageServiceImpl.java:107,167-169,409-431`） |
| C2 | 班课详情 `getClassDetail` | 复用已经批量查出的分配学生，不再每个任务单独查询；提交记录只取需要的列 | 同 C1 |
| C3 | 提交记录分页、批改分页 | ① 新写“每条记录取最新一条 AI 评阅”的批量查询，按 `MAX(id)` 取，与 `EduHomeworkAiReviewMapper.java:18-23` 的 `ORDER BY id DESC LIMIT 1` 一致，返回状态、AI 分数、评语、置信度。现有的 `getLatestAiReviewStatusMap` 只返回状态，不够用；② 批量路径用不带逐行权限校验的 mapper 方法，页面权限由分页接口统一校验一次；③ 排名只查 `student_id, score`，用 `RANK() OVER (PARTITION BY homework_id ORDER BY score DESC)` | 排名与 `EduMetricPolicy.java:80-87` 一致：只排 `status = 2`，分数相同并列（空分数之间也并列），`ORDER BY` 只用 score，不加时间 |
| C4 | 课程分析 `course-analysis` | 每个任务的 top5、last5 用 `ROW_NUMBER() OVER (...)` 一次查出 | 与原排序、取数规则一致 |
| C5 | 任务统计 `/edu/homework/statistics` | 改成 SQL 聚合，不再整行读取 | 同 C1 的口径 |
| C6 | 管理员看板、教师看板 | “本周提交”改用 `submit_time`（有索引）；可见范围先用派生表求出可见任务 ID 再 JOIN | 学期匹配谓词原样保留：任一方为空即匹配，否则两边 TRIM 后相等（`StatisticsMapper.xml:14-16`）。不能简化为等值 |
| C7 | 学生/教师管理分页、可加入班级的学生分页 | 年级筛选改为 `EXISTS (profile)`，不再把整个年级的学生 ID 拼进 `IN`；组织子树改用已缓存的 `getChildDeptIdListFromCache` | **关键字仍按“包含”匹配**学号、姓名、手机号，搜索行为不变，见第五节。加索引并修正 COUNT 后，按类型过滤后的扫描量可以接受 |
| C8 | `/system/user/simple-list?userType=` | `userType` 条件下推到 SQL，只查少量列 | 返回字段不变 |
| C9 | 教师留言列表 | 学生姓名批量查；最后一条留言和未读数用 SQL 聚合 | — |
| C10 | 学生首页、学生课程列表 | 只查需要的列；最近成绩的任务名批量查；课程进度、资源按 `course_id IN` 批量查 | — |
| C11 | 未限定任务的批改分页 | 改为与 `edu_homework` 做 JOIN 按 `teacher_id` 过滤，不再拼接教师全部历史任务 ID | 范围不变 |
| C12 | 批次详情 | 跳过原因用 SQL `GROUP BY` 统计 | — |

### D. 缓存

统一使用项目已有的 Redis `@Cacheable` / `@CacheEvict`，支持 `name#ttl` 写法（`TimeoutRedisCacheManager`）。不再引入 Caffeine，和现有的权限、角色缓存保持一致。

| # | 内容 | TTL | 失效 |
| --- | --- | --- | --- |
| D1 | 系统参数 `ConfigApi.getConfigValueByKey` | 60 秒 | 在“参数配置”里修改时清除对应 key |
| D2 | 当前学期、学期选项 | 10 分钟 | 切换当前学期、绑定或归档班课时清除 |
| D3 | 能力指标树 | 1 小时 | 指标增删改时清除 |
| D4 | 字典数据接口 | 1 小时 | 字典增删改时清除 |
| D5 | 课程信息、课程成员、班级所属专业（权限校验大量重复读取） | **只做请求内缓存**，跨请求缓存可能让刚移除的成员仍有权限 | 请求结束即失效。定时任务、异步线程没有请求上下文时直接查库 |
| D6 | 看板 | **不缓存**（用户决定）。管理员看板和教师看板都保持实时，速度只靠 C6 的 SQL 优化和 B 的索引 | — |

### E. 批量写入

| # | 场景 | 修改 |
| --- | --- | --- |
| E1 | 班级加学生、移学生（开放任务 × 学生逐条 select/insert；逐个校验范围、更新资料） | 先批量查出已有分配，再批量插入；范围校验用 `selectByIds`；资料批量更新 |
| E2 | 站内信 `EduBusinessNotifyService.sendToUsers` | 在 system 模块新增批量发送 API，每 500 条一批插入 `system_notify_message`。已核实站内信没有 WebSocket 推送，模板内容对所有收件人相同，批量插入不会绕过现有逻辑。原来逐人 try/catch、返回成功条数，改成按批成败；同步核对调用方（如 `EduClassCourseServiceImpl.java:217`）对返回值的用法 |
| E3 | 截止提醒定时任务 | 只查 `student_id`；提醒日志和站内信批量插入 |
| E4 | 成绩册导入 | 复用已经预先查好的 `recordsByStudent`，不再逐行 `selectById` |

### F. 需要单独确认的疑点

**F1（Fable 已核实属实）**：提交记录分页的每一行都会经过 `requireHomeworkCorrectionAccess`，而它只允许任务布置者。所以：

- 管理员、课程负责人打开别人布置的任务的提交记录页，会报“无权限”；
- `validateCourseClassAccess` 要求班课未归档，所以已归档班课的任务记录页会报“班课不存在”。

C3 改成批量查询后，权限改由分页接口统一校验，规则按现有页面权限：管理员、课程负责人、团队成员可查看。测试覆盖以下组合：

- 角色：布置者、管理员、课程负责人、团队成员、无关教师；
- 班课状态：进行中、已归档。

### 不在本次范围

- 账号导入逐行创建：需要绕开 `createUser` 的整套校验，风险较大。
- 学生名单、成绩册等导出接口整表读取。
- 前端进入一个页面时发出的请求数量（只记录，不改）。

## 三、验收标准

0. **先建基准（S-V0，改代码之前）**：
   - 把造数脚本和测速脚本提交到 `deploy/tests/perf/`。它们只能连本机一次性测试库，写库前检查目标是 127.0.0.1；
   - 用同一份造数，在**未修改的代码**上跑一遍，保存：各接口耗时、SQL 条数、扫描行数，以及每个接口返回的 JSON 快照；
   - 造数覆盖以下情况：打回重交、移出班级的学生、归档任务、归档班课、空分数并列。
1. **性能**（同一份造数、同一台机器）：
   - C 中各接口每次请求的 SQL 条数不随任务数、每页行数、学生数增长。目标：课程详情 ≤ 15 条，记录/批改分页 ≤ 12 条，班课详情 ≤ 12 条；
   - 列出的接口在数据放大 10 倍后，本机中位数均 ≤ 80 ms；
   - 看板、学生管理的扫描行数下降一个数量级以上，EXPLAIN 显示走了新索引；
   - 一轮基准中参数表查询从 382 次降到个位数。
2. **结果不变**：
   - 改造后各接口的 JSON 与基准快照逐项一致。只有 F1 修正的权限差异例外，需逐条说明；
   - 后端单元测试、真实 MySQL 集成测试（基线 19 + V20）全部通过，新增 XML SQL 有拦截器解析测试；
   - e2e v1018、v1017、v1015 全部通过。
3. **迁移**：check-v20 覆盖以下场景：
   - 全新安装；
   - 带数据升级；
   - 重复执行；
   - 部分执行后恢复：索引建了一半、Flyway 记录为失败；
   - 基线 20 与“基线 19 + V20”逐表一致。

   NUS-doc 基线升到 20。
4. **前端**：A1–A6 在浏览器里逐项验证；单元测试、类型检查、生产构建通过。
5. **发布**：构建 V1.0.1.9 升级包，在 107 上完成升级、回滚、失败演练。学校服务器从 V1.0.1.8.1 直接升级。

## 四、分工与顺序

按文件划分，互不重叠。子代理不提交，由负责人审核、复跑测试后提交。

| 分片 | 内容 | 主要文件 | 执行 | 依赖 |
| --- | --- | --- | --- | --- |
| S-V0 | 造数、测速、改造前快照 | `deploy/tests/perf/` | 负责人 | 最先做 |
| S-A | 前端 A1–A6 | 前端各页面 | Claude | 无 |
| S-B | V20 索引迁移、基线 20、check-v20 | 迁移、NUS-doc | 负责人 | S-V0 |
| S-C1 | C1、C2、C5（课程、任务统计聚合） | `EduCourseManageServiceImpl`、`EduHomeworkStatisticsService`、对应 mapper | Codex | S-V0 |
| S-C2 | C3、C11、F1、D5（记录/批改分页、权限批量化与请求内缓存） | `EduHomeworkRecordController`、`EduHomeworkRecordServiceImpl`、`EduAiBusinessAccessService`、`EduDataScopeService`、AI 评阅 mapper | Codex | S-V0 |
| S-STAT | C4、C6 | `EduStatisticsServiceImpl`、`StatisticsMapper.xml` | Codex | S-V0 |
| S-C3 | C8、C9、C10、C12、C7 的 system 部分 | `UserController`、`AdminUserServiceImpl`、`AdminUserMapper`、留言、学生首页/课程、导入批次 | Codex | S-V0 |
| S-D | D1–D4 | `ConfigApiImpl`/`ConfigServiceImpl`、学期、指标、字典服务 | Codex | 无 |
| S-E | E1–E4、C7 的班级学生部分 | `EduClassStudentServiceImpl`、`EduBusinessNotifyService`、system 站内信批量 API、提醒、成绩册导入 | Codex | S-V0 |
| S-V | 对比快照与性能、e2e、打包、107 演练 | — | 负责人 | 全部 |

**优先级**：

- **必做**：B、D1、D2、C1–C3、C6、C7、C8、E1、E2、F1、A；
- **可顺延到下个版本**（时间紧时）：C4、C5、C9–C12、D3、D4、E3、E4。

**估计**：开发 3–4 天，验证和发布 1 天。

## 五、需要用户决定

1. **版本号**：建议 V1.0.1.9。加索引需要新的数据库迁移 V20，必须用新版本号。
2. **看板缓存**：建议先不缓存，SQL 优化后实测；仍慢时只缓存管理员看板 60 秒，教师看板始终实时。
3. **学生搜索**：建议保持“包含”匹配，输入学号后几位也能搜到。改成前缀匹配会更快，但会改变使用习惯。
4. **删除被新索引覆盖的旧索引**：建议删除，提交表的写入开销与现在持平。
5. **学校服务器的数据量**：大约多少学生、多少条提交？用于评估升级停机时长，并校准造数规模。
6. **批量写入 E** 是否放进本次：建议放进来。
7. **测速脚本入库**：建议放到 `deploy/tests/perf/`，只能连本机测试库。

### 用户决定（2026-10-10）

1. 看板：**都不缓存**，管理员看板和教师看板都保持实时。
2. 学号搜索：**保持“包含”匹配**。
3. 被新索引覆盖的旧索引：**删除**。
4. 学校服务器数据量：刚部署一天，目前只有几百名学生，后续会增加。
   - 升级时建索引只需几秒，停机时间不受影响；
   - 验收按 6000 名学生、数据放大 10 倍做；
   - 另外加一轮 2 万名学生的规模测试，确认后续增长后的表现。
5. 版本号 V1.0.1.9、批量写入 E 放进本次、测速脚本入库：按建议执行，待用户最终确认开工。

## 六、执行前提

- 本计划确认后，先做 S-V0（基准与快照），再并行开发。
- 107 当前运行 V1.0.1.8.1，学校服务器待用户升级到 V1.0.1.8.1。

## 七、审阅记录（Fable，2026-10-10）

**结论**：需修改后可行。根因与证据均已核实：A1、C1–C3、B 中缺失的索引、D1 参数无缓存。

**阻断项及处理**：

1. **分片文件重叠** → 重新分片：
   - C4、C6、D6 合并为 S-STAT；
   - C7 拆开，班级学生部分随 S-E，system 部分随 S-C3；
   - D5 随 S-C2。
2. **C1/C2 的聚合未写明原有语义** → 已写入第二节 C 表的“必须保持的原有语义”列（分配表过滤、状态口径、归档与布置者过滤、手写 SQL 加 `deleted`）。
3. **C3 复用 `getLatestAiReviewStatusMap` 不成立，F1 范围不全** → 改为新写取最新一条 AI 评阅的批量查询；F1 补上归档班课和多种角色的测试。
4. **基准脚本不在仓库** → 新增 S-V0：先入库造数和测速脚本，在改代码前保存快照。
5. **迁移幂等写法未指定** → 指定沿用 V16 的写法，并补“部分执行后恢复”用例。

**采纳的建议**：

- 删除被覆盖的旧索引（待用户确认）；
- `dept` 索引补上 `deleted`；
- `edu_message` 索引列序以 EXPLAIN 为准；
- 缓存统一用 Redis；
- D6 先测后定，教师看板不缓存；
- D5 在没有请求上下文时直接查库；
- C6 原样保留学期谓词；
- 新 SQL 加拦截器解析测试；
- 排名口径与原逻辑一致；
- C7 不改变搜索行为；
- E2 分批插入并说明返回值语义变化；
- 补充“不在本次范围”；
- 按优先级分为必做与可顺延，估时调整为 3–4 天。
