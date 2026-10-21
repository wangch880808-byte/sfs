-- =============================================
-- Sprint 13 单项能力评测菜单迁移到学生端
-- 目标：
-- 1. 将 5116「单项能力评测」从教师分析中心移动到学生学习目录
-- 2. 权限改为学生能力分析权限，避免学生端被 edu:statistics:query 拦截
-- 3. 给学生角色补齐菜单授权
-- =============================================

-- ========== 1. 移动菜单到学生学习目录 ==========
UPDATE `system_menu`
SET `parent_id`   = 5086,
    `sort`        = 6,
    `permission`  = 'edu:student-capacity:query',
    `update_time` = NOW()
WHERE `id` = 5116
  AND `deleted` = 0;

-- ========== 2. 给学生角色补齐菜单授权 ==========
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 200,
       5116,
       'admin',
       NOW(),
       'admin',
       NOW(),
       b'0' WHERE NOT EXISTS (
    SELECT 1
    FROM `system_role_menu`
    WHERE `role_id` = 200
      AND `menu_id` = 5116
      AND `deleted` = 0
);

-- ========== 3. 可选：给超级管理员保留查看权限 ==========
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1,
       5116,
       'admin',
       NOW(),
       'admin',
       NOW(),
       b'0' WHERE NOT EXISTS (
    SELECT 1
    FROM `system_role_menu`
    WHERE `role_id` = 1
      AND `menu_id` = 5116
      AND `deleted` = 0
);
