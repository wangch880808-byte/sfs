# V1.0.1.8 修改计划

- 编写日期：2026-10-08
- 基线：`release/V1.0.1.7`（NUS-backend 08ceba0、NUS-front 0b97ed8、NUS-doc 718ced1，Flyway 18）
- 问题来源：
  - [V1.0.1.7 用户手册走查记录](USER_MANUAL_WALKTHROUGH_V1.0.1.7_20261008.md)，下文用 `#编号` 引用其中的问题；
  - V1.0.1.7 遗留的跟进项。
- 定位方式：三组只读代码调研，每条问题都已定位到具体文件。下文“位置”一栏的前端路径都相对于 `NUS-front/apps/web-antd/src/`。

## 0. 目标与原则

1. **统计页不再在没有数据时下结论**；同一个数字在不同页面、不同角色看到的口径一致。
2. **让教师能自己给课程添加班级**。同时补上后端的课程归属校验，这一项是本版唯一的安全修复。
3. 把走查中发现的界面和文案问题一次性清理掉。
4. 只做走查问题和已登记的遗留项，不扩大范围。新增的数据库变更统一放进一个迁移 `V19__v1_0_1_8_*.sql`。

## 1. 前置条件

| # | 事项 | 说明 |
| --- | --- | --- |
| P1 | V1.0.1.7 收尾 | 三个子仓库的 `release/V1.0.1.7` 都还没推送。107 上的强制失败演练和回滚演练也还没做。建议先完成这两件事再拉 `.8` 分支，否则 `.8` 的基线不稳定。 |
| P2 | 建立 `.8` 分支 | 从 `release/V1.0.1.7` 拉出 `release/V1.0.1.8`（backend、front、doc、父仓库各一个）。 |
| P3 | 需要你拍板的事项 | 见第 6 节。D1、D2 会影响 V19 迁移的内容，需要在开工前确定。 |

## 2. 修改清单

优先级：**P0** 必须做（安全或明显错误）；**P1** 本版应做（误导用户或流程走不通）；**P2** 体验和文案。

### A. 统计口径

| 编号 | 优先级 | 问题 | 原因 | 修改方案 | 位置 |
| --- | --- | --- | --- | --- | --- |
| A1 | P1 | #1 教学过程分析在没有数据时写“通过率 100%，达标情况稳定” | 前端用 `fullPassRate = 100 - (unqualifiedRate ?? 0)` 计算，没有数据时得到 100；已有的 `hasPassRate` 只用来控制饼图，没用在结论上。 | 总数为 0 时把 `fullPassRate` 设为 `null`。结论列表改为显示“还没有已批改的任务，暂不判断达标情况”，各卡片显示“—”。 | `views/edu/statistics/teaching-process.vue:165-175, 233-242` |
| A2 | P1 | #2 教学问题研判在没有数据时显示 71.6 分、“中风险” | 同上，再加上各维度公式里的常数项（节奏 74、分层 100 等），平均出 71.56。 | 增加 `hasData` 判断（有趋势点且通过率总数大于 0）。没有数据时：得分和风险显示“暂无数据”，不画雷达图和热度图，结论改为空状态。“AI 研判”按钮在没有数据时保持禁用。 | `views/edu/statistics/teaching-issue.vue:166-243` |
| A3 | P1 | #3 管理员在课程详情里看到“实训任务 0”，教师看到 4 | 后端 `filterVisibleHomeworks` 对所有人都只保留自己创建的任务。 | 以下几类用户不过滤：全局管理员（`isGlobalAdmin`）、有公共课审批权限的人、课程负责人、课程团队成员。其他普通教师保留只看自己的规则。可见性判断直接复用现有 `validateCourseAccess`（`:336-350`）的放行集合。班级详情 `buildClassHomeworkList:396` 有同样的按创建人过滤，需一并修改。 | backend `EduCourseManageServiceImpl.java:103-109, 336-350, 396, 406-413` |
| A4 | P1 | #5 学生“能力分析”显示待评定，“实训报告”却显示已达标 | 能力分析的 SQL 只统计挂了一级指标的任务；报告的 `selectOverallStats` 不限制指标。报告里的获证判断又取自能力分析，所以报告本身前后矛盾。 | **已定（D4）**：<br>• 新建任务时没选指标，弹出提醒但不强制；<br>• 任务计数类（总任务、已评定、达标率）两页都统计全部任务；<br>• 能力画像类（雷达图、综合达标率、获证）只统计挂了指标的任务；<br>• 能力分析接口增加 `unindicatedEvaluatedCount`，前端把“待评定”改成说明，例如“有 N 个已评定任务未关联能力指标，不计入能力画像”。 | backend `StudentCapacityMapper.xml:15-32, 78-92`；`StudentCapacityServiceImpl.java:42-84, 162-176`；front `views/edu/student/capacity/index.vue`、`report.vue` |
| A5 | P1 | #6a 学校教务管理员工作台的“需要关注”里没有待审批的公共课 | 前端要同时拥有 `capacity:course:audit` 和 `capacity:indicator:query` 两个权限码才会去查待审批数量。权限码靠菜单行推导，与后端判断可能不一致；另外，后端会过滤掉 `status≠0` 的课程。 | 由后端在管理员看板接口里直接返回 `pendingPublicAuditCount`，用 `canApprovePublicCourse` 判断权限，并且不受“已归档”过滤影响。前端不再靠菜单推导的权限码去单独请求。 | front `views/edu/statistics/admin-dashboard.vue:41-53`、`views/dashboard/shared/admin.ts:67-98`；backend 看板服务 |
| A6 | P2 | #14 只有 1～2 个指标时，雷达图画成一条线 | ECharts 的雷达图至少要 3 个维度才能画出多边形。 | 新增共享函数 `buildCapacityChartOption`：维度少于 3 个时改画横向条形图。三处页面统一使用它。 | `views/edu/statistics/chart-kit.ts`（新增）；`student/capacity/index.vue:66-107`、`report.vue:131-165`、`statistics/skill-assessment.vue:361-390` |

