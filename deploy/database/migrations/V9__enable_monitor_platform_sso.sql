-- Protect all production monitoring entries with the platform permission model.
-- No business records are inserted by this migration.
UPDATE `system_menu`
SET `permission` = 'infra:monitor:access', `updater` = '1', `update_time` = CURRENT_TIMESTAMP
WHERE `id` IN (111, 112, 1077) AND `deleted` = b'0';

UPDATE `infra_config`
SET `value` = '/druid/index.html', `updater` = '1', `update_time` = CURRENT_TIMESTAMP
WHERE `config_key` = 'url.druid' AND `deleted` = b'0';

UPDATE `infra_config`
SET `value` = '/admin/applications', `updater` = '1', `update_time` = CURRENT_TIMESTAMP
WHERE `config_key` = 'url.spring-boot-admin' AND `deleted` = b'0';
