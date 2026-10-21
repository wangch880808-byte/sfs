-- AI 初评草案保留生成历史，并使用生成令牌阻止过期异步结果覆盖最新状态。
ALTER TABLE `edu_homework_ai_review`
    DROP INDEX `uk_homework_record`,
    ADD COLUMN `generation_token` varchar(64) NULL COMMENT '本次生成令牌' AFTER `submission_mode`,
    ADD COLUMN `source_submit_time` datetime NULL COMMENT '生成依据的提交时间' AFTER `generation_token`,
    ADD INDEX `idx_homework_record` (`homework_record_id`, `id`),
    ADD UNIQUE INDEX `uk_generation_token` (`generation_token`);

UPDATE `edu_homework_ai_review`
SET `generation_token` = CONCAT('legacy-', `id`),
    `source_submit_time` = COALESCE(`source_submit_time`, `create_time`)
WHERE `generation_token` IS NULL;

ALTER TABLE `edu_homework_ai_review`
    MODIFY COLUMN `generation_token` varchar(64) NOT NULL COMMENT '本次生成令牌';
