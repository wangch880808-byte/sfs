# V1.0.1.10 计划：公共课教师认领班级

日期：2026-10-12。状态：用户已同意方向，待 Fable 审阅后开工。

## 一、需求（用户确认）

只针对**公共课**（`capacity_course.is_public_course` 为公共课类型）；专属课程保持现在的“负责人 + 课程团队”模式不变。

1. **认领**：所有教师都可以认领公共课里还没人认领的班级。“添加班级”就是认领。同一班级、同一课程、同一学期只有一位认领人。
2. **只看、只管自己的班级**：
   - 教师打开公共课，只看到自己认领的班级和自己布置的任务；
   - 布置任务、查看班级详情、学生名单、留言、成绩、统计分析，都只限自己认领的班级；
   - 不看、也不干涉别人认领的班级。
3. **添加（认领）班级的弹窗**：已被认领的班级排在列表最下方、变灰不可选，标明“已被××老师认领”；自己已认领的同样灰显，标“已认领”。
4. **角色**：
   - **课程负责人**：能**只读**查看全部班级和任务；自己认领的班级照常管理；仍负责维护课程信息与资源。
   - **课程团队成员**：与普通教师一样，只看自己认领的班级。
   - **管理员、公共课审批人**：看全部。
5. **放弃与转移**：
   - 教师可以随时放弃自己的认领；已经布置的任务仍归原布置者查看、批改；
   - 管理员和公共课审批人可以清除任一班级的认领（教师离职等情况）。
6. **跨学期**：每学期独立认领。学期收尾会归档旧学期的班课；新学期由教师重新认领。
7. **历史数据**：公共课的班级关联都是系统同步自动建的（`creator='system'`），没有真实的“添加人”。升级时按已布置任务回填认领人：本学期该班这门课里**只有一位**教师布置过任务的，认领人就是他；其余留空，由教师自己认领。
8. **版本**：V1.0.1.10，迁移 V21。

## 二、现状要点（盘点结论）

- **自动同步**：`EduPublicCourseSyncService` 会给每门在线公共课、每个正常班级，在当前学期自动建一条 `edu_class_course`（`creator/updater='system'`，`backfill_pending=1`）。
  - 唯一键是 `(class_id, course_id, semester_name, deleted)`，所以认领应写在已有的关联行上；没有这行时（例如公共课尚未审批、班级是新建的）才新建。
- **同步靠 `updater` 判断**：自动归档、恢复都依赖 `updater='system'` / `'system-sync'`，见 `EduClassCourseMapper.java:23-63`。用 `updateById` 写认领人会被字段填充改掉 `updater`，要专门处理。
- **任意教师放行**：在线公共课现在对任意教师放行的位置：
  - `EduDataScopeService` 的 `validateClassAccess:61-63`、`validateCourseAccess:79`、`validateClassCourseAccess`；
  - `EduAiBusinessAccessService.validateCourseClassAccess:94`；
  - `EduCourseManageServiceImpl:384`；
  - `EduHomeworkAiCreateServiceImpl:380`。
- **审批人口径不一**：`EduDataScopeService` 只认 `isGlobalAdmin`，课程详情另外认审批人（`capacity:course:audit`）。
- **现存越权**（不修的话，收窄可以被绕过）：
  - `work-trend`、`course-analysis` 没有数据范围校验；
  - 花名册导入、导出经 `validateClassAccess` 的公共课分支，对所有教师放行。
- **公共课补发**（`EduPublicCourseBackfillService`）：现在的“全课程任务”指一位教师覆盖全部在用班级的任务。认领模式下需要改口径。

## 三、设计

### 1. 数据（V21）

- **新增列**：`edu_class_course` 新增 `claim_teacher_id BIGINT NULL`（认领教师）、`claim_time DATETIME NULL`。
- **新增索引**：`(course_id, claim_teacher_id, status)`、`(claim_teacher_id, status)`。
- **回填**：只处理公共课类型课程的关联行。在 `edu_homework`（未删除、已发布或已结束）里，按 `(course_id, class_id)` 和学期匹配（沿用现有的 TRIM 学期口径）找布置者：
  - 唯一一位的，回填 `claim_teacher_id`，`claim_time` 取该教师最早一条任务的创建时间；
  - 回填**不改** `updater`，以免影响同步判断。