### B. 权限与角色

| 编号 | 优先级 | 问题 | 原因 | 修改方案 | 位置 |
| --- | --- | --- | --- | --- | --- |
| B1 | **P0** | 调研新发现：后端绑定、解绑、恢复班课时**不校验课程归属** | `bindClassCourse`、`unbindClassCourse`、`restoreClassCourse` 只检查菜单权限 `edu:class-course:bind`。目前只有管理员有这个权限，所以还没有暴露；但只要给教师开放，教师就能改别人的课程。 | 在 `EduDataScopeService` 里新增 `validateCourseManage(courseId)`，允许全局管理员、课程负责人、课程团队成员。**不能**复用 `validateCourseAccess`，因为它会放行任何教师访问已上线的公共课。<br>• 绑定：校验每个 `courseId`；<br>• 解绑和恢复：先读出记录，再校验它的 `courseId`。<br>• 补充（审阅意见）：`list-by-class` 接口同样没有归属校验，教师可以列出任意班级的课程，需加 `validateClassAccess`（`EduClassCourseServiceImpl.java:117-128`）；`bindOne` 在班级或课程不存在时会先插入记录（`:166-182`），新校验必须先确认班级存在且未归档；`restoreClassCourse` 也被 `bindOne:162` 内部调用，加校验时要注意抛出的异常是否合适。<br>必须和 B2 一起上线。 | backend `EduClassCourseServiceImpl.java:65-182`、`EduDataScopeService.java:68-81` |
| B2 | P1 | #4 教师不能给自己的课程添加班级 | 教师角色（161）没有菜单 5064 `edu:class-course:bind`；前端按钮只看这个权限码。 | • V19 迁移：给 161 授予 5064（沿用 V16 中带守卫条件的插入写法）；<br>• 前端：“添加班级”、归档、恢复按钮改为 `canManageCourse && 有 bind 权限码` 才显示；<br>• **已定（D1）**：课程负责人和课程团队成员都可以添加班级。前端现有的 `canManageCourse`（`detail.vue:102-113`）不认团队成员，详情接口也不返回团队成员 id，所以由 S1 在 `CourseManageDetailRespVO` 中新增 `canManage` 布尔字段（后端按 `validateCourseManage` 的同一规则计算，并写进 `CONTRACT.md`），前端改用它判断；<br>• 菜单 5065 `edu:class-course:unbind` 从未被代码使用，顺带清理。 | front `views/capacity/course/detail.vue:929, 976, 991`；V19 |
| B3 | P1 | #6 预置角色“学院教务管理员”（160）没有任何菜单 | V1.0.1.4 起有意不授权。另外，`edu.role.admin-codes` 把 `college_admin` 当成全局管理员，会跳过 edu 的业务过滤。 | **本版只用脚手架的部门数据权限**（细节待 D2 确认）：<br>• 角色 160 的数据范围设为“本部门及以下”（`data_scope=4`）。系统模块已经对用户表、部门表启用了部门规则，所以教师、学生、组织列表会自动限定在本学院；<br>• 启用 edu 模块里被注释掉的 `addDeptColumn(EduClassDO)`，让班级列表也按学院过滤；<br>• 把 `college_admin` 从 `edu.role.admin-codes` 中移出；<br>• **已定（D2）：可维护本学院数据。** 授予以下权限：组织查询（不含组织增删改）；班级的新建、修改、归档、学生管理；教师、学生的新建、导入、修改、停用、重置密码；<br>• **写入时补校验**：脚手架的部门规则只作用于查询、修改、删除，**新建不受约束**。因此新建班级、新建或导入师生、给班级加学生时，后端要校验目标组织必须在当前用户的部门数据范围内（用脚手架的 `getDeptDataPermission` 取范围）。导入时逐行校验，超出范围的行在预检中标为“跳过：不在本学院”；<br>• 不授予课程、任务、统计看板，因为它们的表没有 `dept_id`，脚手架规则覆盖不到；<br>• **审阅补充**：<br>&nbsp;&nbsp;① 移出 admin-codes 后，`isGlobalAdmin` 的 7 个调用点要逐个改为“全局管理员，或目标组织在本人部门范围内的管理员”：`EduDataScopeService.java:41,69,102`、`StudentDataExchangeSupport.java:63-73`（导入导出学生）、`EduStatisticsServiceImpl.java:167`、`EduHomeworkRecordServiceImpl.java:712`；<br>&nbsp;&nbsp;② 脚手架改写 UPDATE/DELETE 后，越权的行只是影响 0 行、返回成功，所以每个写操作都必须在服务层显式校验并返回 FORBIDDEN；读不到时现在提示的是“班级不存在”，也要统一成无权限；<br>&nbsp;&nbsp;③ 数据前提：班级挂在专业节点，学院管理员账号必须挂在学院节点。账号未挂部门时，后端直接拒绝并给出明确提示；<br>&nbsp;&nbsp;④ 学院管理员看不到外院教师，班级详情里的教师姓名可能为空，需要处理；<br>&nbsp;&nbsp;⑤ 回归测试：打开 `edu_class` 部门规则后，`StatisticsMapper.xml`、`StudentCourseMapper.xml`、`StudentHomeworkMapper.xml` 中凡是连到 `edu_class` 的地方（含别名、子查询、分页计数）都要逐一验证，确认其他角色（数据范围都是 ALL）不受影响；<br>• 课程、任务、统计按学院过滤（通过任务 → 班级 → 学院关联），留到后续版本，约 2～3 天。 | V19；backend `nus-module-edu/.../framework/datapermission/config/DataPermissionConfiguration.java`；`infra_config edu.role.admin-codes` |
| B4 | P2 | 遗留：学生角色（200）的数据权限还是 ALL | 学生端接口都已显式按登录人过滤，不依赖数据权限。直接改成 SELF 会让学生课程页、留言页里的教师姓名变空（连表时被数据权限过滤掉）。 | 按决策 **D3** 处理（推荐方案 a，暂缓）：<br>• a. 本版不改，在文档里记录理由；<br>• b. 要改的话，先在 `StudentCourseMapper.xml:58,74`、`EduMessageMapper.java:23`、学生个人信息的部门读取处关闭数据权限，再把角色改成 SELF，并冒烟测试学生端三个页面。约半天。 | — |
| B5 | P2 | #17 个人中心的手机、邮箱、性别被标为必填 | 前端 zod 规则没写成可选；后端 `@Length(11,11)` 不接受空字符串。 | 前端改为 `.optional().or(z.literal(''))`，提交前把空字符串转成 `undefined`；后端手机号改为 `@Pattern("^$|^1\\d{10}$")`。 | front `views/_core/profile/modules/base-info.vue:37-64`；backend `UserProfileUpdateReqVO.java:25` |
| B6 | P2 | #18 admin 的“用户类型”显示为“教务管理” | 字典 `system_user_type` 中值 0 的标签是“教务管理”，而所有非教师、非学生的账号默认都是 0。 | V19：把字典标签改为“管理员”（字典数据 id=3036）。 | V19 |
| B7 | P2 | #19 管理员发布的“通知公告”学生和教师都看不到 | 公告只有后台管理接口，没有任何读取它的地方。 | **已定（D5）**：不做公告展示，统一使用右上角铃铛的站内信。V19 中隐藏“基础设施 → 通知公告”菜单（把菜单状态设为停用，数据保留）。手册里不介绍这个菜单。 | V19 |

