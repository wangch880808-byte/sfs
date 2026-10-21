-- V1.0.1.4: redo deadline, workflow indexes, closed self-service entries and scaffold cleanup.
-- Every statement is guarded (information_schema checks, exact original values, NOT EXISTS),
-- so the script can be re-run safely and is a no-op on baseline 15.
-- No business records are inserted or deleted; menus retire only when they still match upstream values.

-- ---------------------------------------------------------------------------
-- C1 structure
-- ---------------------------------------------------------------------------
SET @nus_v15_ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'edu_homework_record'
          AND column_name = 'redo_deadline'
    ),
    'SELECT 1',
    'ALTER TABLE `edu_homework_record` ADD COLUMN `redo_deadline` datetime NULL COMMENT ''重做截止时间'' AFTER `redo_count`'
);
PREPARE nus_v15_stmt FROM @nus_v15_ddl;
EXECUTE nus_v15_stmt;
DEALLOCATE PREPARE nus_v15_stmt;

SET @nus_v15_ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'edu_class_student'
          AND index_name = 'idx_edu_class_student_student'
    ),
    'SELECT 1',
    'ALTER TABLE `edu_class_student` ADD INDEX `idx_edu_class_student_student` (`student_id`)'
);
PREPARE nus_v15_stmt FROM @nus_v15_ddl;
EXECUTE nus_v15_stmt;
DEALLOCATE PREPARE nus_v15_stmt;

SET @nus_v15_ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'edu_homework'
          AND index_name = 'idx_edu_homework_class_status'
    ),
    'SELECT 1',
    'ALTER TABLE `edu_homework` ADD INDEX `idx_edu_homework_class_status` (`class_id`, `status`)'
);
PREPARE nus_v15_stmt FROM @nus_v15_ddl;
EXECUTE nus_v15_stmt;
DEALLOCATE PREPARE nus_v15_stmt;

SET @nus_v15_ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'edu_homework_record'
          AND index_name = 'idx_edu_homework_record_homework_status'
    ),
    'SELECT 1',
    'ALTER TABLE `edu_homework_record` ADD INDEX `idx_edu_homework_record_homework_status` (`homework_id`, `status`)'
);
PREPARE nus_v15_stmt FROM @nus_v15_ddl;
EXECUTE nus_v15_stmt;
DEALLOCATE PREPARE nus_v15_stmt;

-- ---------------------------------------------------------------------------
-- C2 configuration: self-registration off; no shared default password.
-- ---------------------------------------------------------------------------
UPDATE `infra_config`
SET `value` = 'false'
WHERE `config_key` = 'system.user.register-enabled' AND `value` = 'true' AND `deleted` = b'0';

UPDATE `infra_config`
SET `category` = 'biz'
WHERE `config_key` = 'system.user.register-enabled' AND `category` = '用户管理-账号初始密码' AND `deleted` = b'0';

-- Only the upstream default is cleared; a school-defined initial password is kept.
UPDATE `infra_config`
SET `value` = '',
    `remark` = IF(`remark` = '初始化密码 123456', '为空时批量导入非学生账号须填写初始密码', `remark`)
WHERE `config_key` = 'system.user.init-password' AND `value` = '123456' AND `deleted` = b'0';

-- ---------------------------------------------------------------------------
-- C3 menus
--   disable: SMS 1093, mail 2130, OAuth2 application 1263, AI tool 2920 (grants kept for recovery)
--   delete : 114, 116, 1255-1260, 5110, 5112, 5103, 5105 (grants removed)
-- A menu is only touched when id, name, permission, parent_id, path and component still hold the
-- original values. A matched menu that has a non-deleted child outside the known original tree
-- is kept, and so are its matched ancestors (up to 3 levels, as in V14).
-- ---------------------------------------------------------------------------
CREATE TEMPORARY TABLE nus_v15_menu_scope (
    id BIGINT PRIMARY KEY,
    action VARCHAR(8) NOT NULL
);

INSERT INTO nus_v15_menu_scope (id, action)
SELECT id, 'disable' FROM system_menu WHERE deleted = b'0' AND (
     (id = 1093 AND name = '短信管理' AND permission = '' AND parent_id = 2739 AND path = 'sms' AND component <=> NULL)
  OR (id = 2130 AND name = '邮箱管理' AND permission = '' AND parent_id = 2739 AND path = 'mail' AND component <=> NULL)
  OR (id = 1263 AND name = '应用管理' AND permission = '' AND parent_id = 1261 AND path = 'oauth2/application' AND component <=> 'system/oauth2/client/index')
  OR (id = 2920 AND name = '工具管理' AND permission = '' AND parent_id = 2760 AND path = 'tool' AND component <=> 'ai/model/tool/index.vue'));

