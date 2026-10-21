-- ============================================================
-- Sprint 11：实训课程详情/班级参与详情能力升级
-- 目标：
-- 1) 课程-班级关联支持“学期维度”
-- 2) 实训任务（edu_homework）支持学期字段，便于按班级学期过滤
-- 3) 保持历史数据可用，并提供回填语句
-- 适用库：MySQL 5.7 / 8.0（兼容不支持 ADD COLUMN IF NOT EXISTS 的版本）
-- 执行建议：先执行结构变更，再执行数据回填，再发布后端代码
-- ============================================================

SET NAMES utf8mb4;
SET
FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. 扩展 edu_class_course：补齐学期与助教维度
-- ============================================================

-- 1.1 新增字段：学期名称（用于课程内班级筛选）
SET
@sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `edu_class_course` ADD COLUMN `semester_name` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' COMMENT ''学期名称（示例：2025-2026-2）'' AFTER `course_id`',
    'SELECT ''skip add column edu_class_course.semester_name'''
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'edu_class_course'
    AND COLUMN_NAME = 'semester_name'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1.2 新增字段：学期开始日期（用于班级状态计算：未开始/进行中/已结束）
SET
@sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `edu_class_course` ADD COLUMN `semester_start_date` date DEFAULT NULL COMMENT ''学期开始日期'' AFTER `semester_name`',
    'SELECT ''skip add column edu_class_course.semester_start_date'''
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'edu_class_course'
    AND COLUMN_NAME = 'semester_start_date'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1.3 新增字段：学期结束日期（用于班级状态计算）
SET
@sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `edu_class_course` ADD COLUMN `semester_end_date` date DEFAULT NULL COMMENT ''学期结束日期'' AFTER `semester_start_date`',
    'SELECT ''skip add column edu_class_course.semester_end_date'''
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'edu_class_course'
    AND COLUMN_NAME = 'semester_end_date'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1.4 新增字段：助教教师ID串（逗号分隔）
SET
@sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `edu_class_course` ADD COLUMN `assistant_teacher_ids` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''助教教师ID串，逗号分隔'' AFTER `semester_end_date`',
    'SELECT ''skip add column edu_class_course.assistant_teacher_ids'''
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'edu_class_course'
    AND COLUMN_NAME = 'assistant_teacher_ids'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1.5 删除旧唯一索引：uk_cc（若存在）
SET
@sql = (
  SELECT IF(
    COUNT(*) > 0,
    'DROP INDEX `uk_cc` ON `edu_class_course`',
    'SELECT ''skip drop index edu_class_course.uk_cc'''
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'edu_class_course'
    AND INDEX_NAME = 'uk_cc'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1.6 新唯一索引：同一班级同一课程同一学期仅允许一条有效关联
SET
@sql = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE UNIQUE INDEX `uk_cc_semester` ON `edu_class_course` (`class_id`, `course_id`, `semester_name`, `deleted`)',
    'SELECT ''skip create index edu_class_course.uk_cc_semester'''
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'edu_class_course'
    AND INDEX_NAME = 'uk_cc_semester'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1.7 新增查询索引：支撑课程详情页按课程+学期筛选班级
SET
@sql = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE INDEX `idx_course_semester` ON `edu_class_course` (`course_id`, `semester_name`)',
    'SELECT ''skip create index edu_class_course.idx_course_semester'''
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'edu_class_course'
    AND INDEX_NAME = 'idx_course_semester'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ============================================================
-- 2. 扩展 edu_homework：补齐学期字段
-- ============================================================

-- 2.1 新增字段：任务所属学期（便于班级参与详情按学期过滤任务）
SET
@sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `edu_homework` ADD COLUMN `semester_name` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '''' COMMENT ''任务所属学期（示例：2025-2026-2）'' AFTER `class_id`',
    'SELECT ''skip add column edu_homework.semester_name'''
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'edu_homework'
    AND COLUMN_NAME = 'semester_name'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2.2 新增组合索引：支撑课程+班级+学期任务查询
SET
@sql = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE INDEX `idx_course_class_semester` ON `edu_homework` (`course_id`, `class_id`, `semester_name`)',
    'SELECT ''skip create index edu_homework.idx_course_class_semester'''
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'edu_homework'
    AND INDEX_NAME = 'idx_course_class_semester'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ============================================================
-- 3. 历史数据回填（可选但建议执行）
-- ============================================================

-- 回填策略：
-- 对于 semester_name 为空的历史任务，尝试用“同班级同课程最新一条班课关联”的 semester_name 回填
-- 注意：若历史同班级同课程存在多学期开课，本回填仅采用最新关联，实际可按业务再校正
UPDATE `edu_homework` h
    JOIN (
    SELECT `class_id`, `course_id`, MAX (`id`) AS max_id
    FROM `edu_class_course`
    WHERE `deleted` = b'0'
    GROUP BY `class_id`, `course_id`
    ) rel
ON rel.class_id = h.class_id AND rel.course_id = h.course_id
    JOIN `edu_class_course` cc ON cc.id = rel.max_id
    SET h.semester_name = cc.semester_name
WHERE h.semester_name = ''
  AND cc.semester_name <> ''
  AND h.deleted = b'0';

SET
FOREIGN_KEY_CHECKS = 1;
