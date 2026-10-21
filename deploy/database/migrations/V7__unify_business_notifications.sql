-- All teaching notifications use the persisted system inbox consumed by the header bell.
ALTER TABLE `system_notify_message`
  MODIFY COLUMN `template_params` varchar(2048) NOT NULL COMMENT '模版参数';

-- The current baseline already contains this index, while older deployed databases do not.
SET @add_notify_unread_index = IF(
  EXISTS(
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'system_notify_message'
      AND index_name = 'idx_notify_user_unread'
  ),
  'SELECT 1',
  'ALTER TABLE `system_notify_message` ADD INDEX `idx_notify_user_unread` (`user_id`, `user_type`, `read_status`, `id`)'
);
PREPARE add_notify_unread_index_stmt FROM @add_notify_unread_index;
EXECUTE add_notify_unread_index_stmt;
DEALLOCATE PREPARE add_notify_unread_index_stmt;

INSERT INTO `system_notify_template`
(`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '学生被加入班级', 'edu.student.class.joined', '师砺平台',
       '你已被加入班级：{className}。', 1, '["className"]', 0,
       '教学业务站内信模板', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` WHERE `code` = 'edu.student.class.joined' AND `deleted` = b'0');

INSERT INTO `system_notify_template`
(`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '学生被分配课程', 'edu.student.course.assigned', '师砺平台',
       '你所在班级 {className} 已分配课程：{courseName}。', 1, '["className","courseName"]', 0,
       '教学业务站内信模板', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` WHERE `code` = 'edu.student.course.assigned' AND `deleted` = b'0');

INSERT INTO `system_notify_template`
(`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '新作业发布', 'edu.student.homework.created', '师砺平台',
       '课程 {courseName} 发布了新作业：{homeworkName}，截止时间：{deadline}。', 1,
       '["courseName","homeworkName","deadline"]', 0, '教学业务站内信模板',
       'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` WHERE `code` = 'edu.student.homework.created' AND `deleted` = b'0');

INSERT INTO `system_notify_template`
(`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '作业变更', 'edu.student.homework.updated', '师砺平台',
       '课程 {courseName} 的作业 {homeworkName} 已更新，请及时查看，截止时间：{deadline}。', 1,
       '["courseName","homeworkName","deadline"]', 0, '教学业务站内信模板',
       'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` WHERE `code` = 'edu.student.homework.updated' AND `deleted` = b'0');

INSERT INTO `system_notify_template`
(`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '作业批改完成', 'edu.student.homework.corrected', '师砺平台',
       '作业 {homeworkName} 已批改完成，得分：{score}，评语：{comment}。', 1,
       '["homeworkName","score","comment"]', 0, '教学业务站内信模板',
       'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` WHERE `code` = 'edu.student.homework.corrected' AND `deleted` = b'0');

INSERT INTO `system_notify_template`
(`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '作业打回重做', 'edu.student.homework.redo', '师砺平台',
       '作业 {homeworkName} 已被打回重做，请根据要求重新提交。', 1,
       '["homeworkName"]', 0, '教学业务站内信模板',
       'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` WHERE `code` = 'edu.student.homework.redo' AND `deleted` = b'0');

INSERT INTO `system_notify_template`
(`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '作业催交', 'edu.student.homework.remind', '师砺平台',
       '作业 {homeworkName} 尚未提交，请在 {deadline} 前完成提交。', 1,
       '["homeworkName","deadline"]', 0, '教学业务站内信模板',
       'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` WHERE `code` = 'edu.student.homework.remind' AND `deleted` = b'0');

INSERT INTO `system_notify_template`
(`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '教师回复留言', 'edu.student.message.reply', '师砺平台',
       '课程 {courseName} 收到教师回复：{content}', 1,
       '["courseName","content"]', 0, '教学业务站内信模板',
       'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` WHERE `code` = 'edu.student.message.reply' AND `deleted` = b'0');

INSERT INTO `system_notify_template`
(`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '学生发起留言', 'edu.teacher.message.created', '师砺平台',
       '学生 {studentName} 在课程 {courseName} 发起留言：{content}', 1,
       '["studentName","courseName","content"]', 0, '教学业务站内信模板',
       'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` WHERE `code` = 'edu.teacher.message.created' AND `deleted` = b'0');

INSERT INTO `system_notify_template`
(`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '公共课待审批', 'edu.admin.public-course.pending', '师砺平台',
       '公共课 {courseName} 已提交审批，提交教师：{teacherName}。', 1,
       '["courseName","teacherName"]', 0, '教学业务站内信模板',
       'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` WHERE `code` = 'edu.admin.public-course.pending' AND `deleted` = b'0');

INSERT INTO `system_notify_template`
(`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '公共课审批结果', 'edu.teacher.public-course.audit-result', '师砺平台',
       '公共课 {courseName} 审批结果：{auditResult}，审批意见：{auditRemark}。', 1,
       '["courseName","auditResult","auditRemark"]', 0, '教学业务站内信模板',
       'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` WHERE `code` = 'edu.teacher.public-course.audit-result' AND `deleted` = b'0');

-- This disconnected table had no active producer and maintained a second unread state.
DROP TABLE IF EXISTS `edu_student_notification`;
