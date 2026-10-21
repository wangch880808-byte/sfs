-- ============================================================
-- Sprint 16: P0 data exchange foundation
-- MySQL 5.7 / 8.0
--
-- Before execution, back up the database. The migration aborts when it finds
-- duplicate usernames or duplicate user profiles. Class/student duplicate
-- rows are consolidated automatically and cannot be recovered by rollback.
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. Fail fast on business keys that cannot be consolidated automatically.
DROP TEMPORARY TABLE IF EXISTS `_p0_data_exchange_preflight`;
CREATE TEMPORARY TABLE `_p0_data_exchange_preflight` (
  `issue` varchar(64) NOT NULL,
  PRIMARY KEY (`issue`)
) ENGINE=InnoDB;

INSERT INTO `_p0_data_exchange_preflight` (`issue`)
SELECT 'DUPLICATE_SYSTEM_USERS_USERNAME'
WHERE EXISTS (
  SELECT 1 FROM `system_users` GROUP BY `username` HAVING COUNT(*) > 1
)
UNION ALL
SELECT 'DUPLICATE_SYSTEM_USERS_USERNAME'
WHERE EXISTS (
  SELECT 1 FROM `system_users` GROUP BY `username` HAVING COUNT(*) > 1
);

TRUNCATE TABLE `_p0_data_exchange_preflight`;

INSERT INTO `_p0_data_exchange_preflight` (`issue`)
SELECT 'DUPLICATE_USER_PROFILE_USER_ID'
WHERE EXISTS (
  SELECT 1 FROM `system_user_profile` GROUP BY `user_id` HAVING COUNT(*) > 1
)
UNION ALL
SELECT 'DUPLICATE_USER_PROFILE_USER_ID'
WHERE EXISTS (
  SELECT 1 FROM `system_user_profile` GROUP BY `user_id` HAVING COUNT(*) > 1
);

DROP TEMPORARY TABLE `_p0_data_exchange_preflight`;

-- 2. Stable organization business key and node type.
SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `system_dept` ADD COLUMN `dept_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''稳定组织编码'' AFTER `id`',
    'SELECT ''skip add system_dept.dept_code''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_dept' AND COLUMN_NAME = 'dept_code'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `system_dept` ADD COLUMN `dept_type` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''组织类型：SCHOOL/COLLEGE/MAJOR'' AFTER `dept_code`',
    'SELECT ''skip add system_dept.dept_type''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_dept' AND COLUMN_NAME = 'dept_type'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `system_dept`
SET `dept_code` = CONCAT('ORG-', `id`)
WHERE `dept_code` IS NULL OR TRIM(`dept_code`) = '';

UPDATE `system_dept` d
LEFT JOIN `system_dept` p ON p.id = d.parent_id
SET d.`dept_type` = CASE
  WHEN d.parent_id = 0 THEN 'SCHOOL'
  WHEN p.parent_id = 0 THEN 'COLLEGE'
  ELSE 'MAJOR'
END
WHERE d.`dept_type` IS NULL OR TRIM(d.`dept_type`) = '';

ALTER TABLE `system_dept`
  MODIFY COLUMN `dept_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定组织编码',
  MODIFY COLUMN `dept_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '组织类型：SCHOOL/COLLEGE/MAJOR';

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'CREATE UNIQUE INDEX `uk_system_dept_dept_code` ON `system_dept` (`dept_code`)',
    'SELECT ''skip create uk_system_dept_dept_code''')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_dept'
    AND INDEX_NAME = 'uk_system_dept_dept_code'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'CREATE INDEX `idx_system_dept_dept_type` ON `system_dept` (`dept_type`)',
    'SELECT ''skip create idx_system_dept_dept_type''')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_dept'
    AND INDEX_NAME = 'idx_system_dept_dept_type'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. Stable class business key.
SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `edu_class` ADD COLUMN `class_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''稳定班级编码'' AFTER `id`',
    'SELECT ''skip add edu_class.class_code''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_class' AND COLUMN_NAME = 'class_code'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `edu_class`
SET `class_code` = CONCAT('CLASS-', `id`)
WHERE `class_code` IS NULL OR TRIM(`class_code`) = '';

