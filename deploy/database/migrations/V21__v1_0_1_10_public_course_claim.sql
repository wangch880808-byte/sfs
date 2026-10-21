-- V1.0.1.10 公共课认领：只回填唯一原始任务布置者，保留同步依赖的审计字段。
-- 每个对象独立判断，兼容 MySQL 非事务 DDL 部分完成后的 repair、重跑。
SET @nus_v21_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'edu_class_course'
              AND column_name = 'claim_teacher_id'),
    'SELECT 1',
    'ALTER TABLE `edu_class_course` ADD COLUMN `claim_teacher_id` BIGINT NULL COMMENT ''公共课认领教师（用户 ID）'' AFTER `backfill_pending`'
);
PREPARE nus_v21_stmt FROM @nus_v21_ddl;
EXECUTE nus_v21_stmt;
DEALLOCATE PREPARE nus_v21_stmt;

SET @nus_v21_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'edu_class_course'
              AND column_name = 'claim_time'),
    'SELECT 1',
    'ALTER TABLE `edu_class_course` ADD COLUMN `claim_time` DATETIME NULL COMMENT ''认领时间'' AFTER `claim_teacher_id`'
);
PREPARE nus_v21_stmt FROM @nus_v21_ddl;
EXECUTE nus_v21_stmt;
DEALLOCATE PREPARE nus_v21_stmt;

SET @nus_v21_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'edu_class_course'
              AND index_name = 'idx_edu_class_course_claim'),
    'SELECT 1',
    'ALTER TABLE `edu_class_course` ADD INDEX `idx_edu_class_course_claim` (`course_id`, `claim_teacher_id`, `status`)'
);
PREPARE nus_v21_stmt FROM @nus_v21_ddl;
EXECUTE nus_v21_stmt;
DEALLOCATE PREPARE nus_v21_stmt;

SET @nus_v21_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'edu_class_course'
              AND index_name = 'idx_edu_class_course_claim_teacher'),
    'SELECT 1',
    'ALTER TABLE `edu_class_course` ADD INDEX `idx_edu_class_course_claim_teacher` (`claim_teacher_id`, `status`)'
);
PREPARE nus_v21_stmt FROM @nus_v21_ddl;
EXECUTE nus_v21_stmt;
DEALLOCATE PREPARE nus_v21_stmt;

-- 先按班课、规范化学期和教师压缩任务，避免为每条关联反复扫描十万级任务明细。
-- 公共课类型历史取值为 0；已发布、已结束分别为 1、2，补发任务不能用于推断认领人。
DROP TEMPORARY TABLE IF EXISTS nus_v21_homework_teachers;
CREATE TEMPORARY TABLE nus_v21_homework_teachers (
    course_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    semester_name VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    teacher_id BIGINT NOT NULL,
    first_create_time DATETIME NOT NULL,
    PRIMARY KEY (course_id, class_id, semester_name, teacher_id)
) ENGINE=InnoDB;
INSERT INTO nus_v21_homework_teachers (course_id, class_id, semester_name, teacher_id, first_create_time)
SELECT h.course_id, h.class_id, TRIM(COALESCE(h.semester_name, '')), h.teacher_id, MIN(h.create_time)
FROM edu_homework h
JOIN capacity_course c ON c.id = h.course_id AND c.deleted = 0 AND c.is_public_course = 0
WHERE h.deleted = 0 AND h.source_homework_id IS NULL AND h.status IN (1, 2) AND h.teacher_id > 0
GROUP BY h.course_id, h.class_id, TRIM(COALESCE(h.semester_name, '')), h.teacher_id;

-- 空任务学期沿用历史匹配口径；非空任务学期须与班课学期 TRIM 后一致。
DROP TEMPORARY TABLE IF EXISTS nus_v21_claims;
CREATE TEMPORARY TABLE nus_v21_claims (
    class_course_id BIGINT NOT NULL PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    claim_time DATETIME NOT NULL
) ENGINE=InnoDB;
INSERT INTO nus_v21_claims (class_course_id, teacher_id, claim_time)
SELECT cc.id, MIN(h.teacher_id), MIN(h.first_create_time)
FROM edu_class_course cc
JOIN capacity_course c ON c.id = cc.course_id AND c.deleted = 0 AND c.is_public_course = 0
JOIN nus_v21_homework_teachers h ON h.course_id = cc.course_id AND h.class_id = cc.class_id
    AND (h.semester_name = '' OR h.semester_name = TRIM(cc.semester_name))
WHERE cc.deleted = 0 AND cc.status = 0 AND cc.claim_teacher_id IS NULL
GROUP BY cc.id
HAVING COUNT(DISTINCT h.teacher_id) = 1;

-- 显式赋回原时间，避免 ON UPDATE CURRENT_TIMESTAMP 改写同步审计信息。
UPDATE edu_class_course cc
JOIN nus_v21_claims b ON b.class_course_id = cc.id
SET cc.claim_teacher_id = b.teacher_id, cc.claim_time = b.claim_time, cc.update_time = cc.update_time
WHERE cc.deleted = 0 AND cc.status = 0 AND cc.claim_teacher_id IS NULL;

DROP TEMPORARY TABLE IF EXISTS nus_v21_claims;
DROP TEMPORARY TABLE IF EXISTS nus_v21_homework_teachers;

-- V1.0.1.10 起“归档”改称“封存”（只读、仍计入统计），不再新建草稿，旧草稿显示为“待发布”。
-- 只改仍是出厂文字的行，管理员自定义过的标签保持不变；保留原更新时间。
UPDATE system_dict_data SET label = '已封存', update_time = update_time
WHERE dict_type = 'edu_homework_status' AND value = '3' AND label = '已归档' AND deleted = 0;
UPDATE system_dict_data SET label = '待发布', update_time = update_time
WHERE dict_type = 'edu_homework_status' AND value = '0' AND label = '草稿' AND deleted = 0;
