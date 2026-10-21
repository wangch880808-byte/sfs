-- =============================================
-- Sprint 8 留言表加status字段
-- =============================================

ALTER TABLE `edu_message` ADD COLUMN `status` tinyint NOT NULL DEFAULT 0 COMMENT '0=开启,1=关闭' AFTER `type`;
