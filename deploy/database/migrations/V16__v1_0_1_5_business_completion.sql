-- V1.0.1.5 / Flyway V16：业务能力补全。
-- 包含学生强制改密、评分轨迹、归档状态、留言、公共课同步、统计配置和 AI 任务字段；
-- 同时清理已下线的脚手架菜单、登录字典及半成品入口；AI 对话接口改为按“AI 对话”菜单授权。
-- 运维须知：升级后全体学生首次登录需修改初始密码；本迁移仅在首次加列时标记存量学生。
-- 学校自定义菜单和已归档公共课关联按原状态保留。
-- 各段按依赖顺序执行，可重复运行；首次加列的历史回填不会在重跑时覆盖后续业务修改。

-- ===== 学生强制改密 =====
-- 为学生账号记录首次 Web 密码登录的改密要求，教师和管理员账号不受影响。
-- 仅首次加列时回填；重跑不能重新标记已经自行改密的学生。
SET @nus_a1_needs_backfill = NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'system_users'
          AND column_name = 'password_change_required'
);
SET @nus_a1_ddl = IF(@nus_a1_needs_backfill,
    'ALTER TABLE `system_users` ADD COLUMN `password_change_required` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''首次登录必须修改密码''',
    'SELECT 1');
PREPARE nus_a1_stmt FROM @nus_a1_ddl;
EXECUTE nus_a1_stmt;
DEALLOCATE PREPARE nus_a1_stmt;

UPDATE `system_users`
SET `password_change_required` = b'1'
WHERE @nus_a1_needs_backfill AND `user_type` = 2 AND `deleted` = b'0'
  AND `password_change_required` = b'0';

-- ===== 评分轨迹表 =====
-- 新增任务提交和评分事件历史表；已有任务记录不迁移。
SET @nus_a2_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = DATABASE() AND table_name = 'edu_homework_record_history'),
    'SELECT 1',
    'CREATE TABLE `edu_homework_record_history` (
        `id` bigint NOT NULL AUTO_INCREMENT,
        `record_id` bigint NOT NULL,
        `homework_id` bigint NOT NULL,
        `student_id` bigint NOT NULL,
        `event_type` varchar(32) NOT NULL,
        `from_status` tinyint DEFAULT NULL,
        `to_status` tinyint NOT NULL,
        `score` int DEFAULT NULL,
        `comment` text COLLATE utf8mb4_unicode_ci,
        `redo_deadline` datetime DEFAULT NULL,
        `ai_review_id` bigint DEFAULT NULL,
        `operator_id` bigint DEFAULT NULL,
        `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '''',
        `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
        `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '''',
        `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        `deleted` bit(1) NOT NULL DEFAULT b''0'',
        PRIMARY KEY (`id`),
        KEY `idx_record_create_time` (`record_id`, `create_time`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''任务提交与评分轨迹'''
);
PREPARE nus_a2_stmt FROM @nus_a2_ddl;
EXECUTE nus_a2_stmt;
DEALLOCATE PREPARE nus_a2_stmt;

SET @nus_a2_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'edu_homework_record_history'
              AND index_name = 'idx_record_create_time'),
    'SELECT 1',
    'ALTER TABLE `edu_homework_record_history` ADD INDEX `idx_record_create_time` (`record_id`, `create_time`)'
);
PREPARE nus_a2_stmt FROM @nus_a2_ddl;
EXECUTE nus_a2_stmt;
DEALLOCATE PREPARE nus_a2_stmt;

-- ===== 班课和任务归档状态 =====
-- 班课关联记录归档状态，任务记录归档前状态；已有业务数据保持原状。
SET @nus_a3_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
            AND table_name = 'edu_class_course' AND column_name = 'status'),
    'SELECT 1',
    'ALTER TABLE `edu_class_course` ADD COLUMN `status` tinyint NOT NULL DEFAULT 0 COMMENT ''0正常 1已归档'''
);
PREPARE nus_a3_stmt FROM @nus_a3_ddl;
EXECUTE nus_a3_stmt;
DEALLOCATE PREPARE nus_a3_stmt;

