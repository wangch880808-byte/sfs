CREATE TABLE IF NOT EXISTS `ai_business_model_binding` (
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