- **幂等**：可重复执行，只填 `claim_teacher_id IS NULL` 的行。
- **配套**：基线升到 21，提供 check-v21。

### 2. 规则：谁能做什么（公共课）

| 角色 | 认领 / 放弃 | 看班级 | 布置任务 | 看任务、提交、成绩 | 批改 |
| --- | --- | --- | --- | --- | --- |
| 普通教师、团队成员 | 认领未认领的班；放弃自己的 | 只看自己认领的 | 只能给自己认领的班 | 自己布置的 | 自己布置的 |
| 课程负责人 | 同上 | 全部（只读，标明认领人） | 只能给自己认领的班 | 全部（只读） | 自己布置的 |
| 管理员、审批人 | 可以清除任一认领 | 全部 | 与现在相同 | 全部（只读） | 与现在相同 |
| 任务布置者（已不再认领该班） | — | 不再看到该班 | 不能再布置 | 仍能看自己布置的任务 | 仍能批改自己布置的任务 |
| 班课助教 | — | 与现在相同（按班课） | — | 与现在相同 | 与现在相同 |

- **统一判断**：公共课的“能否操作这个班”统一为“认领人是当前用户”，用 `EduDataScopeService` 新增的方法：
  - `isPublicCourse(courseId)`；
  - `canViewAllPublic(courseId)`（管理员、审批人、负责人）；
  - `isClaimedBy(classCourse, userId)`；
  - `claimedClassIds(courseId, userId)`。
- **审批人口径**：把审批人（`capacityCourseApi.isCourseAdmin`）并入 `EduDataScopeService` 的“看全部”判断，与课程详情对齐。

### 3. 接口

| 接口 | 说明 |
| --- | --- |
| `GET /edu/class-course/claim-options?courseId=&semesterName=` | 认领弹窗的班级列表。学期为空时取当前学期。返回全部正常班级，每项包含 `classId, className, gradeYear, deptName, classCourseId?, claimTeacherId?, claimTeacherName?, state`；`state` 取 `AVAILABLE` / `MINE` / `TAKEN`。排序：AVAILABLE 在前，其次 MINE，最后 TAKEN；同组按班级名排序 |
| `POST /edu/class-course/claim`，请求 `{courseId, classIds[], semesterName?}` | 认领。<br>• 有关联行：用 `UPDATE ... SET claim_teacher_id=?, claim_time=NOW() WHERE id=? AND claim_teacher_id IS NULL` 并检查影响行数，防止并发抢占；归档的行一并恢复（status=0）。<br>• 没有关联行：新建一行并写入认领人（creator 为当前用户）。<br>• 任一班级已被他人认领时整体失败，提示“班级「X」已被××老师认领”。<br>• 认领成功后触发“认领教师自己的全课程任务”补发（见第 5 节）。<br>• 权限码 `edu:class-course:bind`，教师已有 |
| `PUT /edu/class-course/release?id=` | 放弃或清除认领。认领人本人、管理员、审批人可以操作；把 `claim_teacher_id`、`claim_time` 置空，任务不动 |
| `/edu/class-course/bind`、`unbind`、`restore` | 公共课上 `bind` 等同认领。`unbind`、`restore` 只允许认领人（以及管理员、审批人）操作；负责人不能动别人的班 |
| 课程详情 `GET /edu/course-manage/detail` | 新增：<br>• 课程级 `claimMode`（是否公共课）、`viewAllReadOnly`（负责人只读看全部）；<br>• 班级行 `claimTeacherId`、`claimTeacherName`、`canOperate`（可布置任务、管理）。<br>普通教师只返回自己认领的班级和自己布置的任务；统计随之收窄 |
| `list-by-course`、`list-by-class`、`copy/targets`、AI 新建任务、题库生成任务 | 公共课只返回或允许自己认领的班级（负责人、管理员只读看全部，但不能布置到别人的班） |
| 班级详情 `class-detail` | 公共课：认领人可看；负责人、管理员、审批人只读；授课教师显示认领人 |
| 留言 | 校验与接收人加入认领人；学生端会话显示的教师为认领人（未认领时为课程负责人） |
| 统计 | 见第 4 节 |

