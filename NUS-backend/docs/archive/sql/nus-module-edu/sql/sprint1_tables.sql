-- =============================================
-- NUS Sprint 1 核心表建表脚本
-- 日期：2026-03-14
-- =============================================

-- 1. 实训任务表
CREATE TABLE IF NOT EXISTS `edu_homework` (
    `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `homework_name`         varchar(200) NOT NULL COMMENT '任务名称',
    `homework_type`         tinyint      NOT NULL DEFAULT 1 COMMENT '任务类型：1=平时任务, 2=考核任务',
    `homework_level`        tinyint      NOT NULL DEFAULT 1 COMMENT '任务等级：0=专业, 1=初级, 2=中级, 3=高级',
    `course_id`             bigint       NOT NULL COMMENT '所属课程ID',
    `class_id`              bigint       NOT NULL COMMENT '所属班级ID',
    `indicator_first_id`    bigint       NOT NULL COMMENT '一级技能指标ID',
    `indicator_second_id`   bigint       NOT NULL COMMENT '二级技能指标ID',
    `requirement`           text         NOT NULL COMMENT '任务要求（富文本）',
    `deadline`              datetime     NOT NULL COMMENT '截止时间',
    `auto_grade`            tinyint      NOT NULL DEFAULT 0 COMMENT '是否自动批改：0=手动, 1=自动AI批改',
    `pass_score`            int          NOT NULL DEFAULT 60 COMMENT '及格分数线',
    `score`                 int          NOT NULL DEFAULT 100 COMMENT '满分值',
    `teacher_id`            bigint       NOT NULL COMMENT '发布教师ID',
    `status`                tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0=草稿, 1=已发布, 2=已截止, 3=已归档',
    `creator`               varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    INDEX `idx_course_class` (`course_id`, `class_id`),
    INDEX `idx_teacher` (`teacher_id`),
    INDEX `idx_deadline` (`deadline`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实训任务表';

-- 2. 任务提交记录表
CREATE TABLE IF NOT EXISTS `edu_homework_record` (
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `homework_id`       bigint       NOT NULL COMMENT '关联任务ID',
    `student_id`        bigint       NOT NULL COMMENT '学生ID',
    `content`           text         NULL COMMENT '提交内容',
    `attachment_urls`   varchar(2000) NULL COMMENT '附件URL列表（JSON数组）',
    `submit_time`       datetime     NULL COMMENT '提交时间',
    `score`             int          NULL COMMENT '得分（0-100）',
    `comment`           text         NULL COMMENT '教师/AI评语',
    `correct_type`      tinyint      NULL COMMENT '批改方式：1=手动, 2=AI',
    `correct_time`      datetime     NULL COMMENT '批改时间',
    `status`            tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0=未提交, 1=已提交待批改, 2=已批改, 3=已打回重做, 4=已重新提交',
    `creator`           varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`         bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_homework_student` (`homework_id`, `student_id`, `deleted`),
    INDEX `idx_student` (`student_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务提交记录表';

-- 3. 任务草稿表
CREATE TABLE IF NOT EXISTS `edu_homework_draft` (
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '草稿ID',
    `homework_id`       bigint       NOT NULL COMMENT '关联任务ID',
    `student_id`        bigint       NOT NULL COMMENT '学生ID',
    `content`           text         NULL COMMENT '草稿内容',
    `attachment_urls`   varchar(2000) NULL COMMENT '附件URL列表（JSON数组）',
    `creator`           varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`         bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_homework_student` (`homework_id`, `student_id`, `deleted`),
    INDEX `idx_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务草稿表';

-- 4. 题库表
CREATE TABLE IF NOT EXISTS `edu_question_bank` (
    `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT '题目ID',
    `title`                 varchar(200) NOT NULL COMMENT '题目标题',
    `requirement`           text         NOT NULL COMMENT '题目要求',
    `indicator_first_id`    bigint       NOT NULL COMMENT '一级技能指标ID',
    `indicator_second_id`   bigint       NOT NULL COMMENT '二级技能指标ID',
    `homework_level`        tinyint      NOT NULL DEFAULT 1 COMMENT '任务等级：0=专业, 1=初级, 2=中级, 3=高级',
    `teacher_id`            bigint       NOT NULL COMMENT '创建教师ID（数据隔离键）',
    `creator`               varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    INDEX `idx_teacher` (`teacher_id`),
    INDEX `idx_indicator` (`indicator_first_id`, `indicator_second_id`),
    INDEX `idx_level` (`homework_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题库表';

-- 5. 班级课程关联表
CREATE TABLE IF NOT EXISTS `edu_class_course` (
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `class_id`      bigint       NOT NULL COMMENT '班级ID',
    `course_id`     bigint       NOT NULL COMMENT '课程ID',
    `creator`       varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`       varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`     bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_class_course` (`class_id`, `course_id`, `deleted`),
    INDEX `idx_course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级课程关联表';

-- 6. 课程资源表
CREATE TABLE IF NOT EXISTS `edu_course_resource` (
    `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT '资源ID',
    `resource_name`         varchar(200) NOT NULL COMMENT '资源名称',
    `course_id`             bigint       NOT NULL COMMENT '所属课程ID',
    `file_url`              varchar(500) NOT NULL COMMENT '文件URL',
    `file_type`             varchar(50)  NOT NULL COMMENT 'MIME类型',
    `file_size`             bigint       NOT NULL COMMENT '文件大小（字节）',
    `standard_audio_url`    varchar(500) NULL COMMENT '标准音频URL（音频文件自动生成）',
    `creator`               varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    INDEX `idx_course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程资源表';

-- 8. 学生排名历史表
CREATE TABLE IF NOT EXISTS `edu_student_ranking` (
    `id`                bigint          NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `student_id`        bigint          NOT NULL COMMENT '学生ID',
    `class_ranking`     int             NULL COMMENT '班级排名',
    `major_ranking`     int             NULL COMMENT '专业排名',
    `total_score`       decimal(5,2)    NULL COMMENT '综合得分',
    `record_date`       date            NOT NULL COMMENT '记录日期',
    `creator`           varchar(64)     NULL DEFAULT '' COMMENT '创建者',
    `create_time`       datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           varchar(64)     NULL DEFAULT '' COMMENT '更新者',
    `update_time`       datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           bit(1)          NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`         bigint          NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    INDEX `idx_student` (`student_id`),
    INDEX `idx_record_date` (`record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生排名历史表';
