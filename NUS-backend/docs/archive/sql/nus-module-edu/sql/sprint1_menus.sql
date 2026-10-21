-- =============================================
-- Sprint 1 菜单与权限SQL
-- 基于现有菜单结构：maxId=5062
-- 教务组织(5047), 能力培养(5055), 课程管理(5061)
-- =============================================

-- ========== 1. 教务组织 下新增菜单 ==========

-- 1.1 班课关联管理（菜单）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5063, '班课关联', 'edu:class-course:list', 2, 10, 5047, 'class-course', 'ep:connection', 'edu/classcourse/index', 'EduClassCourse', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- 班课关联按钮权限
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5064, '绑定课程', 'edu:class-course:bind', 3, 1, 5063, '', '#', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5065, '解绑课程', 'edu:class-course:unbind', 3, 2, 5063, '', '#', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5066, '查询班课', 'edu:class-course:query', 3, 3, 5063, '', '#', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 2. 能力培养 > 课程管理 下新增菜单 ==========

-- 2.1 课程资源管理（菜单，放在课程管理目录下）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5071, '课程资源', 'edu:course-resource:list', 2, 10, 5061, 'course-resource', 'ep:folder-opened', 'edu/courseresource/index', 'EduCourseResource', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- 课程资源按钮权限
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5072, '上传资源', 'edu:course-resource:create', 3, 1, 5071, '', '#', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5073, '删除资源', 'edu:course-resource:delete', 3, 2, 5071, '', '#', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5074, '查询资源', 'edu:course-resource:query', 3, 3, 5071, '', '#', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 3. 能力培养 下新增 实训任务 目录 ==========

-- 3.1 实训任务目录
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5075, '实训任务', '', 1, 20, 5055, 'homework_manager', 'ep:edit-pen', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- 3.2 实训任务管理（菜单）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5076, '实训任务管理', 'edu:homework:list', 2, 0, 5075, 'homework', 'ep:document', 'edu/homework/index', 'EduHomework', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- 实训任务管理按钮权限
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5077, '创建任务', 'edu:homework:create', 3, 1, 5076, '', '#', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5078, '修改任务', 'edu:homework:update', 3, 2, 5076, '', '#', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5079, '删除任务', 'edu:homework:delete', 3, 3, 5076, '', '#', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5080, '查询任务', 'edu:homework:query', 3, 4, 5076, '', '#', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- 3.3 实训任务详情页（隐藏菜单）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5081, '实训任务详情', 'edu:homework:query', 2, 1, 5075, 'homework/:id', '#', 'edu/homework/[id]', 'EduHomeworkDetail', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- 3.4 实训任务批改页（隐藏菜单）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5082, '实训任务批改', 'edu:homework-record:correct', 2, 2, 5075, 'homework-correct', '#', 'edu/homework/correct', 'EduHomeworkCorrect', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- 任务记录按钮权限
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5083, '提交任务', 'edu:homework-record:submit', 3, 5, 5076, '', '#', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5084, '批改任务', 'edu:homework-record:correct', 3, 6, 5076, '', '#', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5085, '查询记录', 'edu:homework-record:query', 3, 7, 5076, '', '#', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 4. 给超级管理员角色分配所有新菜单权限 ==========
-- 查询超级管理员角色ID（通常为1），将新菜单ID加入角色菜单关联
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`) VALUES
(1, 5063, 'admin', NOW(), 'admin', NOW(), b'0', 0),
(1, 5064, 'admin', NOW(), 'admin', NOW(), b'0', 0),
(1, 5065, 'admin', NOW(), 'admin', NOW(), b'0', 0),
(1, 5066, 'admin', NOW(), 'admin', NOW(), b'0', 0),
(1, 5071, 'admin', NOW(), 'admin', NOW(), b'0', 0),
(1, 5072, 'admin', NOW(), 'admin', NOW(), b'0', 0),
(1, 5073, 'admin', NOW(), 'admin', NOW(), b'0', 0),
(1, 5074, 'admin', NOW(), 'admin', NOW(), b'0', 0),
(1, 5075, 'admin', NOW(), 'admin', NOW(), b'0', 0),
(1, 5076, 'admin', NOW(), 'admin', NOW(), b'0', 0),
(1, 5077, 'admin', NOW(), 'admin', NOW(), b'0', 0),
(1, 5078, 'admin', NOW(), 'admin', NOW(), b'0', 0),
(1, 5079, 'admin', NOW(), 'admin', NOW(), b'0', 0),
(1, 5080, 'admin', NOW(), 'admin', NOW(), b'0', 0),
(1, 5081, 'admin', NOW(), 'admin', NOW(), b'0', 0),
(1, 5082, 'admin', NOW(), 'admin', NOW(), b'0', 0),
(1, 5083, 'admin', NOW(), 'admin', NOW(), b'0', 0),
(1, 5084, 'admin', NOW(), 'admin', NOW(), b'0', 0),
(1, 5085, 'admin', NOW(), 'admin', NOW(), b'0', 0);
