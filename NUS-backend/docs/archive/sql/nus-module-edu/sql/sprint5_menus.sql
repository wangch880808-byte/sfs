-- =============================================
-- Sprint 5 题库管理 + 留言互动 菜单 + 权限
-- 菜单ID从5106开始（Sprint 4最大5105）
-- =============================================

-- ========== 1. 题库管理菜单（挂在实训任务5075下） ==========
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5106, '题库管理', 'edu:question-bank:query', 2, 4, 5075, 'question-bank', 'ep:collection', 'edu/questionbank/index', 'EduQuestionBank', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 2. 留言互动菜单（挂在教务组织5047下） ==========
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5107, '留言互动', 'edu:message:query', 2, 3, 5047, 'message', 'ep:chat-line-round', 'edu/message/index', 'EduMessage', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 3. 按钮权限 ==========
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5108, '题库管理', 'edu:question-bank:manage', 3, 1, 5106, '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5109, '留言互动', 'edu:message:manage', 3, 1, 5107, '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 4. 给超级管理员分配菜单 ==========
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(1, 5106, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5107, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5108, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5109, 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 5. 给教师角色(id=161)分配菜单 ==========
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(161, 5106, 'admin', NOW(), 'admin', NOW(), b'0'),
(161, 5107, 'admin', NOW(), 'admin', NOW(), b'0'),
(161, 5108, 'admin', NOW(), 'admin', NOW(), b'0'),
(161, 5109, 'admin', NOW(), 'admin', NOW(), b'0');
