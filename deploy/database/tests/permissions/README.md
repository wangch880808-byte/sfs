# 权限回归测试

接口快照由后端工作树 `nus-server` 的 `EndpointPermissionsSnapshotTest` 生成，记录 HTTP 方法、完整路径和方法安全要求。`PERMIT_ALL`、`AUTHENTICATED` 及学生端无角色校验的现有接口均在测试类中逐项说明；小程序两组路径也在测试中固定。

先在后端工作树运行普通检查：

```sh
TZ=Asia/Shanghai mvnd -o -q -pl nus-server -am test -Dtest=EndpointPermissionsSnapshotTest -Dsurefire.failIfNoSpecifiedTests=false
```

确认接口变更合理后，附加 `-Dnus.permission.snapshot.update=true` 重写 `nus-server/src/test/resources/security/endpoint-permissions.tsv`。更新模式仍执行硬性断言；若新增匿名或只需登录的接口，先在测试类中给出逐项理由。

然后在主项目根目录运行：

```sh
python3 deploy/database/tests/check-permissions.py --self-test
python3 deploy/database/tests/check-permissions.py --update --report /tmp/nus-permissions-report.md
python3 deploy/database/tests/check-permissions.py --report /tmp/nus-permissions-report.md
```

脚本默认读取本仓库的 `NUS-backend/nus-server/src/test/resources/security/endpoint-permissions.tsv` 的快照；其他布局使用 `--snapshot /absolute/path/to/endpoint-permissions.tsv`。矩阵更新只应在审查菜单授权和可疑项后提交。脚本只用本地 MySQL 镜像，`--pull=never --network none --tmpfs /var/lib/mysql`，并在退出时销毁容器；可用 `NUS_TEST_MYSQL_IMAGE` 指定已缓存的 MySQL 镜像。脚本读取基线顶部的 `-- Flyway baseline version: N` 标记，与 Flyway 一致，只按版本号依次执行比 N 新的迁移（基线 18 时执行 V19）；基线同步到新版本后无需改脚本。

审查报告中的各角色接口数量和“学生可调用的非学生端接口”，尤其检查从 `0` 变为 `1` 的矩阵单元、任何 `PERMIT_ALL`/`AUTHENTICATED` 变化，以及学生端无 `hasRole('student')` 的例外。

V1.0.1.7 的 `expected-role-endpoints.tsv` 按基线 17 加 V18 生成；V19 的预期见下文。V18 相对 V17 的变化：新增 18 个接口（批改队列与撤销、个别延期与催交、任务复制、学期收尾与可选学期、提示词与调用日志）；教师可用其中的批改、延期、催交、复制和可选学期，提示词、调用日志和学期收尾只有超级管理员可用。公共课审批 `POST /capacity/course/audit-public` 由写死角色改为按钮权限 `capacity:course:audit`，授予学校教务管理员（159），并连带课程页面与 `capacity:indicator:query`，使 159 新增课程与指标查询、课程详情、班级详情和以管理者身份给学生留言共 7 个接口；学院教务管理员（160）不授予。学生可调用的接口没有变化。审核人改动迁移或接口后，在可运行 Docker 的环境执行一次 `--update`，核对产生的 diff 和报告，再运行无 `--update` 的校验。

## V1.0.1.8 的预期

V19 给教师（161）授予 `edu:class-course:bind`，矩阵中 `POST /edu/class-course/bind`、`DELETE /edu/class-course/unbind`、`PUT /edu/class-course/restore` 改为允许；业务层仍须校验课程负责人或团队成员，公共课的无关教师无权修改。

**160 的授权以 S6 片段为准，合并后核对**。期望先按以下权限标识计算：

- 组织查询：`system:dept:query`，不含组织新建、修改、删除。
- 班级管理：`edu:class:query/create/update/delete`，包含学生管理和归档、恢复。
- 师生管理与导入：`system:user:query/create/update/delete/update-password/import`。
- 不授予课程、任务、统计的菜单权限。`PERMIT_ALL`、`AUTHENTICATED` 及现有学生端无角色检查的接口沿用原矩阵；此矩阵描述方法安全入口，不能代替业务层身份和学院范围校验。

共享权限码会连带开放班级学生导出、组织导出、学生数据导出和通用导入批次接口。通用导入入口允许并不代表 160 可以导入任意业务：业务处理器仍须拒绝课程、任务及组织增删改。合并 S6 后重点检查这些连带入口及全局角色回归。

指定基线或快照文件时：

```sh
python3 deploy/database/tests/check-permissions.py \
  --baseline 'NUS-doc/3-数据库文件/ry261-test.sql' \
  --snapshot NUS-backend/nus-server/src/test/resources/security/endpoint-permissions.tsv \
  --report /tmp/permissions-report.md
```

接口或权限有改动时，先运行矩阵校验并逐条审查差异，再决定是否使用 `--update`，不要用更新模式掩盖遗漏的授权。部署后清 Redis 权限与通知模板缓存并重新登录，命令见 `deploy/tests/e2e/README.md`。