SET @nus_a3_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
            AND table_name = 'edu_homework' AND column_name = 'status_before_archive'),
    'SELECT 1',
    'ALTER TABLE `edu_homework` ADD COLUMN `status_before_archive` tinyint NULL COMMENT ''归档前任务状态'' AFTER `status`'
);
PREPARE nus_a3_stmt FROM @nus_a3_ddl;
EXECUTE nus_a3_stmt;
DEALLOCATE PREPARE nus_a3_stmt;

-- ===== 脚手架菜单与字典 =====
-- 仅清理与原始值完全匹配的短信、邮件、地区菜单及其授权；自定义子菜单保留其祖先。
DROP TEMPORARY TABLE IF EXISTS nus_v16_c1_menu_scope;
CREATE TEMPORARY TABLE nus_v16_c1_menu_scope (id BIGINT PRIMARY KEY);
INSERT INTO nus_v16_c1_menu_scope (id)
SELECT id FROM system_menu WHERE deleted = b'0' AND (
    (id = 1093 AND name = '短信管理' AND permission = '' AND type = 1 AND parent_id = 2739 AND path = 'sms' AND component <=> NULL)
  OR (id = 1094 AND name = '短信渠道' AND permission = '' AND type = 2 AND parent_id = 1093 AND path = 'sms-channel' AND component <=> 'system/sms/channel/index')
  OR (id = 1095 AND name = '短信渠道查询' AND permission = 'system:sms-channel:query' AND type = 3 AND parent_id = 1094 AND path = '' AND component <=> '')
  OR (id = 1096 AND name = '短信渠道创建' AND permission = 'system:sms-channel:create' AND type = 3 AND parent_id = 1094 AND path = '' AND component <=> '')
  OR (id = 1097 AND name = '短信渠道更新' AND permission = 'system:sms-channel:update' AND type = 3 AND parent_id = 1094 AND path = '' AND component <=> '')
  OR (id = 1098 AND name = '短信渠道删除' AND permission = 'system:sms-channel:delete' AND type = 3 AND parent_id = 1094 AND path = '' AND component <=> '')
  OR (id = 1100 AND name = '短信模板' AND permission = '' AND type = 2 AND parent_id = 1093 AND path = 'sms-template' AND component <=> 'system/sms/template/index')
  OR (id = 1101 AND name = '短信模板查询' AND permission = 'system:sms-template:query' AND type = 3 AND parent_id = 1100 AND path = '' AND component <=> '')
  OR (id = 1102 AND name = '短信模板创建' AND permission = 'system:sms-template:create' AND type = 3 AND parent_id = 1100 AND path = '' AND component <=> '')
  OR (id = 1103 AND name = '短信模板更新' AND permission = 'system:sms-template:update' AND type = 3 AND parent_id = 1100 AND path = '' AND component <=> '')
  OR (id = 1104 AND name = '短信模板删除' AND permission = 'system:sms-template:delete' AND type = 3 AND parent_id = 1100 AND path = '' AND component <=> '')
  OR (id = 1105 AND name = '短信模板导出' AND permission = 'system:sms-template:export' AND type = 3 AND parent_id = 1100 AND path = '' AND component <=> '')
  OR (id = 1106 AND name = '发送测试短信' AND permission = 'system:sms-template:send-sms' AND type = 3 AND parent_id = 1100 AND path = '' AND component <=> '')
  OR (id = 1107 AND name = '短信日志' AND permission = '' AND type = 2 AND parent_id = 1093 AND path = 'sms-log' AND component <=> 'system/sms/log/index')
  OR (id = 1108 AND name = '短信日志查询' AND permission = 'system:sms-log:query' AND type = 3 AND parent_id = 1107 AND path = '' AND component <=> '')
  OR (id = 1109 AND name = '短信日志导出' AND permission = 'system:sms-log:export' AND type = 3 AND parent_id = 1107 AND path = '' AND component <=> '')
  OR (id = 2083 AND name = '地区管理' AND permission = '' AND type = 2 AND parent_id = 1 AND path = 'area' AND component <=> 'system/area/index')
  OR (id = 2130 AND name = '邮箱管理' AND permission = '' AND type = 2 AND parent_id = 2739 AND path = 'mail' AND component <=> NULL)
  OR (id = 2131 AND name = '邮箱账号' AND permission = '' AND type = 2 AND parent_id = 2130 AND path = 'mail-account' AND component <=> 'system/mail/account/index')
  OR (id = 2132 AND name = '账号查询' AND permission = 'system:mail-account:query' AND type = 3 AND parent_id = 2131 AND path = '' AND component <=> '')
  OR (id = 2133 AND name = '账号创建' AND permission = 'system:mail-account:create' AND type = 3 AND parent_id = 2131 AND path = '' AND component <=> '')
  OR (id = 2134 AND name = '账号更新' AND permission = 'system:mail-account:update' AND type = 3 AND parent_id = 2131 AND path = '' AND component <=> '')
  OR (id = 2135 AND name = '账号删除' AND permission = 'system:mail-account:delete' AND type = 3 AND parent_id = 2131 AND path = '' AND component <=> '')
  OR (id = 2136 AND name = '邮件模版' AND permission = '' AND type = 2 AND parent_id = 2130 AND path = 'mail-template' AND component <=> 'system/mail/template/index')
  OR (id = 2137 AND name = '模版查询' AND permission = 'system:mail-template:query' AND type = 3 AND parent_id = 2136 AND path = '' AND component <=> '')
  OR (id = 2138 AND name = '模版创建' AND permission = 'system:mail-template:create' AND type = 3 AND parent_id = 2136 AND path = '' AND component <=> '')
  OR (id = 2139 AND name = '模版更新' AND permission = 'system:mail-template:update' AND type = 3 AND parent_id = 2136 AND path = '' AND component <=> '')
  OR (id = 2140 AND name = '模版删除' AND permission = 'system:mail-template:delete' AND type = 3 AND parent_id = 2136 AND path = '' AND component <=> '')
  OR (id = 2141 AND name = '邮件记录' AND permission = '' AND type = 2 AND parent_id = 2130 AND path = 'mail-log' AND component <=> 'system/mail/log/index')
  OR (id = 2142 AND name = '日志查询' AND permission = 'system:mail-log:query' AND type = 3 AND parent_id = 2141 AND path = '' AND component <=> '')
  OR (id = 2143 AND name = '发送测试邮件' AND permission = 'system:mail-template:send-mail' AND type = 3 AND parent_id = 2136 AND path = '' AND component <=> '')
);

