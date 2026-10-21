-- =============================================
-- Sprint 14 统计分析写权限
-- =============================================

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `status`, `visible`,
 `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (5122, '统计配置写入', 'edu:statistics:manage', 3, 2, 5103, '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(),
        'admin', NOW(), b'0');

INSERT INTO `system_role_menu`
(`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (1, 5122, 'admin', NOW(), 'admin', NOW(), b'0');
