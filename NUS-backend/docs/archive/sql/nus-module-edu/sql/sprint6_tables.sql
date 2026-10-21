-- =============================================
-- NUS Sprint 6 校园资讯表建表脚本
-- 日期：2026-03-15
-- =============================================

CREATE TABLE IF NOT EXISTS `edu_news` (
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `title`             varchar(255) NOT NULL COMMENT '标题',
    `introduction`      varchar(500) NULL COMMENT '简介',
    `picture`           varchar(500) NULL COMMENT '封面图片URL',
    `content`           text         NOT NULL COMMENT '内容（富文本）',
    `is_top`            tinyint      NOT NULL DEFAULT 0 COMMENT '是否置顶：0=否,1=是',
    `status`            tinyint      NOT NULL DEFAULT 1 COMMENT '状态：0=下架,1=上架',
    `creator`           varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`         bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_is_top` (`is_top`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园资讯表';