DROP TEMPORARY TABLE IF EXISTS nus_v16_c1_menu_snapshot;
CREATE TEMPORARY TABLE nus_v16_c1_menu_snapshot LIKE nus_v16_c1_menu_scope;
DELETE FROM nus_v16_c1_menu_snapshot;
INSERT INTO nus_v16_c1_menu_snapshot SELECT id FROM nus_v16_c1_menu_scope;
DELETE candidate FROM nus_v16_c1_menu_scope candidate
JOIN system_menu child ON child.parent_id = candidate.id AND child.deleted = b'0'
LEFT JOIN nus_v16_c1_menu_snapshot known_child ON known_child.id = child.id
WHERE known_child.id IS NULL;
DELETE FROM nus_v16_c1_menu_snapshot;
INSERT INTO nus_v16_c1_menu_snapshot SELECT id FROM nus_v16_c1_menu_scope;
DELETE candidate FROM nus_v16_c1_menu_scope candidate
JOIN system_menu child ON child.parent_id = candidate.id AND child.deleted = b'0'
LEFT JOIN nus_v16_c1_menu_snapshot known_child ON known_child.id = child.id
WHERE known_child.id IS NULL;
DELETE FROM nus_v16_c1_menu_snapshot;
INSERT INTO nus_v16_c1_menu_snapshot SELECT id FROM nus_v16_c1_menu_scope;
DELETE candidate FROM nus_v16_c1_menu_scope candidate
JOIN system_menu child ON child.parent_id = candidate.id AND child.deleted = b'0'
LEFT JOIN nus_v16_c1_menu_snapshot known_child ON known_child.id = child.id
WHERE known_child.id IS NULL;