### C. 界面与文案

| 编号 | 优先级 | 问题 | 原因 | 修改方案 | 位置 |
| --- | --- | --- | --- | --- | --- |
| C1 | P1 | #7 任务中心把未解锁的任务显示为“未提交 / 去提交” | 列表接口不返回锁定状态；工作台的“未解锁”是前端自己算出来的。 | 后端在 `AppStudentHomeworkRespVO` 里增加 `locked` 和 `lockReason`。前端改用 `getStudentTodoStatusMeta` 显示状态；锁定的任务把操作改成“查看解锁条件”，跳到课程详情。 | backend `AppStudentHomeworkRespVO.java`；front `views/edu/student/homework/index.vue:70-80, 213-237` |
| C2 | P1 | #13 新建任务时，“指定课程”“指定班级”没有必填星号；不填就点确认，看不到提示 | 指定课程是自定义的表单项，没有星号也没有错误状态；`classId` 的规则写成了 `.optional()`；校验失败只弹一个 toast，可能被弹窗盖住。 | 给自定义表单项加必填样式和行内错误（新增 `courseError`）；`classId` 改为必填规则。 | front `views/edu/homework/data.ts:197-263`、`HomeworkForm.vue:467-505, 843-860` |
| C3 | P2 | #8 学期收尾弹窗切换页面后不关闭 | 页面被 KeepAlive 缓存，离开时只是失活，弹窗挂在 body 上。 | 在 `onDeactivated` 和 `onBeforeRouteLeave` 里关闭弹窗；提交过程中阻止离开。 | front `views/edu/semester/index.vue:31-33, 175` |
| C4 | P2 | #9 通知里出现“（含 0 份重交）”；“标为已读”图标压住文字 | 通知模板固定拼了 `{resubmitCount}`；通知列表项的按钮是绝对定位，文字列没有给它留出空间。 | • V19 更新模板，把这半句改成参数 `{resubmitNote}`，Java 里重交数为 0 时传空字符串，并同步改单元测试；<br>• 通知组件改成弹性布局：文字列 `min-w-0 flex-1`，按钮列 `shrink-0`。 | backend `EduHomeworkRemindServiceImpl.java:231-234`（及其测试 `:304-307`）；front `packages/effects/layouts/src/widgets/notification/notification.vue:125-152`；V19 |
| C5 | P2 | #10 班级列表的“学制”列被操作列遮住一半 | 各列最小宽度合计 1260px，左侧组织树又占了 240px。 | 操作列从 260 缩到约 200，创建时间列缩到 160，班级编号列缩到 80；1440 宽度下组织树缩为 200px。 | front `views/edu/class/data.ts:239-296`、`index.vue:217` |
| C6 | P2 | #11 新建学生、新建教师弹窗的标题都是“新建用户”；密码框只提示“请输入” | 用户表单写死了“用户”；密码框没有设置提示文字。 | 由调用方传入 `entityName`，标题变为“新建学生 / 编辑教师”等；密码框提示改为“请输入初始密码（8–16 位，须同时包含字母和数字）”。 | front `views/system/user/modules/form.vue:23-35`、`data.ts:106-115`、`views/edu/common/user-management.vue:248-268` |
| C7 | P2 | #12 批改方式：新建时叫“仅人工批改”，详情页叫“教师手动批改” | 两处各自写了一份文字。 | 详情页的文字改为从 `GRADING_STRATEGY_OPTIONS` 读取，只保留一个来源；在术语表 `docs/ui-copy.md` 里补上“仅人工批改”。 | front `views/edu/homework/display.ts:110-118`、`display.test.ts:87-88` |
| C8 | P2 | #15 首次登录的“修改初始密码”页整体发灰 | 尚未确认。推测是登录页的离场动画还没结束就跳转过来，入场动画的类名没有被移除。 | 先在 DevTools 里确认。修复方向二选一：这个路由不使用页面切换动画（`meta.noTransition`）；或者在页面根节点上强制 `opacity:1` 并取消动画。同时确认只触发一次跳转（拦截器和路由守卫都可能跳）。 | front `views/_core/authentication/initial-password.vue`；`packages/effects/layouts/src/authentication/form.vue:17-27` |
| C9 | P2 | #16 添加班级成功后，班级下拉的浮层残留在页面左上角 | 添加成功后列表重新加载，下拉组件在弹窗关闭动画期间被卸载，浮层挂在 body 上没有被清理。 | 弹窗加 `destroy-on-close`；两个下拉都设置 `get-popup-container`；把重新加载放到弹窗完全关闭（`@after-close`）之后。 | front `views/capacity/course/detail.vue:655-705, 1162-1230` |

