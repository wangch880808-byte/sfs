-- =============================================
-- Sprint 6 校园资讯 + 证书配置 菜单 + 权限
-- 菜单ID从5110开始（Sprint 5最大5109）
-- =============================================

-- ========== 1. 校园资讯菜单（挂在教务组织5047下） ==========
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5110, '校园资讯', 'edu:news:query', 2, 4, 5047, 'news', 'ep:document', 'edu/news/index', 'EduNews', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 2. 证书配置菜单（挂在能力培养5055下） ==========
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5111, '证书配置', 'edu:certificate:query', 2, 5, 5055, 'certificate', 'ep:trophy', 'edu/certificate/index', 'EduCertificate', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 3. 按钮权限 ==========
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5112, '资讯管理', 'edu:news:manage', 3, 1, 5110, '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5113, '证书配置', 'edu:certificate:manage', 3, 1, 5111, '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 4. 给超级管理员分配菜单 ==========
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(1, 5110, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5111, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5112, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5113, 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 5. 给教师角色(id=161)分配菜单 ==========
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(161, 5110, 'admin', NOW(), 'admin', NOW(), b'0'),
(161, 5111, 'admin', NOW(), 'admin', NOW(), b'0'),
(161, 5112, 'admin', NOW(), 'admin', NOW(), b'0'),
(161, 5113, 'admin', NOW(), 'admin', NOW(), b'0');