DELETE relation FROM system_role_menu relation
JOIN nus_v16_c1_menu_scope candidate ON candidate.id = relation.menu_id;
UPDATE system_menu menu
JOIN nus_v16_c1_menu_scope candidate ON candidate.id = menu.id
SET menu.deleted = b'1', menu.status = 1;
DROP TEMPORARY TABLE nus_v16_c1_menu_snapshot;
DROP TEMPORARY TABLE nus_v16_c1_menu_scope;

-- 数据源菜单 1255–1260 已由 V15 下线；岗位菜单 104 保持停用。
UPDATE system_dict_type
SET status = 1
WHERE deleted = b'0' AND status = 0 AND (
    (type = 'system_sms_channel_code' AND name = '短信渠道编码')
  OR     (type = 'system_sms_template_type' AND name = '短信模板的类型')
  OR     (type = 'system_sms_send_status' AND name = '短信发送状态')
  OR     (type = 'system_sms_receive_status' AND name = '短信接收状态')
  OR     (type = 'system_mail_send_status' AND name = '邮件发送状态')
  OR     (type = 'system_oauth2_grant_type' AND name = 'OAuth 2.0 授权类型')
  OR     (type = 'date_interval' AND name = '时间间隔')
  OR     (type = 'terminal' AND name = '终端')
);

-- ===== 留言已读与菜单 =====
-- 会话开关仍使用 edu_message.status；新增学生留言入口并授予学生角色，教师入口重新启用。
-- 只在首次增加 read_status 时回填历史数据，重复执行不再改动。
SET @nus_a6_read_backfill = NOT EXISTS (SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'edu_message' AND column_name = 'read_status');

SET @nus_a6_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'edu_message' AND column_name = 'read_status'),
    'SELECT 1',
    'ALTER TABLE `edu_message` ADD COLUMN `read_status` tinyint NOT NULL DEFAULT 0 COMMENT ''接收方已读：0否1是'' AFTER `status`');
PREPARE nus_a6_stmt FROM @nus_a6_ddl;
EXECUTE nus_a6_stmt;
DEALLOCATE PREPARE nus_a6_stmt;

SET @nus_a6_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'edu_message' AND column_name = 'read_time'),
    'SELECT 1',
    'ALTER TABLE `edu_message` ADD COLUMN `read_time` datetime NULL COMMENT ''接收方阅读时间'' AFTER `read_status`');
PREPARE nus_a6_stmt FROM @nus_a6_ddl;
EXECUTE nus_a6_stmt;
DEALLOCATE PREPARE nus_a6_stmt;

SET @nus_a6_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'edu_message' AND index_name = 'idx_message_conversation'),
    'SELECT 1',
    'ALTER TABLE `edu_message` ADD INDEX `idx_message_conversation` (`class_id`, `course_id`, `student_id`, `id`)');
PREPARE nus_a6_stmt FROM @nus_a6_ddl;
EXECUTE nus_a6_stmt;
DEALLOCATE PREPARE nus_a6_stmt;

-- 升级前没有已读记录：教师消息，以及之后已有教师回复的学生消息视为已读；
-- 仍在等待回复的学生消息保持未读，避免升级后教师未读数激增或漏掉待回复留言。
UPDATE `edu_message` m
LEFT JOIN (SELECT `class_id`, `course_id`, `student_id`, MAX(`id`) AS `last_reply_id`
           FROM `edu_message` WHERE `type` = 1 AND `deleted` = b'0'
           GROUP BY `class_id`, `course_id`, `student_id`) reply
    ON reply.`class_id` = m.`class_id` AND reply.`course_id` = m.`course_id`
   AND reply.`student_id` = m.`student_id`
SET m.`read_status` = 1
WHERE @nus_a6_read_backfill = 1 AND m.`deleted` = b'0' AND m.`read_status` = 0
  AND (m.`type` = 1 OR reply.`last_reply_id` > m.`id`);

