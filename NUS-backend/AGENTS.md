# 后端补充规则（NUS-backend）

先读根目录 `AGENTS.md`。本文件只写后端特有的规则和踩过的坑。

## 运行与测试

- JDK 17，Maven 3.9（也可以用 mvnd，命令相同）。
- 单元测试：`TZ=Asia/Shanghai mvn -q test`。只跑一个模块：`-pl nus-module-edu -am`；只跑一个类：`-Dtest=类名 -Dsurefire.failIfNoSpecifiedTests=false`。
- 真实 MySQL 集成测试（需要 Docker）：
  ```bash
  TZ=Asia/Shanghai mvn -q test -pl nus-module-edu -am -Pdb-it \
    -Dnus.it.baseline-sql=$PWD/../NUS-doc/3-数据库文件/ry261-test.sql
  ```
  改了复杂 SQL、迁移、索引时必须跑。
- 本地启动：环境变量 `SPRING_PROFILES_ACTIVE=local`，主类 `NusServerApplication`，端口 48080。数据库和 Redis 的连接方式见本目录 `README.md`。

## 数据与权限

1. **逻辑删除**：所有表都有 `deleted` 字段。Wrapper 查询会自动加条件，手写 SQL（XML、`@Select`、`inSql`）必须自己写 `deleted = 0`，每张表都要。
2. **数据权限拦截器**：会给 `edu_class`、`system_users`、`system_dept` 自动追加院系条件，子查询、派生表里也会改写。
   - 写涉及这三张表的 SQL 后，用 H2（MySQL 模式）走真实拦截器链测一遍，参考 `EduClassDataPermissionSqlTest`、`EduImportBatchLookupSqlTest`。
   - 已知例外：JOIN 的 ON 条件内部的嵌套 EXISTS 不会被改写。
3. **公共课的权限判断**统一调用 `EduDataScopeService`，不要自己另写：
   - `isPublicCourse`、`canViewAllPublic`（管理员、审批人、课程负责人）；
   - `isClaimedByCurrentUser`、`canAssignToPublicClass`、`canViewPublicClass`。
   - 规则：认领人才能布置；负责人只读看全部；布置者永远能看、能批改自己的任务。
4. **认领字段**（`edu_class_course.claim_teacher_id`、`claim_time`）只能用条件 UPDATE 写，不要 `updateById`。原因：公共课同步靠 `updater='system'` 判断是否为自动关联，`updateById` 会改掉 `updater`。
5. **任务对学生可见，靠的是分配快照**（`edu_homework_assignee`），不是“班里有谁”。
   - 学生进班、出班，必须同步未截止任务的分配。所有入口都要走 `EduClassStudentService`：
     - 班级管理；
     - 学生导入；
     - 用户管理改班（通过 `StudentClassesChangedEvent`）。
   - 新增入班入口时不能漏掉同步。
6. **状态口径**：
   - 任务状态：0 待发布（旧草稿，不再新建）、1 已发布、2 已截止（后端不写，按截止时间判断）、3 已封存。
   - 班级、课程、班课：0 在用，1 已封存。
   - **统计**：分析类（合格率、趋势、课程分析、排名）计入已封存；看板总数和待办类只算在用；已删除的一律不计。

## 编码约定

- **错误码**：在 `ErrorCodeConstants` 里按模块号段新增，不能重号，`ErrorCodeConstantsUniqueTest` 会检查。提示语用中文，对用户说清楚怎么办。
- **防重复提交**：会产生数据的接口加 `@Idempotent`。前端会自动带请求指纹作为幂等键。
- **缓存**：`@Cacheable` 的名字写成 `名字#过期时间`；改数据的地方记得清缓存。升级脚本会清除参数、学期、指标、字典、权限缓存，新增缓存类别时要同步改 `deploy/offline/lib/common.sh`。
- **性能**：
  - 列表和详情接口不要逐行查询，用批量查询或 SQL 聚合；
  - 小范围的权限条件用相关 EXISTS，不要用全库 `IN (SELECT DISTINCT …)`；
  - 新加的状态类字段如果要过滤，记得在迁移里建直方图，参考 V20。
- **配置**：`application-local.yaml` 只用于本机，打包时会被排除；`application-prod.yaml` 的值全部来自环境变量。不要在任何 yaml 里写真实口令。
- **注释**：写“为什么”，不写“改了什么”；用中文。

## 改动检查清单

- [ ] 新接口：权限点、`@Idempotent`、权限快照 `endpoint-permissions.tsv`。
- [ ] 新 SQL：`deleted = 0`、数据权限测试、必要时跑 db-it。
- [ ] 改表结构：新迁移、基线、check 脚本（见 `docs/03-开发/数据库迁移.md`）。
- [ ] 状态或统计口径：对照上面“状态口径”，并检查学生端、看板、分析三处是否一致。
