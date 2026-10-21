-- 学期自动更新开关。关闭后保留管理员手动设置。
INSERT INTO `infra_config`
(`category`, `type`, `name`, `config_key`, `value`, `visible`, `remark`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'edu', 2, '学期自动更新', 'edu.current.semester.auto-enabled', 'true', b'1',
       '按自然月自动维护当前学期', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `infra_config`
    WHERE `config_key` = 'edu.current.semester.auto-enabled' AND `deleted` = b'0'
);

-- 每日校准一次；任务也可在任务中心手动执行。
INSERT INTO `infra_job`
(`name`, `status`, `handler_name`, `handler_param`, `cron_expression`, `retry_count`, `retry_interval`,
 `monitor_timeout`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '当前学期自动更新', 1, 'eduSemesterAutoUpdateJob', '', '0 5 0 * * ?', 1, 60000,
       60000, 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `infra_job`
    WHERE `handler_name` = 'eduSemesterAutoUpdateJob' AND `deleted` = b'0'
);
