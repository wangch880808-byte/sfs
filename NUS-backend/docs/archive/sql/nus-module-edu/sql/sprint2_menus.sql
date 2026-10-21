-- =============================================
-- Sprint 2 学生端菜单 + 学生角色
-- 菜单ID从5086开始（Sprint 1最大5085）
-- =============================================

-- ========== 1. 学生端顶级目录 ==========
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5086, '学生学习', '', 1, 0, 0, '/student', 'ep:reading', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 2. 学生端菜单 ==========
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5087, '学习首页', 'edu:student-home:query', 2, 1, 5086, 'home', 'ep:home-filled', 'edu/student/home/index', 'StudentHome', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5088, '我的课程', 'edu:student-course:query', 2, 2, 5086, 'course', 'ep:notebook', 'edu/student/course/index', 'StudentCourse', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5089, '任务中心', 'edu:student-homework:query', 2, 3, 5086, 'homework', 'ep:edit-pen', 'edu/student/homework/index', 'StudentHomework', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5090, '能力分析', 'edu:student-capacity:query', 2, 4, 5086, 'capacity', 'ep:data-analysis', 'edu/student/capacity/index', 'StudentCapacity', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5091, '个人中心', 'edu:student-profile:query', 2, 5, 5086, 'profile', 'ep:user-filled', 'edu/student/profile/index', 'StudentProfile', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 3. 隐藏路由菜单 ==========
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(5092, '课程详情', 'edu:student-course:query', 2, 10, 5086, 'course/detail', '#', 'edu/student/course/detail', 'StudentCourseDetail', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5093, '任务提交', 'edu:student-homework:submit', 2, 11, 5086, 'homework/submit', '#', 'edu/student/homework/submit', 'StudentHomeworkSubmit', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5094, '成绩反馈', 'edu:student-homework:query', 2, 12, 5086, 'homework/feedback', '#', 'edu/student/homework/feedback', 'StudentHomeworkFeedback', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(5095, '实训报告', 'edu:student-capacity:query', 2, 13, 5086, 'capacity/report', '#', 'edu/student/capacity/report', 'StudentCapacityReport', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 4. 学生角色 ==========
INSERT INTO `system_role` (`id`, `name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(200, '学生', 'student', 10, 1, '', 0, 2, '学生角色，拥有学生端所有菜单权限', 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 5. 给学生角色分配菜单 ==========
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(200, 5086, 'admin', NOW(), 'admin', NOW(), b'0'),
(200, 5087, 'admin', NOW(), 'admin', NOW(), b'0'),
(200, 5088, 'admin', NOW(), 'admin', NOW(), b'0'),
(200, 5089, 'admin', NOW(), 'admin', NOW(), b'0'),
(200, 5090, 'admin', NOW(), 'admin', NOW(), b'0'),
(200, 5091, 'admin', NOW(), 'admin', NOW(), b'0'),
(200, 5092, 'admin', NOW(), 'admin', NOW(), b'0'),
(200, 5093, 'admin', NOW(), 'admin', NOW(), b'0'),
(200, 5094, 'admin', NOW(), 'admin', NOW(), b'0'),
(200, 5095, 'admin', NOW(), 'admin', NOW(), b'0');

-- ========== 6. 给超级管理员也分配学生端菜单（方便调试） ==========
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(1, 5086, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5087, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5088, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5089, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5090, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5091, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5092, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5093, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5094, 'admin', NOW(), 'admin', NOW(), b'0'),
(1, 5095, 'admin', NOW(), 'admin', NOW(), b'0');
