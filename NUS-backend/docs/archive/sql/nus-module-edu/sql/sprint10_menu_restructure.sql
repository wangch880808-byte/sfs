-- =============================================
-- Sprint 10 菜单结构重构（按PRD角色端归类）
-- 新建目录：5119指标中心、5120分析中心
-- 移动菜单归属、修复可见性和排序
-- =============================================

-- ========== 1. 新建目录菜单 ==========

-- 1.1 指标中心（挂在能力培养5055下）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5119, '指标中心', '', 1, 3, 5055, 'indicator-center', 'ep:odometer', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- 1.2 分析中心（挂在能力培养5055下）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5120, '分析中心', '', 1, 4, 5055, 'analysis-center', 'ep:data-analysis', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 2. 移动菜单到指标中心(5119) ==========

-- 评价指标管理：从能力培养(5055) → 指标中心(5119)
UPDATE `system_menu` SET `parent_id` = 5119, `sort` = 1, `update_time` = NOW() WHERE `id` = 5056 AND `deleted` = 0;

-- 证书配置：从能力培养(5055) → 指标中心(5119)
UPDATE `system_menu` SET `parent_id` = 5119, `sort` = 2, `update_time` = NOW() WHERE `id` = 5111 AND `deleted` = 0;

-- ========== 3. 移动菜单到分析中心(5120) ==========

-- 统计分析：从能力培养(5055) → 分析中心(5120)
UPDATE `system_menu` SET `parent_id` = 5120, `sort` = 1, `update_time` = NOW() WHERE `id` = 5103 AND `deleted` = 0;

-- 教学过程分析：从能力培养(5055) → 分析中心(5120)
UPDATE `system_menu` SET `parent_id` = 5120, `sort` = 2, `update_time` = NOW() WHERE `id` = 5114 AND `deleted` = 0;

-- 教学问题研判：从能力培养(5055) → 分析中心(5120)
UPDATE `system_menu` SET `parent_id` = 5120, `sort` = 3, `update_time` = NOW() WHERE `id` = 5115 AND `deleted` = 0;

-- 单项能力评测：从能力培养(5055) → 分析中心(5120)
UPDATE `system_menu` SET `parent_id` = 5120, `sort` = 4, `update_time` = NOW() WHERE `id` = 5116 AND `deleted` = 0;

-- 学生统计列表（隐藏路由）：从能力培养(5055) → 分析中心(5120)
UPDATE `system_menu` SET `parent_id` = 5120, `sort` = 10, `update_time` = NOW() WHERE `id` = 5104 AND `deleted` = 0;

-- ========== 4. 修复AI对话/分析可见性（改为隐藏） ==========

UPDATE `system_menu` SET `visible` = b'0', `update_time` = NOW() WHERE `id` = 5101 AND `deleted` = 0;
UPDATE `system_menu` SET `visible` = b'0', `update_time` = NOW() WHERE `id` = 5102 AND `deleted` = 0;

-- ========== 5. 修复教务组织下sort排序 ==========

-- 班课关联 sort 10→5
UPDATE `system_menu` SET `sort` = 5, `update_time` = NOW() WHERE `id` = 5063 AND `deleted` = 0;
-- 校园资讯 sort 4→6
UPDATE `system_menu` SET `sort` = 6, `update_time` = NOW() WHERE `id` = 5110 AND `deleted` = 0;
-- 留言互动 sort 3→7
UPDATE `system_menu` SET `sort` = 7, `update_time` = NOW() WHERE `id` = 5107 AND `deleted` = 0;

-- ========== 6. 修复能力培养下sort排序 ==========

-- 课程管理 sort→1
UPDATE `system_menu` SET `sort` = 1, `update_time` = NOW() WHERE `id` = 5061 AND `deleted` = 0;
-- 实训任务 sort 20→2
UPDATE `system_menu` SET `sort` = 2, `update_time` = NOW() WHERE `id` = 5075 AND `deleted` = 0;
-- 管理员看板 sort→5
UPDATE `system_menu` SET `sort` = 5, `update_time` = NOW() WHERE `id` = 5117 AND `deleted` = 0;
-- 教师工作台 sort→6
UPDATE `system_menu` SET `sort` = 6, `update_time` = NOW() WHERE `id` = 5118 AND `deleted` = 0;

-- ========== 7. 给角色分配新目录菜单 ==========

-- 超级管理员(1)
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(1, 5119, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5120, 'admin', NOW(), 'admin', NOW(), b'0');

-- 教师角色(161)
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(161, 5119, 'admin', NOW(), 'admin', NOW(), b'0'),
(161, 5120, 'admin', NOW(), 'admin', NOW(), b'0');
