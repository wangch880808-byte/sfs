-- V24: 等级闯关按“课程 + 一级指标（技能）”独立计算
-- 1. edu_student_level_unlock 增加 indicator_first_id 列（0 表示任务没填一级指标）
-- 2. 唯一键 uk_student_course_level 改为 (student_id, course_id, indicator_first_id, level)
-- 3. 把按整门课写的旧快照复制到这门课已有的每个一级指标下，避免升级后把已解锁的学生锁回去
-- 全部按 information_schema 判断后动态执行，可重复执行。

-- ============================================================
-- 1. 增加 indicator_first_id 列
-- ============================================================
SET @nus_v24_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'edu_student_level_unlock'
              AND column_name = 'indicator_first_id'),
    'SELECT 1',
    'ALTER TABLE `edu_student_level_unlock` ADD COLUMN `indicator_first_id` bigint NOT NULL DEFAULT 0 COMMENT ''一级指标（技能）编号，0=未填指标'' AFTER `course_id`'
);
PREPARE nus_v24_stmt FROM @nus_v24_ddl;
EXECUTE nus_v24_stmt;
DEALLOCATE PREPARE nus_v24_stmt;

-- ============================================================
-- 2. 唯一键：旧键（3 列）换成新键（4 列）
--    用 information_schema.statistics 的列数区分新旧，保证幂等。
-- ============================================================
SET @nus_v24_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'edu_student_level_unlock'
        AND index_name = 'uk_student_course_level') = 3,
    'ALTER TABLE `edu_student_level_unlock` DROP INDEX `uk_student_course_level`',
    'SELECT 1'
);
PREPARE nus_v24_stmt FROM @nus_v24_ddl;
EXECUTE nus_v24_stmt;
DEALLOCATE PREPARE nus_v24_stmt;

SET @nus_v24_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'edu_student_level_unlock'
              AND index_name = 'uk_student_course_level'),
    'SELECT 1',
    'ALTER TABLE `edu_student_level_unlock` ADD UNIQUE KEY `uk_student_course_level` (`student_id`, `course_id`, `indicator_first_id`, `level`)'
);
PREPARE nus_v24_stmt FROM @nus_v24_ddl;
EXECUTE nus_v24_stmt;
DEALLOCATE PREPARE nus_v24_stmt;

-- ============================================================
-- 3. 旧快照复制到各一级指标
--    旧快照 indicator_first_id=0；对每条旧快照，找到该课程已出现过的一级指标
--    （来自任务的 COALESCE(indicator_first_id, 0)，只取明确填了指标的），
--    为每个指标复制一条同样的解锁记录。原来 0 组的记录保留，供没填指标的老任务使用。
--    INSERT IGNORE 靠新唯一键去重，可重复执行。
-- ============================================================
INSERT IGNORE INTO `edu_student_level_unlock`
    (`student_id`, `course_id`, `indicator_first_id`, `level`, `unlocked_at`, `ratio_at_unlock`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT u.student_id, u.course_id, ind.indicator_first_id, u.level, u.unlocked_at, u.ratio_at_unlock,
       u.creator, u.create_time, u.updater, u.update_time, u.deleted
FROM (
    SELECT student_id, course_id, level, unlocked_at, ratio_at_unlock,
           creator, create_time, updater, update_time, deleted
    FROM `edu_student_level_unlock`
    WHERE indicator_first_id = 0 AND deleted = b'0'
) u
JOIN (
    SELECT DISTINCT course_id, indicator_first_id
    FROM `edu_homework`
    WHERE deleted = 0 AND indicator_first_id IS NOT NULL AND indicator_first_id <> 0
) ind ON ind.course_id = u.course_id;