### D. 维护与遗留

| 编号 | 优先级 | 事项 | 方案 |
| --- | --- | --- | --- |
| D-1 | P2 | #20 课程字段 `isPublicCourse` 的含义与名字相反（0 = 公共课） | 本版不改数据含义：<br>• 改写 4 处接口说明（`@Schema`），并新增常量 `PUBLIC_COURSE = 0`、`EXCLUSIVE_COURSE = 1`；<br>• 返回值里增加派生字段 `publicCourse: Boolean`，前端逐步改用它。 |
| D-2 | P2 | V1.0.1.7 遗留：删除助教 CSV 旧列 | **改为分两步**：<br>• 本版先停止写入这一列。`EduClassCourseServiceImpl.java:172-174` 每次绑定仍会写 `assistant_teacher_ids`，DO 里也还有这个字段；<br>• 下一版再删列，同时修改 `check-v18.py:61,452`。<br>理由：如果本版就删列，回滚到 V1.0.1.7 的后端时绑定会报 SQL 错误，回滚演练就做不了。 |
| D-5 | P2 | 走查 #6 标注“107 未核实” | 开工前先在 107 上查看角色 160 的菜单现状。 |
| D-3 | P2 | V1.0.1.7 遗留：MySQL 健康检查把密码写在命令行参数里 | `deploy/compose.yaml` 的健康检查改用 `MYSQL_PWD` 或密码文件。 |
| D-4 | P2 | 用户手册同步 | 上线后更新以下内容并重新截图：<br>• 教师能自己添加班级、任务中心显示未解锁：手册 2.11、3.3、4.4 节和常见问题 Q3；<br>• 学院教务管理员可以维护本学院数据：手册 2.1、2.13 节和附录的角色权限表；<br>• 通知公告菜单已隐藏。 |