-- Original sub-pages and buttons below the disabled menus; they stay untouched and do not protect.
INSERT INTO nus_v15_menu_scope (id, action)
SELECT id, 'known' FROM system_menu WHERE deleted = b'0' AND (id, parent_id) IN (
    (1094, 1093), (1095, 1094), (1096, 1094), (1097, 1094), (1098, 1094),
    (1100, 1093), (1101, 1100), (1102, 1100), (1103, 1100), (1104, 1100), (1105, 1100), (1106, 1100),
    (1107, 1093), (1108, 1107), (1109, 1107),
    (2131, 2130), (2132, 2131), (2133, 2131), (2134, 2131), (2135, 2131),
    (2136, 2130), (2137, 2136), (2138, 2136), (2139, 2136), (2140, 2136), (2143, 2136),
    (2141, 2130), (2142, 2141),
    (1264, 1263), (1265, 1263), (1266, 1263), (1267, 1263),
    (2921, 2920), (2922, 2920), (2923, 2920), (2924, 2920));

INSERT INTO nus_v15_menu_scope (id, action)
SELECT id, 'delete' FROM system_menu WHERE deleted = b'0' AND (
     (id = 114 AND name = '表单构建' AND permission = 'infra:build:list' AND parent_id = 2 AND path = 'build' AND component <=> 'infra/build/index')
  OR (id = 116 AND name = 'API 接口' AND permission = 'infra:swagger:list' AND parent_id = 2 AND path = 'swagger' AND component <=> 'infra/swagger/index')
  OR (id = 1255 AND name = '数据源配置' AND permission = '' AND parent_id = 2 AND path = 'data-source-config' AND component <=> 'infra/dataSourceConfig/index')
  OR (id = 1256 AND name = '数据源配置查询' AND permission = 'infra:data-source-config:query' AND parent_id = 1255 AND path = '' AND component <=> '')
  OR (id = 1257 AND name = '数据源配置创建' AND permission = 'infra:data-source-config:create' AND parent_id = 1255 AND path = '' AND component <=> '')
  OR (id = 1258 AND name = '数据源配置更新' AND permission = 'infra:data-source-config:update' AND parent_id = 1255 AND path = '' AND component <=> '')
  OR (id = 1259 AND name = '数据源配置删除' AND permission = 'infra:data-source-config:delete' AND parent_id = 1255 AND path = '' AND component <=> '')
  OR (id = 1260 AND name = '数据源配置导出' AND permission = 'infra:data-source-config:export' AND parent_id = 1255 AND path = '' AND component <=> '')
  OR (id = 5110 AND name = '校园资讯' AND permission = 'edu:news:query' AND parent_id = 5047 AND path = 'news' AND component <=> 'edu/news/index')
  OR (id = 5112 AND name = '资讯管理' AND permission = 'edu:news:manage' AND parent_id = 5110 AND path = '' AND component <=> '')
  OR (id = 5103 AND name = '统计分析' AND permission = 'edu:statistics:query' AND parent_id = 5120 AND path = 'statistics' AND component <=> 'edu/statistics/index')
  OR (id = 5105 AND name = '统计查询' AND permission = 'edu:statistics:query' AND parent_id = 5103 AND path = '' AND component <=> ''));

CREATE TEMPORARY TABLE nus_v15_menu_snapshot LIKE nus_v15_menu_scope;
DELETE FROM nus_v15_menu_snapshot;
INSERT INTO nus_v15_menu_snapshot SELECT id, action FROM nus_v15_menu_scope;
DELETE candidate FROM nus_v15_menu_scope candidate
JOIN system_menu child ON child.parent_id = candidate.id AND child.deleted = b'0'
LEFT JOIN nus_v15_menu_snapshot known_child ON known_child.id = child.id
WHERE known_child.id IS NULL;
DELETE FROM nus_v15_menu_snapshot;
INSERT INTO nus_v15_menu_snapshot SELECT id, action FROM nus_v15_menu_scope;
DELETE candidate FROM nus_v15_menu_scope candidate
JOIN system_menu child ON child.parent_id = candidate.id AND child.deleted = b'0'
LEFT JOIN nus_v15_menu_snapshot known_child ON known_child.id = child.id
WHERE known_child.id IS NULL;
DELETE FROM nus_v15_menu_snapshot;
INSERT INTO nus_v15_menu_snapshot SELECT id, action FROM nus_v15_menu_scope;
DELETE candidate FROM nus_v15_menu_scope candidate
JOIN system_menu child ON child.parent_id = candidate.id AND child.deleted = b'0'
LEFT JOIN nus_v15_menu_snapshot known_child ON known_child.id = child.id
WHERE known_child.id IS NULL;