UPDATE `system_menu` SET `status` = 0
WHERE `status` = 1 AND `deleted` = b'0'
  AND ((`id` = 5107 AND `permission` = 'edu:message:query')
    OR (`id` = 5109 AND `permission` = 'edu:message:manage'));

-- 新菜单不写死 ID（学校库可能已自行新增菜单占用了 ID），一律按权限标识定位，ID 由自增分配。
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '我的留言', 'edu:student-message:query', 2, 5, parent.`id`, 'message',
       'ep:chat-line-round', 'edu/student/message/index', 'StudentMessage',
       0, b'1', b'1', b'1', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM `system_menu` parent
WHERE parent.`id` = 5086 AND parent.`type` = 1 AND parent.`path` = '/student' AND parent.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
                  WHERE `permission` = 'edu:student-message:query' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '发送留言', 'edu:student-message:send', 3, 1, page.`id`, '', '', '', NULL,
       0, b'1', b'1', b'1', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM (SELECT MIN(`id`) AS `id` FROM `system_menu`
      WHERE `permission` = 'edu:student-message:query' AND `type` = 2 AND `deleted` = b'0') page
WHERE page.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_menu`
                  WHERE `permission` = 'edu:student-message:send' AND `deleted` = b'0');

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT role.`id`, menu.`id`, 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM `system_role` role
JOIN `system_menu` menu ON menu.`deleted` = b'0' AND menu.`status` = 0
    AND ((role.`id` = 161 AND
             ((menu.`id` = 5047 AND menu.`name` = '教务组织' AND menu.`permission` = '')
               OR (menu.`id` = 5107 AND menu.`permission` = 'edu:message:query')
               OR (menu.`id` = 5109 AND menu.`permission` = 'edu:message:manage')))
      OR (role.`id` = 200 AND menu.`permission` IN ('edu:student-message:query', 'edu:student-message:send')))
WHERE role.`deleted` = b'0'
  AND ((role.`id` = 161 AND role.`code` = 'teacher')
    OR (role.`id` = 200 AND role.`code` = 'student'))
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` granted
                  WHERE granted.`role_id` = role.`id`
                    AND granted.`menu_id` = menu.`id` AND granted.`deleted` = b'0');

