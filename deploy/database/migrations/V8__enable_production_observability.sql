-- Enable the three production monitoring entries without inserting business data.
UPDATE `infra_config`
SET `value` = '/druid/index.html', `remark` = 'Druid SQL and connection-pool monitoring',
    `updater` = '1', `update_time` = CURRENT_TIMESTAMP
WHERE `config_key` = 'url.druid' AND `deleted` = b'0';

UPDATE `infra_config`
SET `value` = '/skywalking/', `remark` = 'SkyWalking tracing console',
    `updater` = '1', `update_time` = CURRENT_TIMESTAMP
WHERE `config_key` = 'url.skywalking' AND `deleted` = b'0';

UPDATE `infra_config`
SET `value` = '/admin/applications', `remark` = 'Spring Boot Admin JVM monitoring',
    `updater` = '1', `update_time` = CURRENT_TIMESTAMP
WHERE `config_key` = 'url.spring-boot-admin' AND `deleted` = b'0';

UPDATE `system_menu`
SET `name` = 'SQL与连接池监控', `status` = 0, `updater` = '1', `update_time` = CURRENT_TIMESTAMP
WHERE `id` = 111 AND `deleted` = b'0';

UPDATE `system_menu`
SET `status` = 0, `updater` = '1', `update_time` = CURRENT_TIMESTAMP
WHERE `id` IN (112, 1077) AND `deleted` = b'0';