UPDATE system_menu menu
JOIN nus_v15_menu_scope candidate ON candidate.id = menu.id AND candidate.action = 'disable'
SET menu.status = 1;

DELETE relation FROM system_role_menu relation
JOIN nus_v15_menu_scope candidate ON candidate.id = relation.menu_id AND candidate.action = 'delete';
UPDATE system_menu menu
JOIN nus_v15_menu_scope candidate ON candidate.id = menu.id AND candidate.action = 'delete'
SET menu.deleted = b'1', menu.status = 1;

DROP TEMPORARY TABLE nus_v15_menu_snapshot;
DROP TEMPORARY TABLE nus_v15_menu_scope;

-- ---------------------------------------------------------------------------
-- C4 dictionaries
-- ---------------------------------------------------------------------------
INSERT INTO `system_dict_type`
(`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`)
SELECT '菜单类型', 'system_menu_type', 0, NULL, 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0', NULL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'system_menu_type' AND `deleted` = b'0');

INSERT INTO `system_dict_type`
(`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`)
SELECT '数据范围', 'system_data_scope', 0, NULL, 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0', NULL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'system_data_scope' AND `deleted` = b'0');

INSERT INTO `system_dict_type`
(`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`)
SELECT '用户类型', 'user_type', 0, NULL, 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0', NULL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'user_type' AND `deleted` = b'0');

INSERT INTO `system_dict_data`
(`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1, '目录', '1', 'system_menu_type', 0, '', '', '目录', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'system_menu_type' AND `value` = '1' AND `deleted` = b'0');

INSERT INTO `system_dict_data`
(`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 2, '菜单', '2', 'system_menu_type', 0, '', '', '菜单', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'system_menu_type' AND `value` = '2' AND `deleted` = b'0');

INSERT INTO `system_dict_data`
(`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 3, '按钮', '3', 'system_menu_type', 0, '', '', '按钮', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'system_menu_type' AND `value` = '3' AND `deleted` = b'0');

INSERT INTO `system_dict_data`
(`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1, '全部数据权限', '1', 'system_data_scope', 0, '', '', '全部数据权限', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'system_data_scope' AND `value` = '1' AND `deleted` = b'0');

INSERT INTO `system_dict_data`
(`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 2, '指定部门数据权限', '2', 'system_data_scope', 0, '', '', '指定部门数据权限', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'system_data_scope' AND `value` = '2' AND `deleted` = b'0');

INSERT INTO `system_dict_data`
(`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 3, '本部门数据权限', '3', 'system_data_scope', 0, '', '', '本部门数据权限', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'system_data_scope' AND `value` = '3' AND `deleted` = b'0');

INSERT INTO `system_dict_data`
(`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 4, '本部门及以下数据权限', '4', 'system_data_scope', 0, '', '', '本部门及以下数据权限', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'system_data_scope' AND `value` = '4' AND `deleted` = b'0');

INSERT INTO `system_dict_data`
(`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 5, '仅本人数据权限', '5', 'system_data_scope', 0, '', '', '仅本人数据权限', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'system_data_scope' AND `value` = '5' AND `deleted` = b'0');