## 3. V19 迁移内容（草案）

文件名：`deploy/database/migrations/V19__v1_0_1_8_flow_and_stats.sql`。每条语句都要带守卫条件，保证重复执行无副作用。

1. 授予教师角色 161 菜单 5064 `edu:class-course:bind`（B2）。
2. 学院教务管理员（B3，方案见第 6 节 D2 和第 8 节）：设置数据范围、授予菜单，并从 `edu.role.admin-codes` 中移出。
3. 更新通知模板 `edu.teacher.homework.submitted` 的内容和参数（C4）。
4. 把字典 `system_user_type` 值 0 的标签改为“管理员”（字典数据 id=3036）（B6）。
5. 停用“通知公告”菜单（菜单 id=107，只改状态、不删除）（B7）。需要核实：超级管理员的菜单列表 `getMenuList()` 停用后是否还会下发；前端有没有路由依赖这个菜单。
6. 清理未使用的菜单 5065：软删除菜单本身，同时软删除 `system_role_menu` 中角色 1 → 5065 的关联（B2）。
7. ~~删除助教 CSV 旧列~~：**本版不删**，见 D-2。

**回滚方案**：V19 的内容全部是可逆的数据更新。回滚方式为：用 `upgrade.sh` 升级前做的数据库备份恢复，再清除恢复后的缓存（`deploy/offline/lib/common.sh:212-216` 的 `clear_restored_db_caches`）。

**权限缓存**：
- 后端判断权限时读的是 Redis 中的菜单角色缓存，直接改 `system_role_menu` 不会让缓存失效。107 走 `upgrade.sh` 时会自动清理（`common.sh:206-208`，包括权限缓存和通知模板缓存）。
- 本机测试环境没有这一步，所以 `e2e_v1018.py` 必须在迁移后清除 `permission_menu_ids`、`menu_role_ids`、`user_role_ids`、`role`、`notify_template` 这几个缓存键。
- 前端的权限码在登录时下发，验收步骤要写明“迁移后重新登录”。

