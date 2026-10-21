-- =============================================
-- NUS edu 模块 Sprint 1 核心表建表脚本
-- =============================================

-- ----------------------------
-- 1. edu_homework 实训任务
-- ----------------------------
CREATE TABLE `edu_homework` (
    `id`                   bigint       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `homework_name`        varchar(200) NOT NULL COMMENT '任务名称',
    `homework_type`        tinyint      NOT NULL COMMENT '任务类型：1=平时任务, 2=考核任务',
    `homework_level`       tinyint      NOT NULL COMMENT '任务等级：0=专业, 1=初级, 2=中级, 3=高级',
    `course_id`            bigint       NOT NULL COMMENT '所属课程ID',
    `class_id`             bigint       NOT NULL COMMENT '所属班级ID',
    `indicator_first_id`   bigint       NULL     DEFAULT NULL COMMENT '一级技能指标ID',
    `indicator_second_id`  bigint       NULL     DEFAULT NULL COMMENT '二级技能指标ID',
    `requirement`          text         NULL     COMMENT '任务要求（富文本）',
    `deadline`             datetime     NULL     DEFAULT NULL COMMENT '截止时间',
    `auto_grade`           tinyint      NOT NULL DEFAULT 0 COMMENT '是否自动批改：0=手动, 1=自动AI批改',
    `submission_mode`                varchar(20) NOT NULL DEFAULT 'text' COMMENT '提交模式：text/image/audio/video/mixed',
    `ai_review_mode`                 tinyint     NOT NULL DEFAULT 0 COMMENT 'AI评阅模式：0=仅人工, 1=自动初评+人工复核',
    `required_attachment_types_json` varchar(500) NULL DEFAULT NULL COMMENT '允许/要求的附件类型配置（JSON数组）',
    `ai_rubric_json`                 text NULL     COMMENT 'AI评分rubric（JSON）',
    `ai_reference_json`              text NULL     COMMENT 'AI参考资料（JSON）',
    `ai_confidence_threshold`        decimal(5, 2) NULL DEFAULT 0.70 COMMENT 'AI初评置信度阈值',
    `pass_score`           int          NOT NULL DEFAULT 60 COMMENT '及格分数线',
    `score`                int          NOT NULL DEFAULT 100 COMMENT '满分值',
    `teacher_id`           bigint       NOT NULL COMMENT '发布教师ID',
    `status`               tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0=草稿, 1=已发布, 2=已截止, 3=已归档',
    `creator`              varchar(64)  NULL     DEFAULT '' COMMENT '创建者',
    `create_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`              varchar(64)  NULL     DEFAULT '' COMMENT '更新者',
    `update_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`              bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`            bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    INDEX `idx_course_class` (`course_id`, `class_id`),
    INDEX `idx_teacher` (`teacher_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实训任务';

-- ----------------------------
-- 2. edu_homework_record 任务提交记录
-- ----------------------------
CREATE TABLE `edu_homework_record` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `homework_id`      bigint        NOT NULL COMMENT '关联任务ID',
    `student_id`       bigint        NOT NULL COMMENT '学生ID',
    `content`          text          NULL     COMMENT '提交内容',
    `attachment_urls`  varchar(2000) NULL     DEFAULT NULL COMMENT '附件URL列表（JSON数组）',
    `submit_time`      datetime      NULL     DEFAULT NULL COMMENT '提交时间',
    `score`            int           NULL     DEFAULT NULL COMMENT '得分（0-100）',
    `comment`          text          NULL     COMMENT '教师/AI评语',
    `correct_type`     tinyint       NULL     DEFAULT NULL COMMENT '批改方式：1=手动, 2=AI',
    `correct_time`     datetime      NULL     DEFAULT NULL COMMENT '批改时间',
    `status`           tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0=未提交, 1=已提交待批改, 2=已批改, 3=已打回重做, 4=已重新提交',
    `creator`          varchar(64)   NULL     DEFAULT '' COMMENT '创建者',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`          varchar(64)   NULL     DEFAULT '' COMMENT '更新者',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`        bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    INDEX `idx_homework` (`homework_id`),
    INDEX `idx_student` (`student_id`),
    UNIQUE INDEX `uk_homework_student` (`homework_id`, `student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务提交记录';

-- ----------------------------
-- 2.1 edu_homework_ai_review AI初评草案
-- ----------------------------
CREATE TABLE `edu_homework_ai_review`
(
    `id`                 bigint      NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `homework_record_id` bigint      NOT NULL COMMENT '任务记录ID',
    `homework_id`        bigint      NOT NULL COMMENT '任务ID',
    `student_id`         bigint      NOT NULL COMMENT '学生ID',
    `status`             tinyint     NOT NULL DEFAULT 0 COMMENT '状态：0=排队中,1=处理中,2=已生成,3=失败,4=已采纳,5=已拒绝,6=已跳过',
    `submission_mode`    varchar(20) NOT NULL DEFAULT 'text' COMMENT '提交模式',
    `generation_token`   varchar(64) NOT NULL COMMENT '本次生成令牌',
    `source_submit_time` datetime NULL COMMENT '生成依据的提交时间',
    `model_id`           bigint NULL     DEFAULT NULL COMMENT '模型ID',
    `model_name`         varchar(100) NULL     DEFAULT NULL COMMENT '模型名称',
    `transcript`         text NULL     COMMENT '转写文本',
    `evidence_json`      longtext NULL     COMMENT '结构化证据JSON',
    `ai_score`           int NULL     DEFAULT NULL COMMENT 'AI草案分数',
    `ai_comment`         text NULL     COMMENT 'AI草案评语',
    `ai_confidence`      decimal(5, 2) NULL     DEFAULT NULL COMMENT 'AI置信度',
    `sub_scores_json`    longtext NULL     COMMENT '分项评分JSON',
    `risk_flags_json`    longtext NULL     COMMENT '风险标记JSON',
    `teacher_decision`   varchar(20) NULL     DEFAULT NULL COMMENT '教师处理：accept/adjust/reject',
    `teacher_score`      int NULL     DEFAULT NULL COMMENT '教师最终分数',
    `teacher_comment`    text NULL     COMMENT '教师最终评语',
    `raw_response`       longtext NULL     COMMENT '模型原始响应',
    `error_message`      varchar(1000) NULL     DEFAULT NULL COMMENT '错误信息',
    `creator`            varchar(64) NULL     DEFAULT '' COMMENT '创建者',
    `create_time`        datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`            varchar(64) NULL     DEFAULT '' COMMENT '更新者',
    `update_time`        datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`            bit(1)      NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`          bigint      NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_generation_token` (`generation_token`),
    INDEX                `idx_homework_record` (`homework_record_id`, `id`),
    INDEX                `idx_homework_student` (`homework_id`, `student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务AI初评草案';

-- ----------------------------
-- 3. edu_homework_draft 任务草稿
-- ----------------------------
CREATE TABLE `edu_homework_draft` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '草稿ID',
    `homework_id`      bigint        NOT NULL COMMENT '关联任务ID',
    `student_id`       bigint        NOT NULL COMMENT '学生ID',
    `content`          text          NULL     COMMENT '草稿内容',
    `attachment_urls`  varchar(2000) NULL     DEFAULT NULL COMMENT '附件URL列表（JSON数组）',
    `creator`          varchar(64)   NULL     DEFAULT '' COMMENT '创建者',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`          varchar(64)   NULL     DEFAULT '' COMMENT '更新者',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`        bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_homework_student` (`homework_id`, `student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务草稿';

-- ----------------------------
-- 3.1 edu_homework_ai_create_preview AI新建任务预览会话
-- ----------------------------
CREATE TABLE `edu_homework_ai_create_preview`
(
    `id`                  bigint        NOT NULL AUTO_INCREMENT COMMENT '预览会话ID',
    `client_request_id`   varchar(64)   NOT NULL COMMENT '客户端幂等请求编号',
    `task_status`         varchar(16)   NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
    `progress`            int           NOT NULL DEFAULT 0 COMMENT '任务进度百分比',
    `error_message`       varchar(1000) NULL COMMENT '失败原因',
    `retry_count`         int           NOT NULL DEFAULT 0 COMMENT '恢复重试次数',
    `start_time`          datetime      NULL COMMENT '开始执行时间',
    `finish_time`         datetime      NULL COMMENT '执行完成时间',
    `preview_id`          varchar(64)   NOT NULL COMMENT '预览编号',
    `teacher_id`          bigint        NOT NULL COMMENT '创建教师ID',
    `course_id`           bigint        NOT NULL COMMENT '课程ID',
    `indicator_first_id`  bigint NULL     DEFAULT NULL COMMENT '一级技能指标ID',
    `indicator_second_id` bigint NULL     DEFAULT NULL COMMENT '二级技能指标ID',
    `class_ids_json`      varchar(2000) NOT NULL COMMENT '班级ID列表JSON',
    `question_ids_json`   varchar(2000) NOT NULL COMMENT '抽中源题ID列表JSON',
    `items_json`          longtext      NOT NULL COMMENT '预览题目JSON',
    `homework_type`       tinyint       NOT NULL COMMENT '任务类型',
    `homework_level`      tinyint       NOT NULL COMMENT '任务等级',
    `deadline`            datetime      NOT NULL COMMENT '截止时间',
    `auto_grade`          tinyint       NOT NULL COMMENT '是否自动批改',
    `submission_mode`     varchar(20)   NOT NULL COMMENT '提交模式',
    `ai_review_mode`      tinyint       NOT NULL COMMENT 'AI评阅模式',
    `pass_score`          int           NOT NULL COMMENT '及格分数线',
    `score`               int           NOT NULL COMMENT '满分值',
    `expire_time`         datetime      NOT NULL COMMENT '过期时间',
    `confirmed`           bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否已确认创建',
    `creator`             varchar(64) NULL     DEFAULT '' COMMENT '创建者',
    `create_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`             varchar(64) NULL     DEFAULT '' COMMENT '更新者',
    `update_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_preview_id` (`preview_id`),
    UNIQUE INDEX `uk_teacher_client_request` (`teacher_id`, `client_request_id`),
    INDEX                 `idx_task_status_update` (`task_status`, `update_time`),
    INDEX                 `idx_teacher_expire` (`teacher_id`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI新建任务预览会话';

-- ----------------------------
-- 4. edu_question_bank 题库
-- ----------------------------
CREATE TABLE `edu_question_bank` (
    `id`                   bigint       NOT NULL AUTO_INCREMENT COMMENT '题目ID',
    `title`                varchar(200) NOT NULL COMMENT '题目标题',
    `requirement`          text         NULL     COMMENT '题目要求',
    `indicator_first_id`   bigint       NULL     DEFAULT NULL COMMENT '一级技能指标ID',
    `indicator_second_id`  bigint       NULL     DEFAULT NULL COMMENT '二级技能指标ID',
    `homework_level`       tinyint      NOT NULL COMMENT '任务等级：0=专业, 1=初级, 2=中级, 3=高级',
    `teacher_id`           bigint       NOT NULL COMMENT '创建教师ID（数据隔离键）',
    `creator`              varchar(64)  NULL     DEFAULT '' COMMENT '创建者',
    `create_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`              varchar(64)  NULL     DEFAULT '' COMMENT '更新者',
    `update_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`              bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`            bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    INDEX `idx_teacher` (`teacher_id`),
    INDEX `idx_indicator` (`indicator_first_id`, `indicator_second_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题库';
-- ----------------------------
-- 5. edu_class_course 班级课程关联
-- ----------------------------
CREATE TABLE `edu_class_course` (
    `id`           bigint      NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `class_id`     bigint      NOT NULL COMMENT '班级ID',
    `course_id`    bigint      NOT NULL COMMENT '课程ID',
    `creator`      varchar(64) NULL     DEFAULT '' COMMENT '创建者',
    `create_time`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`      varchar(64) NULL     DEFAULT '' COMMENT '更新者',
    `update_time`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      bit(1)      NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`    bigint      NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_class_course` (`class_id`, `course_id`),
    INDEX `idx_course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级课程关联';

-- ----------------------------
-- 7. edu_course_resource 课程资源
-- ----------------------------
CREATE TABLE `edu_course_resource` (
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT '资源ID',
    `resource_name`       varchar(200) NOT NULL COMMENT '资源名称',
    `course_id`           bigint       NOT NULL COMMENT '所属课程ID',
    `file_url`            varchar(500) NOT NULL COMMENT '文件URL',
    `file_type`           varchar(50)  NOT NULL COMMENT 'MIME类型',
    `file_size`           bigint       NOT NULL COMMENT '文件大小（字节）',
    `standard_audio_url`  varchar(500) NULL     DEFAULT NULL COMMENT '标准音频URL（音频文件自动生成）',
    `creator`             varchar(64)  NULL     DEFAULT '' COMMENT '创建者',
    `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`             varchar(64)  NULL     DEFAULT '' COMMENT '更新者',
    `update_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`           bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    INDEX `idx_course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程资源';

-- ----------------------------
-- 8. edu_student_ranking 学生排名历史
-- ----------------------------
CREATE TABLE `edu_student_ranking` (
    `id`             bigint        NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `student_id`     bigint        NOT NULL COMMENT '学生ID',
    `class_ranking`  int           NULL     DEFAULT NULL COMMENT '班级排名',
    `major_ranking`  int           NULL     DEFAULT NULL COMMENT '专业排名',
    `total_score`    decimal(5,2)  NULL     DEFAULT NULL COMMENT '综合得分',
    `record_date`    date          NOT NULL COMMENT '记录日期',
    `creator`        varchar(64)   NULL     DEFAULT '' COMMENT '创建者',
    `create_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`        varchar(64)   NULL     DEFAULT '' COMMENT '更新者',
    `update_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`      bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    INDEX `idx_student` (`student_id`),
    INDEX `idx_record_date` (`record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生排名历史';