-- Platform users only; member (value 1) accounts do not exist in this deployment.
INSERT INTO `system_dict_data`
(`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 2, '管理员', '2', 'user_type', 0, 'success', '', NULL, 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'user_type' AND `value` = '2' AND `deleted` = b'0');

-- Hide AI platforms that AiPlatformEnum rejects, unless an API key or model still uses them.
UPDATE `system_dict_data` platform
SET platform.`status` = 1
WHERE platform.`dict_type` = 'ai_platform' AND platform.`deleted` = b'0' AND platform.`status` = 0
  AND platform.`value` IN ('AzureOpenAI', 'Gemini', 'YiYan', 'XingHuo', 'TongYi', 'StableDiffusion',
                           'Midjourney', 'Suno', 'DouBao', 'HunYuan', 'SiliconFlow', 'BaiChuan')
  AND NOT EXISTS (SELECT 1 FROM `ai_api_key` api_key WHERE api_key.`platform` = platform.`value` AND api_key.`deleted` = b'0')
  AND NOT EXISTS (SELECT 1 FROM `ai_model` model WHERE model.`platform` = platform.`value` AND model.`deleted` = b'0');

-- WeCom / WeChat MP / WeChat Open Platform, unless a social client is configured for them.
UPDATE `system_dict_data` social_type
SET social_type.`status` = 1
WHERE social_type.`dict_type` = 'system_social_type' AND social_type.`deleted` = b'0' AND social_type.`status` = 0
  AND social_type.`value` IN ('30', '31', '32')
  AND NOT EXISTS (
      -- social_type is numeric: compare as numbers (a CHAR cast would mix collations).
      SELECT 1 FROM `system_social_client` client
      WHERE client.`social_type` = social_type.`value` + 0 AND client.`deleted` = b'0'
  );

-- justauth is disabled in production, so the DingTalk entry cannot work.
UPDATE `system_dict_data`
SET `value` = 'false'
WHERE `dict_type` = 'system_login_feature' AND `label` = 'dingtalk_login' AND `value` = 'true' AND `deleted` = b'0';

-- ---------------------------------------------------------------------------
-- C5 log retention jobs; handler_param is the retention in days.
-- ---------------------------------------------------------------------------
INSERT INTO `infra_job`
(`name`, `status`, `handler_name`, `handler_param`, `cron_expression`, `retry_count`, `retry_interval`,
 `monitor_timeout`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'API访问日志清理', 1, 'accessLogCleanJob', '180', '0 20 3 * * ?', 1, 60000,
       300000, 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `infra_job`
    WHERE `handler_name` = 'accessLogCleanJob' AND `deleted` = b'0'
);

INSERT INTO `infra_job`
(`name`, `status`, `handler_name`, `handler_param`, `cron_expression`, `retry_count`, `retry_interval`,
 `monitor_timeout`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'API错误日志清理', 1, 'errorLogCleanJob', '180', '0 25 3 * * ?', 1, 60000,
       300000, 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `infra_job`
    WHERE `handler_name` = 'errorLogCleanJob' AND `deleted` = b'0'
);

INSERT INTO `infra_job`
(`name`, `status`, `handler_name`, `handler_param`, `cron_expression`, `retry_count`, `retry_interval`,
 `monitor_timeout`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '定时任务日志清理', 1, 'jobLogCleanJob', '30', '0 30 3 * * ?', 1, 60000,
       300000, 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `infra_job`
    WHERE `handler_name` = 'jobLogCleanJob' AND `deleted` = b'0'
);

-- ---------------------------------------------------------------------------
-- C6 demo AI tools whose functions were removed from the backend.
-- ---------------------------------------------------------------------------
UPDATE `ai_tool`
SET `status` = 1
WHERE `name` IN ('directory_list', 'weather_query', 'user_profile_query') AND `status` <> 1 AND `deleted` = b'0';

-- ---------------------------------------------------------------------------
-- Roles. Permission checks are enforced again (backend 4b11f35); keep the data range that was in
-- effect before (data permission was never applied, i.e. all data) and grant school_admin the query
-- permissions its pages call. Only exact original values are changed; 160/161/200 get no grants.
-- ---------------------------------------------------------------------------
UPDATE `system_role`
SET `data_scope` = 1
WHERE `id` = 161 AND `code` = 'teacher' AND `data_scope` = 5 AND `deleted` = b'0';

UPDATE `system_role`
SET `data_scope` = 1
WHERE `id` = 159 AND `code` = 'school_admin' AND `data_scope` = 4 AND `deleted` = b'0';

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT role.`id`, menu.`id`, 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM `system_role` role
JOIN `system_menu` menu ON menu.`deleted` = b'0' AND (
     (menu.`id` = 1001 AND menu.`permission` = 'system:user:query')
  OR (menu.`id` = 5054 AND menu.`permission` = 'edu:class:query')
  OR (menu.`id` = 5117 AND menu.`permission` = 'edu:statistics:query'))
WHERE role.`id` = 159 AND role.`code` = 'school_admin' AND role.`deleted` = b'0'
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` granted
      WHERE granted.`role_id` = role.`id` AND granted.`menu_id` = menu.`id` AND granted.`deleted` = b'0'
  )
ORDER BY menu.`id`;
