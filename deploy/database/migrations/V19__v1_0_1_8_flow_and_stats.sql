-- V1.0.1.8：教师班课管理、统计与流程文案。
-- 必须先合并后端课程归属校验，再开放教师绑定权限；迁移后清权限与通知模板缓存并重新登录。

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT role.`id`, menu.`id`, 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM `system_role` role
JOIN `system_menu` menu ON menu.`id` = 5064 AND menu.`permission` = 'edu:class-course:bind'
    AND menu.`deleted` = b'0' AND menu.`status` = 0
WHERE role.`id` = 161 AND role.`code` = 'teacher' AND role.`deleted` = b'0' AND role.`status` = 0
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` granted
                  WHERE granted.`role_id` = role.`id` AND granted.`menu_id` = menu.`id` AND granted.`deleted` = b'0');

-- 学院教务管理员（B3 / S6）
-- S6 / V1.0.1.8：学院教务管理员只维护本学院及下级专业的班级和师生。
-- 合并进 V19 后执行；先合并后端范围校验，再启用授权。
UPDATE `system_role`
SET `data_scope` = 4, `updater` = 'system', `update_time` = CURRENT_TIMESTAMP
WHERE `id` = 160 AND `code` = 'college_admin' AND `deleted` = b'0'
  AND (`data_scope` IS NULL OR `data_scope` <> 4);

-- 菜单编号/权限以 ry261-test.sql 为准；组织页面和班级/师生页面参照角色 159。
-- 师生按钮复用 system:user:*；教务的停用/恢复接口（/edu/user/delete、/edu/user/restore）要求 system:user:delete，
-- 后端已对删除、停用、恢复逐个校验本学院范围。班级恢复使用 delete。
-- 不授予组织增删改、课程、任务、统计或公共课审批（5142）。
INSERT INTO `system_role_menu`
(`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT role.`id`, menu.`id`, 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM `system_role` role
JOIN `system_menu` menu ON menu.`deleted` = b'0' AND menu.`status` = 0
  AND ((menu.`id` = 5047 AND menu.`name` = '教务组织' AND menu.`permission` = '')
    OR (menu.`id` = 103 AND menu.`permission` = 'system:dept:list')
    OR (menu.`id` = 1017 AND menu.`permission` = 'system:dept:query')
    OR (menu.`id` = 5049 AND menu.`permission` = 'edu:class:list')
    OR (menu.`id` = 5051 AND menu.`permission` = 'edu:class:create')
    OR (menu.`id` = 5052 AND menu.`permission` = 'edu:class:update')
    OR (menu.`id` = 5053 AND menu.`permission` = 'edu:class:delete')
    OR (menu.`id` = 5054 AND menu.`permission` = 'edu:class:query')
    OR (menu.`id` = 5048 AND menu.`permission` = 'edu:teacher:list')
    OR (menu.`id` = 5050 AND menu.`permission` = 'edu:student:list')
    OR (menu.`id` = 1001 AND menu.`permission` = 'system:user:query')
    OR (menu.`id` = 1002 AND menu.`permission` = 'system:user:create')
    OR (menu.`id` = 1003 AND menu.`permission` = 'system:user:update')
    OR (menu.`id` = 1004 AND menu.`permission` = 'system:user:delete')
    OR (menu.`id` = 1005 AND menu.`permission` = 'system:user:export')
    OR (menu.`id` = 1006 AND menu.`permission` = 'system:user:import')
    OR (menu.`id` = 1007 AND menu.`permission` = 'system:user:update-password')
    OR (menu.`id` = 5126 AND menu.`permission` = 'edu:data-exchange:import'))
WHERE role.`id` = 160 AND role.`code` = 'college_admin' AND role.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` granted
      WHERE granted.`role_id` = role.`id` AND granted.`menu_id` = menu.`id`
        AND granted.`deleted` = b'0');

-- 只删除 college_admin 这个完整 token，保留其他角色值及顺序。
-- EduRoleApi 同时支持英文/中文逗号、分号和空白；前瞻避免吞掉相邻 token 的分隔符。
UPDATE `infra_config`
SET `value` = TRIM(BOTH ',' FROM TRIM(REGEXP_REPLACE(`value`,
      '(^|[,，;；[:space:]])college_admin(?=[,，;；[:space:]]|$)', '', 1, 0, 'c'))),
    `updater` = 'system', `update_time` = CURRENT_TIMESTAMP
WHERE `config_key` = 'edu.role.admin-codes' AND `deleted` = b'0'
  AND REGEXP_LIKE(`value`, '(^|[,，;；[:space:]])college_admin(?=[,，;；[:space:]]|$)', 'c');

-- 部署后清除角色/菜单/用户角色缓存，并重新登录；EduRoleApi 配置缓存最多 30 秒失效。

UPDATE `system_notify_template`
SET `content` = '「{homeworkName}」有 {count} 份新提交{resubmitNote}待批改。',
    `params` = '["homeworkName","count","resubmitNote"]'
WHERE `code` = 'edu.teacher.homework.submitted' AND `deleted` = b'0'
  AND (NOT (`content` <=> '「{homeworkName}」有 {count} 份新提交{resubmitNote}待批改。')
       OR NOT (`params` <=> '["homeworkName","count","resubmitNote"]'));

UPDATE `system_dict_data`
SET `label` = '管理员'
WHERE `id` = 3036 AND `dict_type` = 'system_user_type' AND `value` = '0' AND `deleted` = b'0'
  AND NOT (`label` <=> '管理员');

UPDATE `system_menu`
SET `status` = 1
WHERE `id` = 107 AND `name` = '通知公告' AND `permission` = '' AND `type` = 2
  AND `component` = 'system/notice/index' AND `deleted` = b'0' AND `status` <> 1;

-- 先清理关联；菜单已被软删除的部分执行状态也可以修复。
UPDATE `system_role_menu` granted
JOIN `system_role` role ON role.`id` = granted.`role_id` AND role.`id` = 1
    AND role.`code` = 'super_admin' AND role.`deleted` = b'0'
JOIN `system_menu` menu ON menu.`id` = granted.`menu_id` AND menu.`id` = 5065
    AND menu.`permission` = 'edu:class-course:unbind'
SET granted.`deleted` = b'1'
WHERE granted.`deleted` = b'0';

UPDATE `system_menu`
SET `deleted` = b'1'
WHERE `id` = 5065 AND `permission` = 'edu:class-course:unbind' AND `deleted` = b'0';

-- 本版不删除 assistant_teacher_ids 助教 CSV 旧列：保证可以回滚到 V1.0.1.7。
