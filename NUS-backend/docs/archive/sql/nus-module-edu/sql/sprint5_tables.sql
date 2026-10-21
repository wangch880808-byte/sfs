-- =============================================
-- NUS Sprint 5 留言互动表建表脚本
-- 日期：2026-03-15
-- =============================================

-- 留言互动表
CREATE TABLE IF NOT EXISTS `edu_message` (
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `class_id`          bigint       NOT NULL COMMENT '班级ID',
    `course_id`         bigint       NOT NULL COMMENT '课程ID',
    `student_id`        bigint       NOT NULL COMMENT '学生ID',
    `content`           text         NOT NULL COMMENT '留言内容',
    `type`              tinyint      NOT NULL DEFAULT 0 COMMENT '0=学生留言,1=教师回复',
    `creator`           varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`         bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    INDEX `idx_class_course` (`class_id`, `course_id`),
    INDEX `idx_student` (`student_id`),
    INDEX `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='留言互动表';
