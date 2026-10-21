-- ============================================================
-- Sprint 16: common P0 data exchange permission
-- Business handlers must additionally enforce their own data scope.
-- ============================================================

SET @edu_data_exchange_menu_id := (
  SELECT `id`
  FROM `system_menu`
  WHERE `permission` = 'edu:data-exchange:import' AND `deleted` = b'0'
  ORDER BY `id`
  LIMIT 1
);

SET @edu_data_exchange_menu_id := COALESCE(
  @edu_data_exchange_menu_id,
  (SELECT COALESCE(MAX(`id`), 0) + 1 FROM `system_menu`)
);

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `status`, `visible`,
 `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT @edu_data_exchange_menu_id, '数据导入导出', 'edu:data-exchange:import', 3, 99, 5047, '', '', '', 0, b'1', b'1', b'1',
       'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `system_menu`
  WHERE `permission` = 'edu:data-exchange:import' AND `deleted` = b'0'
);

SET @edu_data_exchange_menu_id := (
  SELECT `id`
  FROM `system_menu`
  WHERE `permission` = 'edu:data-exchange:import' AND `deleted` = b'0'
  ORDER BY `id`
  LIMIT 1
);

INSERT INTO `system_role_menu`
(`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1, @edu_data_exchange_menu_id, 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @edu_data_exchange_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu`
    WHERE `role_id` = 1 AND `menu_id` = @edu_data_exchange_menu_id AND `deleted` = b'0'
  );

INSERT INTO `system_role_menu`
(`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 161, @edu_data_exchange_menu_id, 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @edu_data_exchange_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu`
    WHERE `role_id` = 161 AND `menu_id` = @edu_data_exchange_menu_id AND `deleted` = b'0'
  );
