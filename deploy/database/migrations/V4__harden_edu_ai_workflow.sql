-- Preserve every AI draft and reject stale asynchronous results by generation token.
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

-- Binding is configuration only. Models and API keys are configured by an administrator.
CREATE TABLE `ai_business_model_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `business_code` varchar(64) NOT NULL COMMENT '固定业务码',
  `model_id` bigint NOT NULL COMMENT 'AI模型编号',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_business_code` (`business_code`, `deleted`),
  KEY `idx_ai_business_model` (`model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI业务模型绑定';
