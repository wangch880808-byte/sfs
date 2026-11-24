-- V23: 管理员端对齐 — 新表与字段
-- 1. edu_official_course 正式课程目录
-- 2. edu_term 学期表（按组织）
-- 3. edu_class_request 建班审核
-- 4. edu_course_resource 增加审核字段

-- ============================================================
-- 1. 正式课程目录
-- ============================================================
CREATE TABLE IF NOT EXISTS `edu_official_course` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `dept_id`     BIGINT       NOT NULL COMMENT '所属部门 ID',
    `code`        VARCHAR(64)  NOT NULL COMMENT '课程编码',
    `name`        VARCHAR(200) NOT NULL COMMENT '课程名称',
    `credit`      DECIMAL(3,1) NOT NULL DEFAULT 0.0 COMMENT '学分',
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0 在用 1 已归档',
    `remark`      VARCHAR(500) NULL COMMENT '备注',
    `creator`     VARCHAR(64)  NULL COMMENT '创建者',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64)  NULL COMMENT '更新者',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_edu_official_course_code` (`code`, `deleted`),
    KEY `idx_edu_official_course_dept` (`dept_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '正式课程目录';

-- ============================================================
-- 2. 学期表（按组织）
-- ============================================================
CREATE TABLE IF NOT EXISTS `edu_term` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `organization_id` BIGINT       NOT NULL COMMENT '组织（部门） ID',
    `code`            VARCHAR(64)  NOT NULL COMMENT '学期编码（如 2025-2026-2）',
    `name`            VARCHAR(200) NOT NULL COMMENT '学期名称',
    `starts_on`       DATE         NOT NULL COMMENT '开始日期',
    `ends_on`         DATE         NOT NULL COMMENT '结束日期',
    `status`          TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0 待启用 1 启用 2 归档',
    `creator`         VARCHAR(64)  NULL COMMENT '创建者',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`         VARCHAR(64)  NULL COMMENT '更新者',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_edu_term_org_code` (`organization_id`, `code`, `deleted`),
    KEY `idx_edu_term_status` (`status`, `starts_on`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '学期';

-- ============================================================
-- 3. 建班审核
-- ============================================================
CREATE TABLE IF NOT EXISTS `edu_class_request` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `course_id`    BIGINT       NOT NULL COMMENT '课程 ID',
    `class_id`     BIGINT       NOT NULL COMMENT '班级 ID',
    `term`         VARCHAR(64)  NULL COMMENT '学期（字符串快照）',
    `applicant_id` BIGINT       NOT NULL COMMENT '申请教师用户 ID',
    `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0 待审核 1 通过 2 拒绝',
    `reason`       VARCHAR(500) NULL COMMENT '拒绝原因 / 申请备注',
    `reviewer_id`  BIGINT       NULL COMMENT '审核人用户 ID',
    `reviewed_at`  DATETIME     NULL COMMENT '审核时间',
    `class_course_id` BIGINT    NULL COMMENT '通过后生成的班课关联 ID',
    `creator`      VARCHAR(64)  NULL COMMENT '创建者',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`      VARCHAR(64)  NULL COMMENT '更新者',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_edu_class_request_status` (`status`, `create_time`),
    KEY `idx_edu_class_request_applicant` (`applicant_id`),
    KEY `idx_edu_class_request_course` (`course_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '建班申请审核';

-- ============================================================
-- 4. 课程资源审核字段（存量资源迁移为"通过"）
-- ============================================================
ALTER TABLE `edu_course_resource`
    ADD COLUMN `review_status` TINYINT   NOT NULL DEFAULT 1 COMMENT '审核状态 0 待审 1 通过 2 驳回' AFTER `standard_audio_url`,
    ADD COLUMN `review_reason` VARCHAR(500) NULL COMMENT '驳回原因' AFTER `review_status`,
    ADD COLUMN `reviewer_id`   BIGINT    NULL COMMENT '审核人用户 ID' AFTER `review_reason`,
    ADD COLUMN `reviewed_at`   DATETIME  NULL COMMENT '审核时间' AFTER `reviewer_id`;
