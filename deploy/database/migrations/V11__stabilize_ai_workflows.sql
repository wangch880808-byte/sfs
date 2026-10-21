ALTER TABLE `edu_homework_ai_create_preview`
    ADD COLUMN `client_request_id` varchar(64) NULL COMMENT '客户端幂等请求编号' AFTER `id`,
    ADD COLUMN `task_status` varchar(16) NOT NULL DEFAULT 'SUCCEEDED' COMMENT '任务状态' AFTER `client_request_id`,
    ADD COLUMN `progress` int NOT NULL DEFAULT 100 COMMENT '任务进度百分比' AFTER `task_status`,
    ADD COLUMN `error_message` varchar(1000) NULL COMMENT '失败原因' AFTER `progress`,
    ADD COLUMN `retry_count` int NOT NULL DEFAULT 0 COMMENT '恢复重试次数' AFTER `error_message`,
    ADD COLUMN `start_time` datetime NULL COMMENT '开始执行时间' AFTER `retry_count`,
    ADD COLUMN `finish_time` datetime NULL COMMENT '执行完成时间' AFTER `start_time`;

UPDATE `edu_homework_ai_create_preview`
SET `client_request_id` = CONCAT('legacy-', `id`),
    `task_status` = 'SUCCEEDED',
    `progress` = 100,
    `finish_time` = `update_time`
WHERE `client_request_id` IS NULL;

ALTER TABLE `edu_homework_ai_create_preview`
    MODIFY COLUMN `client_request_id` varchar(64) NOT NULL COMMENT '客户端幂等请求编号',
    ADD UNIQUE INDEX `uk_teacher_client_request` (`teacher_id`, `client_request_id`),
    ADD INDEX `idx_task_status_update` (`task_status`, `update_time`);
