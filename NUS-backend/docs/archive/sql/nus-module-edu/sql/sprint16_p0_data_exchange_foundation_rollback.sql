-- ============================================================
-- Sprint 16 P0 data exchange foundation rollback
-- WARNING:
-- 1. Import batch and receipt data will be deleted.
-- 2. Class/student duplicate rows consolidated by the forward migration
--    cannot be restored by this script.
-- 3. Back up the database before execution.
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `edu_import_batch_item`;
DROP TABLE IF EXISTS `edu_import_batch`;

SET @sql = (
  SELECT IF(COUNT(*) > 0,
    'DROP INDEX `uk_edu_class_student_relation` ON `edu_class_student`',
    'SELECT ''skip drop uk_edu_class_student_relation''')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_class_student'
    AND INDEX_NAME = 'uk_edu_class_student_relation'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) > 0,
    'DROP INDEX `uk_system_user_profile_user_id` ON `system_user_profile`',
    'SELECT ''skip drop uk_system_user_profile_user_id''')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_user_profile'
    AND INDEX_NAME = 'uk_system_user_profile_user_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) > 0,
    'DROP INDEX `uk_system_users_username` ON `system_users`',
    'SELECT ''skip drop uk_system_users_username''')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_users'
    AND INDEX_NAME = 'uk_system_users_username'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) > 0,
    'DROP INDEX `uk_edu_class_class_code` ON `edu_class`',
    'SELECT ''skip drop uk_edu_class_class_code''')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_class'
    AND INDEX_NAME = 'uk_edu_class_class_code'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `edu_class` DROP COLUMN `class_code`',
    'SELECT ''skip drop edu_class.class_code''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_class' AND COLUMN_NAME = 'class_code'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) > 0,
    'DROP INDEX `idx_system_dept_dept_type` ON `system_dept`',
    'SELECT ''skip drop idx_system_dept_dept_type''')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_dept'
    AND INDEX_NAME = 'idx_system_dept_dept_type'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) > 0,
    'DROP INDEX `uk_system_dept_dept_code` ON `system_dept`',
    'SELECT ''skip drop uk_system_dept_dept_code''')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_dept'
    AND INDEX_NAME = 'uk_system_dept_dept_code'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `system_dept` DROP COLUMN `dept_type`',
    'SELECT ''skip drop system_dept.dept_type''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_dept' AND COLUMN_NAME = 'dept_type'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `system_dept` DROP COLUMN `dept_code`',
    'SELECT ''skip drop system_dept.dept_code''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_dept' AND COLUMN_NAME = 'dept_code'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
