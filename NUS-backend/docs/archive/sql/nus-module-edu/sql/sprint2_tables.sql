-- =============================================
-- NUS Sprint 2 新增表建表脚本
-- 日期：2026-03-14
-- =============================================

-- 1. 课程学习进度表
CREATE TABLE IF NOT EXISTS `edu_course_progress` (
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `student_id`          bigint       NOT NULL COMMENT '学生ID',
    `course_id`           bigint       NOT NULL COMMENT '课程ID',
    `total_homework`      int          NOT NULL DEFAULT 0 COMMENT '总任务数',
    `completed_homework`  int          NOT NULL DEFAULT 0 COMMENT '已完成任务数（已批改且达标）',
    `avg_score`           decimal(5,2) NULL     DEFAULT NULL COMMENT '平均分',
    `unlocked_level`      tinyint      NOT NULL DEFAULT 1 COMMENT '已解锁等级：1=初级, 2=中级, 3=高级',
    `creator`             varchar(64)  NULL     DEFAULT '' COMMENT '创建者',
    `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`             varchar(64)  NULL     DEFAULT '' COMMENT '更新者',
    `update_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`           bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_student_course` (`student_id`, `course_id`, `deleted`),
    INDEX `idx_course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程学习进度表';
