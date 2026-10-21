-- ============================================================
-- Sprint 12：学期管理 + 顶部当前学期展示
-- 目标：
-- 1) 在“教务组织(5047)”下新增“学期管理”菜单
-- 2) 新增学期管理权限点（查询/更新）
-- 3) 初始化 infra_config 中的当前学期配置项
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- 1. 菜单与权限
-- ============================================================

-- 1.1 学期管理菜单（教务组织下）
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 5119,
       '学期管理',
       'edu:semester:query',
       2,
       6,
       5047,
       'semester',
       'ep:calendar',
       'edu/semester/index',
       'EduSemester',
       0,
       b'1',
       b'1',
       b'1',
       'admin',
       NOW(),
       'admin',
       NOW(),
       b'0' WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 5119);

-- 1.2 按钮权限：查询
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 5120,
       '查询学期',
       'edu:semester:query',
       3,
       1,
       5119,
       '',
       '#',
       '',
       0,
       b'1',
       b'1',
       b'1',
       'admin',
       NOW(),
       'admin',
       NOW(),
       b'0' WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 5120);

-- 1.3 按钮权限：更新
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 5121,
       '更新学期',
       'edu:semester:update',
       3,
       2,
       5119,
       '',
       '#',
       '',
       0,
       b'1',
       b'1',
       b'1',
       'admin',
       NOW(),
       'admin',
       NOW(),
       b'0' WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 5121);

-- 1.4 超级管理员角色授权（role_id=1）
INSERT INTO `system_role_menu`
(`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1,
       5119,
       'admin',
       NOW(),
       'admin',
       NOW(),
       b'0' WHERE NOT EXISTS (SELECT 1 FROM `system_role_menu` WHERE `role_id` = 1 AND `menu_id` = 5119 AND `deleted` = b'0');

INSERT INTO `system_role_menu`
(`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1,
       5120,
       'admin',
       NOW(),
       'admin',
       NOW(),
       b'0' WHERE NOT EXISTS (SELECT 1 FROM `system_role_menu` WHERE `role_id` = 1 AND `menu_id` = 5120 AND `deleted` = b'0');

INSERT INTO `system_role_menu`
(`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1,
       5121,
       'admin',
       NOW(),
       'admin',
       NOW(),
       b'0' WHERE NOT EXISTS (SELECT 1 FROM `system_role_menu` WHERE `role_id` = 1 AND `menu_id` = 5121 AND `deleted` = b'0');

-- ============================================================
-- 2. 初始化当前学期配置（infra_config）
-- ============================================================

INSERT INTO `infra_config`
(`category`, `type`, `name`, `config_key`, `value`, `visible`, `remark`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'edu',
       2,
       '当前学期',
       'edu.current.semester.name',
       '未设置',
       b'1',
       '教学平台当前学期名称',
       'admin',
       NOW(),
       'admin',
       NOW(),
       b'0' WHERE NOT EXISTS (
  SELECT 1 FROM `infra_config` WHERE `config_key` = 'edu.current.semester.name' AND `deleted` = b'0'
);

INSERT INTO `infra_config`
(`category`, `type`, `name`, `config_key`, `value`, `visible`, `remark`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'edu',
       2,
       '当前学期开始日期',
       'edu.current.semester.start-date',
       '',
       b'1',
       '教学平台当前学期开始日期',
       'admin',
       NOW(),
       'admin',
       NOW(),
       b'0' WHERE NOT EXISTS (
  SELECT 1 FROM `infra_config` WHERE `config_key` = 'edu.current.semester.start-date' AND `deleted` = b'0'
);

INSERT INTO `infra_config`
(`category`, `type`, `name`, `config_key`, `value`, `visible`, `remark`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'edu',
       2,
       '当前学期结束日期',
       'edu.current.semester.end-date',
       '',
       b'1',
       '教学平台当前学期结束日期',
       'admin',
       NOW(),
       'admin',
       NOW(),
       b'0' WHERE NOT EXISTS (
  SELECT 1 FROM `infra_config` WHERE `config_key` = 'edu.current.semester.end-date' AND `deleted` = b'0'
);
