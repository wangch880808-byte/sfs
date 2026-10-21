-- =============================================
-- NUS Sprint 3 AI教育评阅模块建表脚本
-- 日期：2026-03-14
-- =============================================

-- 1. AI评阅日志表
CREATE TABLE IF NOT EXISTS `ai_edu_review_log` (
    `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `homework_record_id`    bigint       NOT NULL COMMENT '任务记录ID',
    `model_id`              bigint       NULL COMMENT 'AI模型ID',
    `model_name`            varchar(100) NULL COMMENT '模型名称',
    `prompt`                text         NULL COMMENT '发送的Prompt',
    `response`              text         NULL COMMENT 'AI返回内容',
    `score`                 int          NULL COMMENT 'AI评分(0-100)',
    `comment`               text         NULL COMMENT 'AI评语',
    `status`                tinyint      NOT NULL DEFAULT 0 COMMENT '0=处理中,1=成功,2=失败',
    `error_message`         varchar(500) NULL COMMENT '错误信息',
    `cost_time`             int          NULL COMMENT '耗时(毫秒)',
    `creator`               varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    INDEX `idx_homework_record` (`homework_record_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI评阅日志表';

-- 2. AI批改异步任务表
CREATE TABLE IF NOT EXISTS `ai_edu_review_task` (
    `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `homework_id`           bigint       NOT NULL COMMENT '任务ID',
    `class_id`              bigint       NULL COMMENT '班级ID',
    `total_count`           int          NOT NULL DEFAULT 0 COMMENT '总数',
    `success_count`         int          NOT NULL DEFAULT 0 COMMENT '成功数',
    `fail_count`            int          NOT NULL DEFAULT 0 COMMENT '失败数',
    `status`                tinyint      NOT NULL DEFAULT 0 COMMENT '0=处理中,1=完成,2=部分失败',
    `creator`               varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    INDEX `idx_homework` (`homework_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI批改异步任务表';
