-- Sprint 16: common P0 data exchange permission rollback

DELETE `role_menu`
FROM `system_role_menu` AS `role_menu`
INNER JOIN `system_menu` AS `menu` ON `menu`.`id` = `role_menu`.`menu_id`
WHERE `menu`.`permission` = 'edu:data-exchange:import';

DELETE FROM `system_menu`
WHERE `permission` = 'edu:data-exchange:import';
