-- ============================================================
-- Sprint 16: immutable homework assignee snapshot and redo count
-- MySQL 5.7 / 8.0
-- Existing published tasks are backfilled from current active class members.
-- Historical students already removed from a class cannot be reconstructed.
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `edu_homework_record` ADD COLUMN `redo_count` int NOT NULL DEFAULT 0 COMMENT ''累计打回重做次数'' AFTER `status`',
    'SELECT ''skip add edu_homework_record.redo_count''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_homework_record'
    AND COLUMN_NAME = 'redo_count'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `edu_homework_assignee` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '快照ID',
  `homework_id` bigint NOT NULL COMMENT '任务ID',
  `student_id` bigint NOT NULL COMMENT '任务发布时学生ID',
  `class_id` bigint NOT NULL COMMENT '任务发布时班级ID',
  `assigned_time` datetime NOT NULL COMMENT '分配时间',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_edu_homework_assignee` (`homework_id`, `student_id`),
  KEY `idx_edu_homework_assignee_student` (`student_id`, `homework_id`),
  KEY `idx_edu_homework_assignee_class` (`class_id`, `homework_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务发布学生快照';

INSERT IGNORE INTO `edu_homework_assignee`
  (`homework_id`, `student_id`, `class_id`, `assigned_time`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT h.id, rel.student_id, h.class_id, COALESCE(h.create_time, NOW()),
       h.creator, NOW(), h.updater, NOW(), b'0'
FROM `edu_homework` h
JOIN `edu_class_student` rel ON rel.class_id = h.class_id AND rel.deleted = b'0'
WHERE h.deleted = b'0' AND h.status <> 0;

SET FOREIGN_KEY_CHECKS = 1;