同步修改：

- `NUS-doc` 的基线升到 19，并更新 `check-v19` 迁移检查脚本；
- 权限矩阵期望文件 `deploy/database/tests/permissions/expected-role-endpoints.tsv` 和对应 README。

## 4. 分工与执行

沿用 V1.0.1.7 的方式：按文件互不重叠拆成并行切片；子代理只改代码、不提交，由负责人审查、合并、测试和提交。后端切片优先交给 Codex，前端切片由 Claude 完成。

| 切片 | 内容 | 主要文件 | 执行 |
| --- | --- | --- | --- |
| S1 后端统计 | A3、A4 后端部分、A5 后端部分 | `EduCourseManageServiceImpl`、`StudentCapacity*`、看板服务 | Codex |
| S2 后端权限 | B1、B5 后端部分、C1 后端部分、C4 Java 部分、D-1 后端部分 | `EduDataScopeService`（只加 `validateCourseManage`）、`EduClassCourseServiceImpl`、`UserProfileUpdateReqVO`、`AppStudentHomeworkRespVO`、`EduHomeworkRemindServiceImpl` | Codex |
| S3 前端统计页 | A1、A2、A4 前端部分、A5 前端部分、A6 | `teaching-process.vue`、`teaching-issue.vue`、`chart-kit.ts`、学生能力页、`admin-dashboard` | Claude |
| S4 前端体验 | B2 前端部分、B5 前端部分、C1～C9 前端部分 | 课程详情、任务中心、`HomeworkForm`、学期页、通知组件、班级列表、用户表单、初始密码页 | Claude |
| S6 学院范围（B3） | 启用班级部门规则；7 个 `isGlobalAdmin` 调用点；班级、学生、教师、组织的写操作显式校验；导入时逐行校验范围；数据权限回归测试 | edu `DataPermissionConfiguration`、`EduDataScopeService`（学院范围判断部分）、`StudentDataExchangeSupport`、`EduClassServiceImpl`、`EduClassStudentServiceImpl`、`OrganizationDataExchangeHandler`、`EduStatisticsServiceImpl`、`EduHomeworkRecordServiceImpl` | Codex，负责人重点审查 |
| S5 迁移与测试 | V19、基线 19、权限矩阵、`check-v19`、`e2e_v1018.py`、D-3 | `deploy/`、`NUS-doc/` | 负责人 |

**依赖关系**

- S2 的 B1 必须先于 S5 的 V19 第 1 条合并（先有归属校验，再开放权限）。
- S2 和 S6 都会修改 `EduDataScopeService`：S2 只新增 `validateCourseManage` 方法，S6 修改学院范围相关的判断。两边先在 `CONTRACT.md` 里约定方法签名，合并时由负责人处理冲突。
- V19 第 2 条（学院管理员）必须在 S6 合并之后才能启用。
- S3 和 S4 只依赖 S1、S2 新增的接口字段：`pendingPublicAuditCount`、`unindicatedEvaluatedCount`、`locked`、`lockReason`、`publicCourse`。这些字段先写进共享约定 `CONTRACT.md`，前后端就可以并行开发。

## 5. 测试与验收

**单元测试**

- 后端：
  - 新增 `validateCourseManage` 的用例，覆盖负责人、团队成员、无关教师、公共课的无关教师、管理员；
  - 课程详情任务数按角色区分；
  - 能力分析与报告的口径；
  - 通知模板里重交数为 0 和大于 0 两种情况；
  - 个人资料接口接受空手机号。
- 前端：
  - 统计页没有数据时的显示；
  - 维度少于 3 个时改画条形图；
  - 任务列表的锁定状态；
  - 用户表单标题；
  - 批改方式文案统一；
  - 新建任务表单的必填校验。

**端到端脚本 `e2e_v1018.py`**（沿用 v1015/v1017 的框架）

