-- V1.0.1.9 查询索引与统计信息：调整普通索引，并为状态类列建立直方图；保留业务数据、列定义和唯一约束。
-- 先建立全部替代索引，避免删除旧索引时查询失去可用索引。

SET @nus_v20_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'system_users'
              AND index_name = 'idx_system_users_type_status'),
    'SELECT 1',
    'ALTER TABLE `system_users` ADD INDEX `idx_system_users_type_status` (`user_type`, `status`, `deleted`, `id`)'
);
PREPARE nus_v20_stmt FROM @nus_v20_ddl;
EXECUTE nus_v20_stmt;
DEALLOCATE PREPARE nus_v20_stmt;

SET @nus_v20_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'system_users'
              AND index_name = 'idx_system_users_dept_type'),
    'SELECT 1',
    'ALTER TABLE `system_users` ADD INDEX `idx_system_users_dept_type` (`dept_id`, `user_type`, `status`, `deleted`)'
);
PREPARE nus_v20_stmt FROM @nus_v20_ddl;
EXECUTE nus_v20_stmt;
DEALLOCATE PREPARE nus_v20_stmt;

SET @nus_v20_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'system_users'
              AND index_name = 'idx_system_users_mobile'),
    'SELECT 1',
    'ALTER TABLE `system_users` ADD INDEX `idx_system_users_mobile` (`mobile`)'
);
PREPARE nus_v20_stmt FROM @nus_v20_ddl;
EXECUTE nus_v20_stmt;
DEALLOCATE PREPARE nus_v20_stmt;

SET @nus_v20_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'system_users'
              AND index_name = 'idx_system_users_email'),
    'SELECT 1',
    'ALTER TABLE `system_users` ADD INDEX `idx_system_users_email` (`email`)'
);
PREPARE nus_v20_stmt FROM @nus_v20_ddl;
EXECUTE nus_v20_stmt;
DEALLOCATE PREPARE nus_v20_stmt;

SET @nus_v20_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'system_user_role'
              AND index_name = 'idx_system_user_role_user'),
    'SELECT 1',
    'ALTER TABLE `system_user_role` ADD INDEX `idx_system_user_role_user` (`user_id`, `role_id`)'
);
PREPARE nus_v20_stmt FROM @nus_v20_ddl;
EXECUTE nus_v20_stmt;
DEALLOCATE PREPARE nus_v20_stmt;

SET @nus_v20_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'system_user_role'
              AND index_name = 'idx_system_user_role_role'),
    'SELECT 1',
    'ALTER TABLE `system_user_role` ADD INDEX `idx_system_user_role_role` (`role_id`)'
);
PREPARE nus_v20_stmt FROM @nus_v20_ddl;
EXECUTE nus_v20_stmt;
DEALLOCATE PREPARE nus_v20_stmt;

SET @nus_v20_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'system_user_profile'
              AND index_name = 'idx_system_user_profile_type_grade'),
    'SELECT 1',
    'ALTER TABLE `system_user_profile` ADD INDEX `idx_system_user_profile_type_grade` (`user_type`, `grade_year`, `user_id`)'
);
PREPARE nus_v20_stmt FROM @nus_v20_ddl;
EXECUTE nus_v20_stmt;
DEALLOCATE PREPARE nus_v20_stmt;

SET @nus_v20_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'infra_config'
              AND index_name = 'idx_infra_config_key'),
    'SELECT 1',
    'ALTER TABLE `infra_config` ADD INDEX `idx_infra_config_key` (`config_key`)'
);
PREPARE nus_v20_stmt FROM @nus_v20_ddl;
EXECUTE nus_v20_stmt;
DEALLOCATE PREPARE nus_v20_stmt;

SET @nus_v20_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'edu_homework_record'
              AND index_name = 'idx_edu_homework_record_hw_status_score'),
    'SELECT 1',
    'ALTER TABLE `edu_homework_record` ADD INDEX `idx_edu_homework_record_hw_status_score` (`homework_id`, `status`, `score`)'
);
PREPARE nus_v20_stmt FROM @nus_v20_ddl;
EXECUTE nus_v20_stmt;
DEALLOCATE PREPARE nus_v20_stmt;

SET @nus_v20_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'edu_homework_record'
              AND index_name = 'idx_edu_homework_record_status_hw'),
    'SELECT 1',
    'ALTER TABLE `edu_homework_record` ADD INDEX `idx_edu_homework_record_status_hw` (`status`, `homework_id`)'
);
PREPARE nus_v20_stmt FROM @nus_v20_ddl;
EXECUTE nus_v20_stmt;
DEALLOCATE PREPARE nus_v20_stmt;

