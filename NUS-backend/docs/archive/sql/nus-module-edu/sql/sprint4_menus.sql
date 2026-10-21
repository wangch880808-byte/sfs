-- =============================================
-- Sprint 4 统计分析菜单 + 权限
-- 菜单ID从5103开始（Sprint 3最大5102）
-- =============================================

-- ========== 1. 统计分析菜单页面（挂在能力培养5055下） ==========
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5103, '统计分析', 'edu:statistics:query', 2, 4, 5055, 'statistics', 'ep:data-line', 'edu/statistics/index', 'EduStatistics', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 2. 学生统计列表（隐藏路由） ==========
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5104, '学生统计列表', 'edu:statistics:query', 2, 10, 5055, 'statistics/student-list', '#', 'edu/statistics/student-list', 'EduStatisticsStudentList', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 3. 按钮权限 ==========
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5105, '统计查询', 'edu:statistics:query', 3, 1, 5103, '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 4. 给超级管理员分配菜单 ==========
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(1, 5103, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5104, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5105, 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 5. 给教师角色(id=161)分配菜单 ==========
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(161, 5103, 'admin', NOW(), 'admin', NOW(), b'0'),
(161, 5104, 'admin', NOW(), 'admin', NOW(), b'0'),
(161, 5105, 'admin', NOW(), 'admin', NOW(), b'0');
