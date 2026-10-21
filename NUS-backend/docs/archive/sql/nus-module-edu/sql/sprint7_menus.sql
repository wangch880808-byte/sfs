-- =============================================
-- Sprint 7 统计分析扩展菜单
-- 菜单ID从5114开始（Sprint 6最大5113）
-- =============================================

-- ========== 1. 教学过程分析（挂在能力培养5055下） ==========
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5114, '教学过程分析', 'edu:statistics:query', 2, 6, 5055, 'teaching-process', 'ep:trend-charts', 'edu/statistics/teaching-process', 'TeachingProcess', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 2. 教学问题研判（挂在能力培养5055下） ==========
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5115, '教学问题研判', 'edu:statistics:query', 2, 7, 5055, 'teaching-issue', 'ep:warning', 'edu/statistics/teaching-issue', 'TeachingIssue', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 3. 单项能力评测（挂在能力培养5055下） ==========
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5116, '单项能力评测', 'edu:statistics:query', 2, 8, 5055, 'skill-assessment', 'ep:aim', 'edu/statistics/skill-assessment', 'SkillAssessment', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 4. 给超级管理员分配菜单 ==========
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(1, 5114, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5115, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5116, 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 5. 给教师角色(id=161)分配菜单 ==========
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(161, 5114, 'admin', NOW(), 'admin', NOW(), b'0'),
(161, 5115, 'admin', NOW(), 'admin', NOW(), b'0'),
(161, 5116, 'admin', NOW(), 'admin', NOW(), b'0');

-- =============================================
-- Sprint 9 看板菜单（管理员看板 + 教师工作台）
-- 菜单ID 5117-5118
-- =============================================

-- ========== 6. 管理员看板（挂在能力培养5055下） ==========
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5117, '管理员看板', 'edu:statistics:query', 2, 9, 5055, 'admin-dashboard', 'ep:data-board', 'edu/statistics/admin-dashboard', 'AdminDashboard', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 7. 教师工作台（挂在能力培养5055下） ==========
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5118, '教师工作台', 'edu:statistics:query', 2, 10, 5055, 'teacher-dashboard', 'ep:monitor', 'edu/statistics/teacher-dashboard', 'TeacherDashboard', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 8. 给超级管理员分配看板菜单 ==========
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(1, 5117, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5118, 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 9. 给教师角色(id=161)分配看板菜单 ==========
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(161, 5117, 'admin', NOW(), 'admin', NOW(), b'0'),
(161, 5118, 'admin', NOW(), 'admin', NOW(), b'0');
