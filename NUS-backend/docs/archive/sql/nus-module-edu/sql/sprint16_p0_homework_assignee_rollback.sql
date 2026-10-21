-- ============================================================
-- Sprint 16 homework assignee rollback
-- WARNING: assignee snapshots and redo counters will be permanently lost.
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `edu_homework_assignee`;

SET @sql = (
  SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `edu_homework_record` DROP COLUMN `redo_count`',
    'SELECT ''skip drop edu_homework_record.redo_count''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_homework_record'
    AND COLUMN_NAME = 'redo_count'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
