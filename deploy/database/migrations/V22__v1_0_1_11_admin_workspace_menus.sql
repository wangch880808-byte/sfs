-- V22: 管理员端对齐（工作台/班课关联/正式课程/课程资源审核菜单骨架）
-- 依据《管理员端对齐清单》P0 菜单骨架 + P1/P2 新页面入口
-- 约定：新菜单不写死 ID，一律按权限标识定位，ID 由自增分配。

-- ============================================================
-- 1. 工作台一级组
-- ============================================================
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '工作台', '', 1, 1, 0, '/workspace', 'ep:monitor',
       NULL, NULL,
       0, b'1', b'1', b'1', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_menu`
                  WHERE `path` = '/workspace' AND `parent_id` = 0 AND `type` = 1 AND `deleted` = b'0')
LIMIT 1;

-- ============================================================
-- 2. 工作台 → 管理总览（复用现有 admin-dashboard 组件）
-- ============================================================
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '管理总览', 'edu:statistics:query', 2, 1, ws.`id`, 'overview', 'ep:data-board',
       'edu/statistics/admin-dashboard', 'AdminDashboard',
       0, b'1', b'1', b'1', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM (SELECT `id` FROM `system_menu`
      WHERE `path` = '/workspace' AND `parent_id` = 0 AND `type` = 1 AND `deleted` = b'0'
      LIMIT 1) ws
WHERE ws.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
                  WHERE `component` = 'edu/statistics/admin-dashboard' AND `parent_id` = ws.`id` AND `deleted` = b'0');

-- ============================================================
-- 3. 工作台 → 待办事项（新页面）
-- ============================================================
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '待办事项', 'edu:workspace:query', 2, 2, ws.`id`, 'todo', 'ep:bell',
       'edu/workspace/todo/index', 'WorkspaceTodo',
       0, b'1', b'1', b'1', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM (SELECT `id` FROM `system_menu`
      WHERE `path` = '/workspace' AND `parent_id` = 0 AND `type` = 1 AND `deleted` = b'0'
      LIMIT 1) ws
WHERE ws.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
                  WHERE `component` = 'edu/workspace/todo/index' AND `deleted` = b'0');

-- ============================================================
-- 4. 工作台 → 运行概况（新页面）
-- ============================================================
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '运行概况', 'edu:workspace:query', 2, 3, ws.`id`, 'runtime', 'ep:trend-charts',
       'edu/workspace/runtime/index', 'WorkspaceRuntime',
       0, b'1', b'1', b'1', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM (SELECT `id` FROM `system_menu`
      WHERE `path` = '/workspace' AND `parent_id` = 0 AND `type` = 1 AND `deleted` = b'0'
      LIMIT 1) ws
WHERE ws.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
                  WHERE `component` = 'edu/workspace/runtime/index' AND `deleted` = b'0');

-- ============================================================
-- 5. 教务组织 → 正式课程管理（新页面）
-- ============================================================
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '正式课程管理', 'edu:official-course:list', 2, 6, edu.`id`, 'official-course', 'ep:collection',
       'edu/officialcourse/index', 'OfficialCourse',
       0, b'1', b'1', b'1', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM (SELECT `id` FROM `system_menu`
      WHERE `id` = 5047 AND `type` = 1 AND `path` = '/edu' AND `deleted` = b'0'
      LIMIT 1) edu
WHERE edu.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
                  WHERE `component` = 'edu/officialcourse/index' AND `deleted` = b'0');

-- ============================================================
-- 6. 教务组织 → 班课关联（新页面）
-- ============================================================
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '班课关联', 'edu:class-course:bind', 2, 8, edu.`id`, 'class-course', 'ep:link',
       'edu/classcourse/index', 'ClassCourse',
       0, b'1', b'1', b'1', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM (SELECT `id` FROM `system_menu`
      WHERE `id` = 5047 AND `type` = 1 AND `path` = '/edu' AND `deleted` = b'0'
      LIMIT 1) edu
WHERE edu.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
                  WHERE `component` = 'edu/classcourse/index' AND `deleted` = b'0');

-- ============================================================
-- 7. 教务组织 → 建班审核（新页面）
-- ============================================================
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '建班审核', 'edu:class-request:list', 2, 9, edu.`id`, 'class-request', 'ep:stamp',
       'edu/classrequest/index', 'ClassRequest',
       0, b'1', b'1', b'1', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM (SELECT `id` FROM `system_menu`
      WHERE `id` = 5047 AND `type` = 1 AND `path` = '/edu' AND `deleted` = b'0'
      LIMIT 1) edu
WHERE edu.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
                  WHERE `component` = 'edu/classrequest/index' AND `deleted` = b'0');

-- ============================================================
-- 8. 基础设施 → 课程资源审核（新页面）
-- ============================================================
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '课程资源审核', 'edu:course-resource:review', 2, 8, infra.`id`, 'resource-review', 'ep:document-checked',
       'edu/courseresource/review', 'CourseResourceReview',
       0, b'1', b'1', b'1', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM (SELECT `id` FROM `system_menu`
      WHERE `id` = 2 AND `type` = 1 AND `path` = '/infra' AND `deleted` = b'0'
      LIMIT 1) infra
WHERE infra.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
                  WHERE `component` = 'edu/courseresource/review' AND `deleted` = b'0');

-- ============================================================
-- 9. 按钮权限：正式课程管理
-- ============================================================
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT button.`name`, button.`permission`, 3, button.`sort`, page.`id`, '', '', '', NULL,
       0, b'1', b'1', b'1', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM (
    SELECT '正式课程查询' AS `name`, 'edu:official-course:list' AS `permission`, 1 AS `sort`,
           'edu/officialcourse/index' AS `component`
    UNION ALL SELECT '正式课程新增', 'edu:official-course:create', 2, 'edu/officialcourse/index'
    UNION ALL SELECT '正式课程编辑', 'edu:official-course:update', 3, 'edu/officialcourse/index'
    UNION ALL SELECT '正式课程归档', 'edu:official-course:archive', 4, 'edu/officialcourse/index'
) button
JOIN (SELECT `component`, MIN(`id`) AS `id` FROM `system_menu`
      WHERE `component` = 'edu/officialcourse/index' AND `type` = 2 AND `deleted` = b'0'
      GROUP BY `component`) page ON page.`component` = button.`component`
WHERE NOT EXISTS (SELECT 1 FROM `system_menu`
                  WHERE `permission` = button.`permission` AND `deleted` = b'0');

-- ============================================================
-- 10. 按钮权限：建班审核
-- ============================================================
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT button.`name`, button.`permission`, 3, button.`sort`, page.`id`, '', '', '', NULL,
       0, b'1', b'1', b'1', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM (
    SELECT '建班审核查询' AS `name`, 'edu:class-request:list' AS `permission`, 1 AS `sort`,
           'edu/classrequest/index' AS `component`
    UNION ALL SELECT '建班审核通过', 'edu:class-request:approve', 2, 'edu/classrequest/index'
    UNION ALL SELECT '建班审核拒绝', 'edu:class-request:reject', 3, 'edu/classrequest/index'
) button
JOIN (SELECT `component`, MIN(`id`) AS `id` FROM `system_menu`
      WHERE `component` = 'edu/classrequest/index' AND `type` = 2 AND `deleted` = b'0'
      GROUP BY `component`) page ON page.`component` = button.`component`
WHERE NOT EXISTS (SELECT 1 FROM `system_menu`
                  WHERE `permission` = button.`permission` AND `deleted` = b'0');

-- ============================================================
-- 11. 按钮权限：课程资源审核
-- ============================================================
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT button.`name`, button.`permission`, 3, button.`sort`, page.`id`, '', '', '', NULL,
       0, b'1', b'1', b'1', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM (
    SELECT '资源审核通过' AS `name`, 'edu:course-resource:approve' AS `permission`, 1 AS `sort`,
           'edu/courseresource/review' AS `component`
    UNION ALL SELECT '资源审核驳回', 'edu:course-resource:reject', 2, 'edu/courseresource/review'
) button
JOIN (SELECT `component`, MIN(`id`) AS `id` FROM `system_menu`
      WHERE `component` = 'edu/courseresource/review' AND `type` = 2 AND `deleted` = b'0'
      GROUP BY `component`) page ON page.`component` = button.`component`
WHERE NOT EXISTS (SELECT 1 FROM `system_menu`
                  WHERE `permission` = button.`permission` AND `deleted` = b'0');

-- ============================================================
-- 12. 角色授权：学校教务管理员(159) + 学院教务管理员(160) + 教师(161 建班申请)
-- ============================================================
-- 工作台组 + 子页授权给教务管理员
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT role.`id`, menu.`id`, 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM `system_role` role
JOIN `system_menu` menu ON menu.`deleted` = b'0' AND menu.`status` = 0
    AND menu.`component` IN ('edu/statistics/admin-dashboard', 'edu/workspace/todo/index', 'edu/workspace/runtime/index',
                             'edu/officialcourse/index', 'edu/classcourse/index', 'edu/classrequest/index',
                             'edu/courseresource/review')
    AND role.`id` IN (159, 160) AND role.`deleted` = b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_role_menu` rm
                  WHERE rm.`role_id` = role.`id` AND rm.`menu_id` = menu.`id` AND rm.`deleted` = b'0');

