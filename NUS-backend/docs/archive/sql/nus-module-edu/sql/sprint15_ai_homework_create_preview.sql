-- AI新建任务预览会话
CREATE TABLE IF NOT EXISTS `edu_homework_ai_create_preview`
(
    `id`
    bigint
    NOT
    NULL
    AUTO_INCREMENT
    COMMENT
    '预览会话ID',
    `client_request_id` varchar(64) NOT NULL COMMENT '客户端幂等请求编号',
    `task_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
    `progress` int NOT NULL DEFAULT 0 COMMENT '任务进度百分比',
    `error_message` varchar(1000) NULL COMMENT '失败原因',
    `retry_count` int NOT NULL DEFAULT 0 COMMENT '恢复重试次数',
    `start_time` datetime NULL COMMENT '开始执行时间',
    `finish_time` datetime NULL COMMENT '执行完成时间',
    `preview_id`
    varchar
(
    64
) NOT NULL COMMENT '预览编号',
    `teacher_id` bigint NOT NULL COMMENT '创建教师ID',
    `course_id` bigint NOT NULL COMMENT '课程ID',
    `indicator_first_id` bigint NULL DEFAULT NULL COMMENT '一级技能指标ID',
    `indicator_second_id` bigint NULL DEFAULT NULL COMMENT '二级技能指标ID',
    `class_ids_json` varchar
(
    2000
) NOT NULL COMMENT '班级ID列表JSON',
    `question_ids_json` varchar
(
    2000
) NOT NULL COMMENT '抽中源题ID列表JSON',
    `items_json` longtext NOT NULL COMMENT '预览题目JSON',
    `homework_type` tinyint NOT NULL COMMENT '任务类型',
    `homework_level` tinyint NOT NULL COMMENT '任务等级',
    `deadline` datetime NOT NULL COMMENT '截止时间',
    `auto_grade` tinyint NOT NULL COMMENT '是否自动批改',
    `submission_mode` varchar
(
    20
) NOT NULL COMMENT '提交模式',
    `ai_review_mode` tinyint NOT NULL COMMENT 'AI评阅模式',
    `pass_score` int NOT NULL COMMENT '及格分数线',
    `score` int NOT NULL COMMENT '满分值',
    `expire_time` datetime NOT NULL COMMENT '过期时间',
    `confirmed` bit
(
    1
) NOT NULL DEFAULT b'0' COMMENT '是否已确认创建',
    `creator` varchar
(
    64
) NULL DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar
(
    64
) NULL DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit
(
    1
) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY
(
    `id`
),
    UNIQUE INDEX `uk_preview_id`
(
    `preview_id`
),
    UNIQUE INDEX `uk_teacher_client_request` (`teacher_id`, `client_request_id`),
    INDEX `idx_task_status_update` (`task_status`, `update_time`),
    INDEX `idx_teacher_expire`
(
    `teacher_id`,
    `expire_time`
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE =utf8mb4_unicode_ci COMMENT='AI新建任务预览会话';