-- ===== 公共课关联与每日任务 =====
-- 只为已设置的当前学期补齐在线公共课与正常班级；人工归档的关联仍视为已存在。
INSERT INTO `edu_class_course`
(`class_id`, `course_id`, `semester_name`, `semester_start_date`, `semester_end_date`,
 `assistant_teacher_ids`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT cl.`id`, co.`id`, TRIM(sem.`value`), STR_TO_DATE(st.`value`, '%Y-%m-%d'),
       STR_TO_DATE(en.`value`, '%Y-%m-%d'), NULL, 0, 'system', CURRENT_TIMESTAMP,
       'system', CURRENT_TIMESTAMP, b'0'
FROM `edu_class` cl
JOIN `capacity_course` co ON co.`is_public_course` = 0
    AND co.`public_audit_status` = 2 AND co.`status` = 0 AND co.`deleted` = b'0'
JOIN `infra_config` sem ON sem.`config_key` = 'edu.current.semester.name'
    AND sem.`deleted` = b'0' AND TRIM(sem.`value`) <> '' AND TRIM(sem.`value`) <> '未设置'
LEFT JOIN `infra_config` st ON st.`config_key` = 'edu.current.semester.start-date' AND st.`deleted` = b'0'
LEFT JOIN `infra_config` en ON en.`config_key` = 'edu.current.semester.end-date' AND en.`deleted` = b'0'
WHERE cl.`status` = 0 AND cl.`deleted` = b'0'
  AND NOT EXISTS (
      SELECT 1 FROM `edu_class_course` cc
      WHERE cc.`class_id` = cl.`id` AND cc.`course_id` = co.`id`
        AND TRIM(cc.`semester_name`) = TRIM(sem.`value`) AND cc.`deleted` = b'0'
  );

-- 每日兜底；学期切换和课程、班级变更时也会立即同步。
INSERT INTO `infra_job`
(`name`, `status`, `handler_name`, `handler_param`, `cron_expression`, `retry_count`, `retry_interval`,
 `monitor_timeout`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '公共课班级同步', 1, 'eduPublicCourseSyncJob', '', '0 15 0 * * ?', 1, 60000,
       60000, 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `infra_job` WHERE `handler_name` = 'eduPublicCourseSyncJob' AND `deleted` = b'0'
);

-- ===== 登录入口字典下线 =====
-- 登录入口展示以社交客户端配置为准，旧登录功能开关字典逻辑下线。
UPDATE `system_dict_data`
SET `deleted` = b'1'
WHERE `dict_type` = 'system_login_feature' AND `deleted` = b'0';

UPDATE `system_dict_type`
SET `deleted` = b'1', `deleted_time` = NOW()
WHERE `type` = 'system_login_feature' AND `deleted` = b'0';

-- ===== 半成品菜单下线 =====
-- 下线未完成的班课关联、AI 对话分析和学生统计入口；保留 AI 评阅按钮与授权。
UPDATE `system_menu` button_menu
JOIN `system_menu` parent_menu ON parent_menu.`id` = 5062 AND parent_menu.`deleted` = b'0'
    AND parent_menu.`name` = '实训课程管理' AND parent_menu.`permission` = 'capacity:curse:list'
    AND parent_menu.`component` = 'capacity/course/index'
SET button_menu.`parent_id` = 5062
WHERE button_menu.`deleted` = b'0' AND button_menu.`type` = 3 AND (
    (button_menu.`id` = 5064 AND button_menu.`name` = '绑定课程' AND button_menu.`permission` = 'edu:class-course:bind' AND button_menu.`parent_id` = 5063
        AND button_menu.`path` = '' AND button_menu.`component` = '')
 OR (button_menu.`id` = 5065 AND button_menu.`name` = '解绑课程' AND button_menu.`permission` = 'edu:class-course:unbind' AND button_menu.`parent_id` = 5063
        AND button_menu.`path` = '' AND button_menu.`component` = '')
 OR (button_menu.`id` = 5066 AND button_menu.`name` = '查询班课' AND button_menu.`permission` = 'edu:class-course:query' AND button_menu.`parent_id` = 5063
        AND button_menu.`path` = '' AND button_menu.`component` = '')
);

DROP TEMPORARY TABLE IF EXISTS `nus_a9_retired_menu`;
CREATE TEMPORARY TABLE `nus_a9_retired_menu` (`id` bigint PRIMARY KEY);
INSERT INTO `nus_a9_retired_menu` (`id`)
SELECT m.`id` FROM `system_menu` m
WHERE m.`deleted` = b'0' AND m.`type` = 2 AND (
    (m.`id` = 5063 AND m.`name` = '班课关联' AND m.`permission` = 'edu:class-course:list'
        AND m.`parent_id` = 5047 AND m.`path` = 'class-course' AND m.`component` = 'edu/classcourse/index')
 OR (m.`id` = 5101 AND m.`name` = 'AI对话' AND m.`permission` = 'edu:ai-review:chat'
        AND m.`parent_id` = 5075 AND m.`path` = 'ai-chat' AND m.`component` = 'edu/aireview/chat')
 OR (m.`id` = 5102 AND m.`name` = 'AI分析' AND m.`permission` = 'edu:ai-review:analysis'
        AND m.`parent_id` = 5075 AND m.`path` = 'ai-analysis' AND m.`component` = 'edu/aireview/analysis')
 OR (m.`id` = 5104 AND m.`name` = '学生统计列表' AND m.`permission` = 'edu:statistics:query'
        AND m.`parent_id` = 5047 AND m.`path` = 'statistics/student-list' AND m.`component` = 'edu/statistics/student-list')
) AND NOT EXISTS (SELECT 1 FROM `system_menu` child
    WHERE child.`parent_id` = m.`id` AND child.`deleted` = b'0');

UPDATE `system_role_menu` rm
JOIN `nus_a9_retired_menu` retired ON retired.`id` = rm.`menu_id`
SET rm.`deleted` = b'1'
WHERE rm.`deleted` = b'0';

UPDATE `system_menu` m
JOIN `nus_a9_retired_menu` retired ON retired.`id` = m.`id`
SET m.`deleted` = b'1'
WHERE m.`deleted` = b'0';

DROP TEMPORARY TABLE `nus_a9_retired_menu`;

-- ===== 统计口径配置 =====
-- 增加成绩分档和课程解锁比例；仅停用仍为 2025 的旧学年配置。
INSERT INTO infra_config (category, type, name, config_key, value, visible, remark,
                          creator, create_time, updater, update_time, deleted)
SELECT 'edu', 2, '成绩良好比例', 'edu.score.good-ratio', '0.75', b'1',
       '任务满分的良好档比例', 'system', NOW(), 'system', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM infra_config WHERE config_key = 'edu.score.good-ratio' AND deleted = b'0');