SET @nus_v20_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'edu_homework_record'
              AND index_name = 'idx_edu_homework_record_student_status'),
    'SELECT 1',
    'ALTER TABLE `edu_homework_record` ADD INDEX `idx_edu_homework_record_student_status` (`student_id`, `status`, `correct_time`)'
);
PREPARE nus_v20_stmt FROM @nus_v20_ddl;
EXECUTE nus_v20_stmt;
DEALLOCATE PREPARE nus_v20_stmt;

SET @nus_v20_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'edu_homework'
              AND index_name = 'idx_edu_homework_teacher_status'),
    'SELECT 1',
    'ALTER TABLE `edu_homework` ADD INDEX `idx_edu_homework_teacher_status` (`teacher_id`, `status`, `id`)'
);
PREPARE nus_v20_stmt FROM @nus_v20_ddl;
EXECUTE nus_v20_stmt;
DEALLOCATE PREPARE nus_v20_stmt;

SET @nus_v20_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'edu_message'
              AND index_name = 'idx_edu_message_type_read'),
    'SELECT 1',
    'ALTER TABLE `edu_message` ADD INDEX `idx_edu_message_type_read` (`type`, `read_status`, `class_id`, `course_id`)'
);
PREPARE nus_v20_stmt FROM @nus_v20_ddl;
EXECUTE nus_v20_stmt;
DEALLOCATE PREPARE nus_v20_stmt;

-- 替代索引的左前缀完整覆盖旧索引；独立判断可兼容部分执行后的重跑。

SET @nus_v20_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'edu_homework_record'
              AND index_name = 'idx_edu_homework_record_homework_status'),
    'ALTER TABLE `edu_homework_record` DROP INDEX `idx_edu_homework_record_homework_status`',
    'SELECT 1'
);
PREPARE nus_v20_stmt FROM @nus_v20_ddl;
EXECUTE nus_v20_stmt;
DEALLOCATE PREPARE nus_v20_stmt;

SET @nus_v20_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'edu_homework_record'
              AND index_name = 'idx_student'),
    'ALTER TABLE `edu_homework_record` DROP INDEX `idx_student`',
    'SELECT 1'
);
PREPARE nus_v20_stmt FROM @nus_v20_ddl;
EXECUTE nus_v20_stmt;
DEALLOCATE PREPARE nus_v20_stmt;

SET @nus_v20_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'edu_homework'
              AND index_name = 'idx_teacher'),
    'ALTER TABLE `edu_homework` DROP INDEX `idx_teacher`',
    'SELECT 1'
);
PREPARE nus_v20_stmt FROM @nus_v20_ddl;
EXECUTE nus_v20_stmt;
DEALLOCATE PREPARE nus_v20_stmt;

SET @nus_v20_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'edu_message'
              AND index_name = 'idx_type'),
    'ALTER TABLE `edu_message` DROP INDEX `idx_type`',
    'SELECT 1'
);
PREPARE nus_v20_stmt FROM @nus_v20_ddl;
EXECUTE nus_v20_stmt;
DEALLOCATE PREPARE nus_v20_stmt;

-- 状态、删除标记等少数取值的列建立直方图：没有直方图时优化器按 5% 估算命中率（实际接近 100%），
-- 会从全校班课开始连接，学生任务、教师任务、教师看板等查询扫描全部任务。直方图只是统计信息，不改数据，可重复执行。
ANALYZE TABLE system_users UPDATE HISTOGRAM ON status, user_type, deleted;
ANALYZE TABLE system_user_role UPDATE HISTOGRAM ON deleted;
ANALYZE TABLE system_user_profile UPDATE HISTOGRAM ON user_type, deleted;
ANALYZE TABLE system_dept UPDATE HISTOGRAM ON status, deleted;
ANALYZE TABLE system_notify_message UPDATE HISTOGRAM ON read_status, user_type, deleted;
ANALYZE TABLE capacity_course UPDATE HISTOGRAM ON status, deleted;
ANALYZE TABLE capacity_course_member UPDATE HISTOGRAM ON deleted;
ANALYZE TABLE edu_class UPDATE HISTOGRAM ON status, deleted;
ANALYZE TABLE edu_class_student UPDATE HISTOGRAM ON status, deleted;
ANALYZE TABLE edu_class_course UPDATE HISTOGRAM ON status, deleted;
ANALYZE TABLE edu_homework UPDATE HISTOGRAM ON status, deleted;
ANALYZE TABLE edu_homework_assignee UPDATE HISTOGRAM ON deleted;
ANALYZE TABLE edu_homework_record UPDATE HISTOGRAM ON status, deleted;
ANALYZE TABLE edu_message UPDATE HISTOGRAM ON type, read_status, deleted;
