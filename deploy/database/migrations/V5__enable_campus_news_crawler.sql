-- Register the campus-news crawler without adding any article or other business data.
INSERT INTO `infra_job`
(`name`, `status`, `handler_name`, `handler_param`, `cron_expression`, `retry_count`, `retry_interval`,
 `monitor_timeout`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '校园资讯定时抓取', 1, 'eduNewsCrawlerJob', '', '0 15 7,17 * * ?', 2, 60000,
       300000, 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `infra_job`
    WHERE `handler_name` = 'eduNewsCrawlerJob' AND `deleted` = b'0'
);
