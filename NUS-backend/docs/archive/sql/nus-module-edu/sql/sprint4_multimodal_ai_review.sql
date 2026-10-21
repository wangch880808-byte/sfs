-- Sprint 4: 多模态作业配置 + AI 初评草案

ALTER TABLE `edu_homework`
    ADD COLUMN `submission_mode` varchar(20) NOT NULL DEFAULT 'text' COMMENT '提交模式：text/image/audio/video/mixed' AFTER `auto_grade`,
    ADD COLUMN `ai_review_mode` tinyint NOT NULL DEFAULT 0 COMMENT 'AI评阅模式：0=仅人工,1=自动初评+人工复核' AFTER `submission_mode`,
    ADD COLUMN `required_attachment_types_json` varchar(500) NULL DEFAULT NULL COMMENT '允许/要求的附件类型配置（JSON数组）' AFTER `ai_review_mode`,
    ADD COLUMN `ai_rubric_json` text NULL COMMENT 'AI评分rubric（JSON）' AFTER `required_attachment_types_json`,
    ADD COLUMN `ai_reference_json` text NULL COMMENT 'AI参考资料（JSON）' AFTER `ai_rubric_json`,
    ADD COLUMN `ai_confidence_threshold` decimal(5,2) NULL DEFAULT 0.70 COMMENT 'AI初评置信度阈值' AFTER `ai_reference_json`;

CREATE TABLE IF NOT EXISTS `edu_homework_ai_review`
(
    `id`
    bigint
    NOT
    NULL
    AUTO_INCREMENT
    COMMENT
    'ID',
    `homework_record_id`
    bigint
    NOT
    NULL
    COMMENT
    '任务记录ID',
    `homework_id`
    bigint
    NOT
    NULL
    COMMENT
    '任务ID',
    `student_id`
    bigint
    NOT
    NULL
    COMMENT
    '学生ID',
    `status`
    tinyint
    NOT
    NULL
    DEFAULT
    0
    COMMENT
    '状态：0=排队中,1=处理中,2=已生成,3=失败,4=已采纳,5=已拒绝,6=已跳过',
    `submission_mode`
    varchar
(
    20
) NOT NULL DEFAULT 'text' COMMENT '提交模式',
    `model_id` bigint NULL DEFAULT NULL COMMENT '模型ID',
    `model_name` varchar
(
    100
) NULL DEFAULT NULL COMMENT '模型名称',
    `transcript` text NULL COMMENT '转写文本',
    `evidence_json` longtext NULL COMMENT '结构化证据JSON',
    `ai_score` int NULL DEFAULT NULL COMMENT 'AI草案分数',
    `ai_comment` text NULL COMMENT 'AI草案评语',
    `ai_confidence` decimal
(
    5,
    2
) NULL DEFAULT NULL COMMENT 'AI置信度',
    `sub_scores_json` longtext NULL COMMENT '分项评分JSON',
    `risk_flags_json` longtext NULL COMMENT '风险标记JSON',
    `teacher_decision` varchar
(
    20
) NULL DEFAULT NULL COMMENT '教师处理：accept/adjust/reject',
    `teacher_score` int NULL DEFAULT NULL COMMENT '教师最终分数',
    `teacher_comment` text NULL COMMENT '教师最终评语',
    `raw_response` longtext NULL COMMENT '模型原始响应',
    `error_message` varchar
(
    1000
) NULL DEFAULT NULL COMMENT '错误信息',
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
    `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY
(
    `id`
),
    UNIQUE KEY `uk_homework_record`
(
    `homework_record_id`
),
    KEY `idx_homework_student`
(
    `homework_id`,
    `student_id`
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE =utf8mb4_unicode_ci COMMENT='任务AI初评草案';