ALTER TABLE `edu_class`
  MODIFY COLUMN `class_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '稳定班级编码';

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'CREATE UNIQUE INDEX `uk_edu_class_class_code` ON `edu_class` (`class_code`)',
    'SELECT ''skip create uk_edu_class_class_code''')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_class'
    AND INDEX_NAME = 'uk_edu_class_class_code'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. Global user/profile keys required by idempotent imports.
SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'CREATE UNIQUE INDEX `uk_system_users_username` ON `system_users` (`username`)',
    'SELECT ''skip create uk_system_users_username''')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_users'
    AND INDEX_NAME = 'uk_system_users_username'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'CREATE UNIQUE INDEX `uk_system_user_profile_user_id` ON `system_user_profile` (`user_id`)',
    'SELECT ''skip create uk_system_user_profile_user_id''')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_user_profile'
    AND INDEX_NAME = 'uk_system_user_profile_user_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. Consolidate duplicate class/student rows, preferring an active row.
DROP TEMPORARY TABLE IF EXISTS `_p0_class_student_winner`;
CREATE TEMPORARY TABLE `_p0_class_student_winner` AS
SELECT `class_id`, `student_id`,
       COALESCE(MIN(CASE WHEN `deleted` = b'0' THEN `id` END), MIN(`id`)) AS `keep_id`
FROM `edu_class_student`
GROUP BY `class_id`, `student_id`;

ALTER TABLE `_p0_class_student_winner`
  ADD PRIMARY KEY (`class_id`, `student_id`),
  ADD UNIQUE KEY `uk_keep_id` (`keep_id`);

DELETE rel
FROM `edu_class_student` rel
JOIN `_p0_class_student_winner` winner
  ON winner.class_id = rel.class_id AND winner.student_id = rel.student_id
WHERE rel.id <> winner.keep_id;

DROP TEMPORARY TABLE `_p0_class_student_winner`;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'CREATE UNIQUE INDEX `uk_edu_class_student_relation` ON `edu_class_student` (`class_id`, `student_id`)',
    'SELECT ''skip create uk_edu_class_student_relation''')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_class_student'
    AND INDEX_NAME = 'uk_edu_class_student_relation'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 6. Import preview/confirmation batches and row-level receipts.
CREATE TABLE IF NOT EXISTS `edu_import_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '批次ID',
  `batch_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '批次号',
  `biz_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '导入业务类型',
  `template_version` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板版本',
  `source_file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件名',
  `source_file_url` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件私有地址',
  `source_file_sha256` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件SHA-256',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '批次状态',
  `total_count` int NOT NULL DEFAULT 0 COMMENT '总行数',
  `valid_count` int NOT NULL DEFAULT 0 COMMENT '有效行数',
  `invalid_count` int NOT NULL DEFAULT 0 COMMENT '无效行数',
  `inserted_count` int NOT NULL DEFAULT 0 COMMENT '新增行数',
  `updated_count` int NOT NULL DEFAULT 0 COMMENT '更新行数',
  `skipped_count` int NOT NULL DEFAULT 0 COMMENT '跳过行数',
  `options_json` json DEFAULT NULL COMMENT '导入选项',
  `error_file_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误回执私有地址',
  `operator_id` bigint NOT NULL COMMENT '操作人ID',
  `expire_time` datetime NOT NULL COMMENT '预检过期时间',
  `confirm_time` datetime DEFAULT NULL COMMENT '确认时间',
  `failure_reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '失败原因',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_edu_import_batch_no` (`batch_no`),
  KEY `idx_edu_import_batch_operator` (`operator_id`, `create_time`),
  KEY `idx_edu_import_batch_status_expire` (`status`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导入预检及执行批次';

CREATE TABLE IF NOT EXISTS `edu_import_batch_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '批次明细ID',
  `batch_id` bigint NOT NULL COMMENT '批次ID',
  `sheet_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '工作表名称',
  `row_number` int NOT NULL COMMENT 'Excel行号',
  `business_key` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务键',
  `normalized_data` json DEFAULT NULL COMMENT '标准化后的行数据',
  `action` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'INSERT/UPDATE/SKIP',
  `validation_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'VALID/INVALID',
  `validation_errors` json DEFAULT NULL COMMENT '校验错误列表',
  `before_data` json DEFAULT NULL COMMENT '变更前数据',
  `after_data` json DEFAULT NULL COMMENT '变更后数据',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_edu_import_batch_item_row` (`batch_id`, `sheet_name`, `row_number`),
  KEY `idx_edu_import_batch_item_validation` (`batch_id`, `validation_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导入批次行级预检及回执';

SET FOREIGN_KEY_CHECKS = 1;