INSERT INTO infra_config (category, type, name, config_key, value, visible, remark,
                          creator, create_time, updater, update_time, deleted)
SELECT 'edu', 2, '成绩优秀比例', 'edu.score.excellent-ratio', '0.85', b'1',
       '任务满分的优秀档比例', 'system', NOW(), 'system', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM infra_config WHERE config_key = 'edu.score.excellent-ratio' AND deleted = b'0');

INSERT INTO infra_config (category, type, name, config_key, value, visible, remark,
                          creator, create_time, updater, update_time, deleted)
SELECT 'edu', 2, '课程等级解锁比例', 'edu.course.unlock-ratio', '0.8', b'1',
       '上一等级任务达标率达到该比例后解锁', 'system', NOW(), 'system', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM infra_config WHERE config_key = 'edu.course.unlock-ratio' AND deleted = b'0');

UPDATE infra_config SET deleted = b'1'
WHERE config_key = 'edu.current.academic.year' AND value = '2025' AND deleted = b'0';

-- ===== AI 评阅任务归属与认领 =====
-- 为 AI 评阅批量任务记录发起教师、执行令牌和失败原因，不改动已有任务。
SET @nus_a5_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'ai_edu_review_task' AND column_name = 'teacher_id'),
    'SELECT 1',
    'ALTER TABLE `ai_edu_review_task` ADD COLUMN `teacher_id` bigint NULL COMMENT ''发起教师ID'' AFTER `class_id`'
);
PREPARE nus_a5_stmt FROM @nus_a5_ddl;
EXECUTE nus_a5_stmt;
DEALLOCATE PREPARE nus_a5_stmt;

SET @nus_a5_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'ai_edu_review_task' AND column_name = 'generation_token'),
    'SELECT 1',
    'ALTER TABLE `ai_edu_review_task` ADD COLUMN `generation_token` varchar(64) NULL COMMENT ''执行实例认领令牌'' AFTER `teacher_id`'
);
PREPARE nus_a5_stmt FROM @nus_a5_ddl;
EXECUTE nus_a5_stmt;
DEALLOCATE PREPARE nus_a5_stmt;

SET @nus_a5_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'ai_edu_review_task' AND column_name = 'error_message'),
    'SELECT 1',
    'ALTER TABLE `ai_edu_review_task` ADD COLUMN `error_message` varchar(1000) NULL COMMENT ''失败原因'' AFTER `generation_token`'
);
PREPARE nus_a5_stmt FROM @nus_a5_ddl;
EXECUTE nus_a5_stmt;
DEALLOCATE PREPARE nus_a5_stmt;

-- ===== AI 对话按菜单授权 =====
-- “AI 对话”页面菜单带上权限标识，对话接口按该权限校验：能看到该页面的角色即可使用，
-- 其它角色（如学生）不能再直接调用。授权在“角色管理”中配置；学校改动过该菜单时不修改。
UPDATE `system_menu`
SET `permission` = 'ai:chat:use'
WHERE `id` = 2759 AND `type` = 2 AND `permission` = '' AND `path` = 'chat'
  AND `component` = 'ai/chat/index/index.vue' AND `deleted` = b'0';