### 4. 统计与越权修复

- `course-options`、`class-options`：教师可以看到自己认领过的公共课和对应班级；`class-options` 顺带补上课程团队。
- `work-trend`、`course-analysis`、`total-pass-rate`、AI 研判：加课程和班级的数据范围校验。公共课教师限自己认领的班级，负责人、管理员、审批人看全部。
- 教师看板：“我的课程”计入认领的公共课；未读留言计入认领班级。
- 花名册导入、导出：公共课分支收窄为认领人（以及管理员）。

### 5. 公共课补发

- **改为在认领时触发**：教师认领一个班级后，把该教师在本课程本学期“覆盖其全部其他已认领在用班级”的进行中任务补发给新班级，布置者仍是该教师。
- **只补发认领教师自己的任务**：不再补发其他教师的任务。
- **系统同步建关联时不再补发任务**：只保留“课程已分配”的学生通知行为，与现在相同。

### 6. 同步的兼容

- 认领、放弃都只更新认领字段，**保留原 `updater`**。
- 自动归档的“空闲自动关联”条件加上 `claim_teacher_id IS NULL`：已认领的关联不会被同步自动归档。

### 7. 前端

- **课程详情（公共课）**：
  - **按钮**：“添加班级”改名“认领班级”，弹窗数据取 `claim-options`：可认领的在上，已被认领、已认领的在下方灰显并标明认领人。
  - **班级列表**：只显示自己认领的；自己的行可以“放弃认领”（二次确认，提示“已布置的任务仍归你批改”）。
  - **负责人视角**：看全部，每行显示认领人，不能操作别人的行，顶部提示“只读查看其他教师的班级”。
  - **指标卡、任务 tab**：随后端收窄。
- **任务表单、复制、AI 新建任务、题库生成任务**：班级选项只含 `canOperate` 的班级。
- **班级详情页**：授课教师显示认领人；只读时隐藏发送留言。
- **统计页、留言页**：依赖后端收窄，并检查空状态文案。
- **管理员**：在课程详情的班级行上可以“清除认领”。

### 8. 测试与验收

- **后端**：
  - 权限矩阵：普通教师、团队成员、负责人、管理员、审批人、助教 × 自己认领、他人认领、未认领 × 读与写；
  - 认领并发：两人同时认领同一班，只有一人成功；
  - 放弃后原布置者仍可批改；
  - 同步不归档已认领的关联；
  - 补发口径。
- **迁移**：check-v21，覆盖全新安装、带数据升级（含回填唯一与多人两种情况）、重复执行、部分执行后恢复，以及基线 21 一致性。
- **端到端**：新增 `e2e_v1019.py`。两位教师 + 负责人 + 管理员在同一门公共课里：
  - 认领、灰显列表；
  - 可见性；
  - 不能给别人的班布置任务；
  - 放弃；
  - 管理员清除；
  - 统计与留言越权被拒。

  原有 v1018、v1017、v1015 回归全部通过。
- **前端**：单测、类型检查、构建；浏览器用两位教师和负责人逐项核对。
- **性能**：用 `deploy/tests/perf` 复测，查询条数不回退。
- **发布**：打包后在 107 上升级、回滚、再升级。

## 四、分工

