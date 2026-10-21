-- 公共课审批能力升级
-- 1. 非公共课：public_audit_status = 0（无需审批）
-- 2. 新申请公共课：public_audit_status = 1（待审批）
-- 3. 审批通过公共课：public_audit_status = 2（审批通过）
-- 4. 审批驳回公共课：public_audit_status = 3（审批驳回）

ALTER TABLE `capacity_course`
    ADD COLUMN `public_audit_status` TINYINT NOT NULL DEFAULT 0 COMMENT '公共课审批状态（0无需审批 1待审批 2审批通过 3审批驳回）' AFTER `is_public_course`,
    ADD COLUMN `public_audit_remark` VARCHAR(500) NULL DEFAULT NULL COMMENT '公共课审批意见' AFTER `public_audit_status`,
    ADD COLUMN `public_audit_time` DATETIME NULL DEFAULT NULL COMMENT '公共课审批时间' AFTER `public_audit_remark`,
    ADD COLUMN `public_audit_user_id` BIGINT NULL DEFAULT NULL COMMENT '公共课审批人ID' AFTER `public_audit_time`;

-- 历史数据回填：
-- 已存在的公共课默认视为审批通过，避免升级后原公共课全部不可见；
-- 其余专属课程默认为无需审批。
UPDATE `capacity_course`
SET `public_audit_status` = CASE
                                WHEN `is_public_course` = 0 THEN 2
                                ELSE 0
    END
WHERE `deleted` = b'0';