| 场景 | 断言 |
| --- | --- |
| 教师绑定班课 | 能给自己的课程添加班级；给别人的课程（包括已上线的公共课）添加、解绑、恢复都返回无权限。 |
| 课程任务数 | 管理员、课程负责人、团队成员看到的任务数一致。 |
| 能力口径 | 未挂指标的已评定任务：报告和能力分析的计数一致，并返回 `unindicatedEvaluatedCount`。 |
| 看板待审批 | 学校教务管理员的看板返回 `pendingPublicAuditCount = 1`。 |
| 学院范围 | 学院教务管理员：<br>• 只能看到本学院的组织、班级、教师、学生；<br>• 在本学院新建班级、学生成功；往其他学院新建，或修改其他学院的数据，返回无权限；<br>• 导入时，其他学院的行在预检中被跳过；<br>• 访问课程、任务、看板接口返回无权限。<br>学校教务管理员和平台管理员不受影响。 |
| 锁定状态 | 学生任务列表对锁定的任务返回 `locked = true` 和原因。 |
| 通知文案 | 没有重交时，通知里不含“含 0 份重交”。 |
| 迁移 | `check-v19` 通过（全新安装到 19，以及从 18 升级到 19）；权限矩阵与期望一致。 |

**界面验收**：按 V1.0.1.7 走查记录逐条复查，用同一套截图方式重新截图，并对比修改前后。

**部署验收**：

- 用 `upgrade.sh` 把 107 从 V1.0.1.7 升到 V1.0.1.8，记录停机时长和 Flyway 版本（19）；
- 做一次回滚演练；
- 写 `docs/RELEASE_V1.0.1.8_PVE107_TEST_REPORT.md`。

## 6. 决策记录

| # | 问题 | 结论 |
| --- | --- | --- |
| D1 | 课程团队成员能否添加班级 | **可以**。课程负责人和团队成员都可以添加。 |
| D2 | 学院教务管理员 | 本版用脚手架的部门数据权限，**可维护本学院的班级和师生账号**：查询自动限定范围，所有写操作和导入由后端显式校验。课程、任务、统计按学院过滤留到后续版本。经审阅后确认留在本版，单独作为切片 S6。 |
| D3 | 学生数据权限改为 SELF | 暂缓，本版不改（理由见 B4）。 |
| D4 | 能力分析口径 | 任务计数统计全部任务；能力画像只统计挂了指标的任务，并说明未计入的数量；新建任务时没选指标，弹出提醒但不强制。 |
| D5 | 通知公告 | 不做展示，隐藏菜单，统一使用站内信（铃铛）。 |

## 7. 工作量估计

| 部分 | 估计 |
| --- | --- |
| S1 后端统计 | 0.5～1 天 |
| S2 后端权限 | 1 天 |
| S6 学院范围 | 1.5 天 |
| S3 前端统计页 | 1 天 |
| S4 前端体验 | 1.5 天 |
| S5 迁移、测试、端到端脚本 | 1 天 |
| 集成、打包、107 升级与验收、手册更新 | 1 天 |
| **合计** | **约 7～8 人日**；并行执行时日历时间约 3～4 天 |

课程、任务、统计按学院过滤不在本版范围内，后续单独做约需 2～3 天。

## 8. 审阅记录

2026-10-08 由 Fable 模型独立审阅（只读，核对了代码证据）。结论：A 组、B1、B2、C 组的根因核实无误；有 5 处阻断问题，已全部修订：

| # | 阻断问题 | 处理 |
| --- | --- | --- |
| 1 | 把 `college_admin` 移出 admin-codes 后，学院管理员无法新建和导入师生（7 个 `isGlobalAdmin` 调用点） | B3 列出全部调用点，单独作为切片 S6 |
| 2 | 脚手架改写 UPDATE/DELETE 后，越权操作只是影响 0 行，不会报无权限；班级和学院账号所挂部门的层级前提没有写明 | B3 要求写操作显式校验；补充数据前提和回归测试 |
| 3 | 前端 `canManageCourse` 不认团队成员，与 D1 冲突 | 课程详情接口新增 `canManage` 字段 |
| 4 | 删除助教 CSV 列会让回滚到 V1.0.1.7 失败 | D-2 改为本版停写、下版删列 |
| 5 | V19 清单编号错乱、条目重复，缺少菜单 id | 重写 V19 清单，补充回滚方案和权限缓存说明 |

同时采纳的建议：

- B1 加上 `list-by-class` 接口的校验，以及 `bindOne` 的前置检查；
- A3 同时修改班级详情的任务过滤；
- 端到端测试在迁移后清除权限缓存，验收时重新登录；
- 新增 D-5：开工前核实 107 上角色 160 的菜单现状；
- 手册同步范围扩大（学院管理员、通知公告）。