| 分片 | 内容 | 执行 |
| --- | --- | --- |
| S0 | `EduClassCourseDO` 增加 `claimTeacherId`、`claimTime` 字段，并定好接口契约（本计划第三节）。先提交到基线分支，其余分片基于它开发 | 负责人 |
| S-DATA | V21 迁移与回填、基线 21、check-v21 | Codex |
| S-CLAIM | 认领、放弃、`claim-options` 接口；`bind/unbind/restore` 的公共课分支；同步兼容；补发改为认领时触发 | Codex |
| S-SCOPE | `EduDataScopeService`、`EduAiBusinessAccessService` 收窄；课程详情、班级详情、任务创建与复制、AI 新建任务、留言、花名册的公共课收窄 | Codex |
| S-STAT | 统计 SQL 与服务：课程、班级选项，越权修复，教师看板 | Codex |
| S-FE | 前端全部 | Claude 子代理 |
| S-V | 合并、全量测试、e2e_v1019、浏览器验证、性能复测、打包、107 | 负责人 |

## 五、Fable 审阅后的修订（2026-10-12）

**结论**：方向成立，以下 7 处已改入设计。

1. **回填排除补发任务**：只统计 `source_homework_id IS NULL` 的任务。补发任务由同步按同一教师复制到所有班，会把几乎所有班判给一人。其他条件：
   - 学期口径沿用 `EduClassCourseMapper:28`（任一方为空即匹配，否则两边 TRIM 后相等）；
   - 只回填 `status=0` 的行。
   - 历史补发任务留在原处，仍归原布置者查看、批改。
2. **拆分两种校验**：
   - `canAssignToClass(courseId, classId)`：认领人或管理员。用于新建、修改、复制任务，AI 新建任务，题库生成任务。
   - 基于任务行的访问：布置者本人或 `canViewAllPublic`。`requireHomeworkAccess`、`requireHomeworkViewAccess` 都改用它。
   - 这样，放弃认领后，布置者仍能对自己的任务做 AI 批改、导入成绩册。
3. **“看全部”的口径**：`canViewAllPublic = 管理员 || 审批人(isCourseAdmin) || 课程负责人`，**不含团队成员**。
   - 公共课下统一用它的地方：`requireHomeworkViewAccess`、`canViewAllHomeworks`、`filterVisibleHomeworks`、学生详情的负责人判定；
   - `canManageCourse` 保持原义，只用于专属课和维护课程信息。
4. **认领时加锁**：
   - 认领先取 `lockPublicCourse(courseId)`，与同步、补发是同一把锁；
   - 锁内按 TRIM 学期重新查询，再决定 UPDATE 还是 INSERT；
   - INSERT 遇到 `DuplicateKeyException` 时改走 UPDATE；
   - 写入的学期一律 TRIM。
5. **认领时补发**：新增 `backfillForClaim(courseId, semester, teacherId, classId)`。
   - 取课程锁；
   - 覆盖范围为“该教师在本课程其他已认领的在用班级”；
   - 复用 `courseWideGroups` 与 `replicate`。

   同步路径保留通知，并照常调用 `completeBackfill` 清除标记，但不再补发任务；否则空闲行永远不会被自动归档。
6. **只认领在线课程**：认领和 `claim-options` 都要求课程在线（公共课、审批通过、未归档），否则拒绝。
   - 恢复已归档行时，若 `updater='system-sync'`，改回 `'system'`，与 `restoreSyncArchivedLinks` 对齐。
   - 认领时新建的行 `creator/updater='system'`，与同步行一致，放弃后照常参与自动归档。
7. **学期收尾**：`isAutoCreated := creator='system' && claimTeacherId == null`。已认领的班课算“在上课”。

**补充决定**：

- **留言接收人**：公共课改为班级认领人（加上班课助教）；未认领的班级发给课程负责人；团队成员不再接收。
- **布置任务**：审批人不能给班级布置任务；管理员与现在相同。
- **文件归属**：
  - S0 直接实现 `EduDataScopeService` 的认领相关方法（含 Mapper 查询）；
  - `EduClassCourseServiceImpl` 整个文件归 S-CLAIM（包括 `listByCourse`、`listByClass` 的收窄）；
  - S-SCOPE 只调用 S0 提供的方法。
- **命名**：端到端测试命名 `e2e_v10110.py`；`check-v21.py` 放在 `deploy/database/tests/`。
