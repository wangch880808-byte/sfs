-- =============================================
-- Sprint 3 AI评阅菜单 + 权限
-- 菜单ID从5096开始（Sprint 2最大5095）
-- =============================================

-- ========== 1. AI评阅按钮权限父节点（隐藏目录，不显示在菜单中） ==========
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5096, 'AI评阅', '', 1, 3, 5075, 'ai-review', 'ep:magic-stick', '', '', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 2. AI评阅按钮权限 ==========
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5097, 'AI批改', 'edu:ai-review:correct', 3, 1, 5096, '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5098, 'AI分析', 'edu:ai-review:analysis', 3, 2, 5096, '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5099, 'AI对话', 'edu:ai-review:chat', 3, 3, 5096, '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5100, 'AI生成', 'edu:ai-review:generate', 3, 4, 5096, '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 3. AI对话和AI分析菜单页面（挂在实训任务5075下，可见） ==========
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5101, 'AI对话', 'edu:ai-review:chat', 2, 10, 5075, 'ai-chat', 'ep:chat-dot-round', 'edu/aireview/chat', 'AiReviewChat', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5102, 'AI分析', 'edu:ai-review:analysis', 2, 11, 5075, 'ai-analysis', 'ep:data-analysis', 'edu/aireview/analysis', 'AiReviewAnalysis', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 4. 给超级管理员分配AI评阅菜单 ==========
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(1, 5096, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5097, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5098, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5099, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5100, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5101, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5102, 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 5. 给教师角色(id=161)也分配AI评阅菜单 ==========
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(161, 5096, 'admin', NOW(), 'admin', NOW(), b'0'),
(161, 5097, 'admin', NOW(), 'admin', NOW(), b'0'),
(161, 5098, 'admin', NOW(), 'admin', NOW(), b'0'),
(161, 5099, 'admin', NOW(), 'admin', NOW(), b'0'),
(161, 5100, 'admin', NOW(), 'admin', NOW(), b'0'),
(161, 5101, 'admin', NOW(), 'admin', NOW(), b'0'),
(161, 5102, 'admin', NOW(), 'admin', NOW(), b'0');