-- 工作台组本身（一级菜单）也要授权，否则子页看不到
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT role.`id`, menu.`id`, 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM `system_role` role
JOIN `system_menu` menu ON menu.`deleted` = b'0' AND menu.`status` = 0
    AND menu.`path` = '/workspace' AND menu.`type` = 1 AND menu.`parent_id` = 0
    AND role.`id` IN (159, 160) AND role.`deleted` = b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_role_menu` rm
                  WHERE rm.`role_id` = role.`id` AND rm.`menu_id` = menu.`id` AND rm.`deleted` = b'0');

-- 按钮权限也授权给教务管理员
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT role.`id`, menu.`id`, 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM `system_role` role
JOIN `system_menu` menu ON menu.`deleted` = b'0' AND menu.`status` = 0 AND menu.`type` = 3
    AND menu.`permission` IN ('edu:official-course:list', 'edu:official-course:create', 'edu:official-course:update',
                              'edu:official-course:archive', 'edu:class-request:list', 'edu:class-request:approve',
                              'edu:class-request:reject', 'edu:course-resource:approve', 'edu:course-resource:reject')
    AND role.`id` IN (159, 160) AND role.`deleted` = b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_role_menu` rm
                  WHERE rm.`role_id` = role.`id` AND rm.`menu_id` = menu.`id` AND rm.`deleted` = b'0');

-- 教师角色(161)：建班申请（教师提交建班申请用）
-- 注：教师端建班申请按钮挂在课程详情中，这里只授权建班审核查询（教师可能需要看自己提交的申请状态）
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT role.`id`, menu.`id`, 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM `system_role` role
JOIN `system_menu` menu ON menu.`deleted` = b'0' AND menu.`status` = 0
    AND menu.`permission` = 'edu:class-request:list' AND menu.`type` = 3
    AND role.`id` = 161 AND role.`deleted` = b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_role_menu` rm
                  WHERE rm.`role_id` = role.`id` AND rm.`menu_id` = menu.`id` AND rm.`deleted` = b'0');
