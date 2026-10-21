-- V1.0.1.6 / Flyway V17：界面术语统一与两处授权修复。
-- 一、按《师砺平台界面术语表与文案规范》（NUS-front/docs/ui-copy.md 第 1、3 节）统一配置数据的文字：
--     菜单与按钮名称（角色授权树中可见）、字典类型名称、字典数据标签、教学业务通知模板的名称、内容和备注。
--     只改仍为发布默认值的行，按 ID 加权限标识、路径、字典类型与字典值或模板编码定位；学校改过的文字原样保留；
--     改名后会与同级菜单或其他字典类型重名的跳过。字典值、模板编码、参数和占位符不变；
--     不改业务数据（评价指标、组织机构、用户、课程、任务、提交、留言和日志），已发出的通知保持原文。
-- 二、授权修复，只补缺失的授权，不删除任何授权：
--   1. 教师端「留言互动」（菜单 5107，edu:message:query）加载课程下拉时调用
--      GET /edu/class-course/list-by-class，该接口要求「查询班课」（按钮 5066，edu:class-course:query）。
--      此前只有超级管理员持有 5066，教师打开页面提示「没有该操作权限」，课程下拉为空。
--      为每个已授权 5107 但缺少 5066 的角色补授 5066。
--   2. 只授权了页面、没有授权上级目录的角色在侧边栏看不到该页面
--      （例如学校教务管理员有「管理员看板」5117，却没有上级目录「能力培养」5055）。
--      为持有启用目录或页面的角色逐级补齐缺失的上级目录（type = 1）；只补目录，不补页面和按钮；
--      上级是页面或已停用目录时不再向上补。
-- 可重复执行：改名只匹配默认值，授权只补缺失项，再次执行不产生任何改动。
-- 运维须知：升级脚本会一并清理菜单、权限与通知模板缓存，新文字在升级后立即生效。

-- ===== 菜单与按钮名称 =====
-- 映射表用 utf8mb4_bin，旧名称须逐字一致（不忽略全半角与大小写）。
DROP TEMPORARY TABLE IF EXISTS nus_v17_menu_name;
CREATE TEMPORARY TABLE nus_v17_menu_name (
    id BIGINT PRIMARY KEY,
    type TINYINT NOT NULL,
    permission VARCHAR(100) NOT NULL,
    path VARCHAR(200) NOT NULL,
    old_name VARCHAR(50) NOT NULL,
    new_name VARCHAR(50) NOT NULL
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin;

INSERT INTO nus_v17_menu_name (id, type, permission, path, old_name, new_name) VALUES
    -- 中英文之间加空格
    (111, 2, 'infra:monitor:access', 'druid', 'SQL与连接池监控', 'SQL 与连接池监控'),
    -- 新建一个对象写「新建」，不写「新增」「创建」
    (1002, 3, 'system:user:create', '', '用户新增', '用户新建'),
    (1009, 3, 'system:role:create', '', '角色新增', '角色新建'),
    (1014, 3, 'system:menu:create', '', '菜单新增', '菜单新建'),
    (1018, 3, 'system:dept:create', '', '部门新增', '部门新建'),
    (1022, 3, 'system:post:create', '', '岗位新增', '岗位新建'),
    (1027, 3, 'system:dict:create', '', '字典新增', '字典新建'),
    (1032, 3, 'infra:config:create', '', '配置新增', '配置新建'),
    (1037, 3, 'system:notice:create', '', '公告新增', '公告新建'),
    (1050, 3, 'infra:job:create', '', '任务新增', '任务新建'),
    (1239, 3, 'infra:file-config:create', '', '文件配置创建', '文件配置新建'),
    (1265, 3, 'system:oauth2-client:create', '', '客户端创建', '客户端新建'),
    (2450, 3, 'system:social-client:create', '', '三方应用创建', '三方应用新建'),
    (2763, 3, 'ai:api-key:create', '', 'API 密钥创建', 'API 密钥新建'),
    (2769, 3, 'ai:model:create', '', '聊天模型创建', '聊天模型新建'),
    (2775, 3, 'ai:chat-role:create', '', '聊天角色创建', '聊天角色新建'),
    (2922, 3, 'ai:tool:create', '', '工具创建', '工具新建'),
    (5051, 3, 'edu:class:create', '', '班级新增', '班级新建'),
    (5057, 3, 'capacity:indicator:create', '', '指标新增', '指标新建'),
    (5123, 3, 'capacity:indicator:create', '', '课程新增', '课程新建'),
    -- 系统推送给用户的提醒叫「通知」，不叫「站内信」「消息」
    (2739, 1, '', 'messages', '消息中心', '通知中心'),
    (2144, 1, '', 'notify', '站内信管理', '通知管理'),
    (2146, 3, 'system:notify-template:query', '', '站内信模板查询', '通知模板查询'),
    (2147, 3, 'system:notify-template:create', '', '站内信模板创建', '通知模板新建'),
    (2148, 3, 'system:notify-template:update', '', '站内信模板更新', '通知模板更新'),
    (2149, 3, 'system:notify-template:delete', '', '站内信模板删除', '通知模板删除'),
    (2150, 3, 'system:notify-template:send-notify', '', '发送测试站内信', '发送测试通知'),
    (2151, 2, '', 'notify-message', '消息记录', '通知记录'),
    (2152, 3, 'system:notify-message:query', '', '站内信消息查询', '通知记录查询'),
    -- 教师布置的训练项目叫「任务」，不叫「作业」
    (5077, 3, 'edu:homework:create', '', '创建任务', '新建任务'),
    (5080, 3, 'edu:homework:query', '', '查询作业', '查询任务'),
    (5083, 3, 'edu:homework-record:submit', '', '提交作业', '提交任务'),
    (5084, 3, 'edu:homework-record:correct', '', '批改作业', '批改任务'),
    -- AI 给出的草案叫「AI 初评」，教师确认后才算成绩；不再用「评阅」
    (5096, 1, '', 'ai-review', 'AI评阅', 'AI 辅助'),
    (5097, 3, 'edu:ai-review:correct', '', 'AI批改', 'AI 初评'),
    (5098, 3, 'edu:ai-review:analysis', '', 'AI分析', 'AI 分析'),
    (5099, 3, 'edu:ai-review:chat', '', 'AI对话', 'AI 对话'),
    (5100, 3, 'edu:ai-review:generate', '', 'AI生成', 'AI 生成');

-- 学校已在同一上级下建了同名菜单时不改，避免菜单管理因同级重名无法保存。
DELETE candidate FROM nus_v17_menu_name candidate
JOIN system_menu target ON target.id = candidate.id
JOIN system_menu sibling ON sibling.parent_id = target.parent_id AND sibling.id <> target.id
    AND sibling.deleted = b'0' AND sibling.name = candidate.new_name COLLATE utf8mb4_unicode_ci;

UPDATE system_menu menu
JOIN nus_v17_menu_name candidate ON candidate.id = menu.id AND candidate.type = menu.type
    AND candidate.permission = menu.permission AND candidate.path = menu.path
    AND candidate.old_name = menu.name
SET menu.name = candidate.new_name
WHERE menu.deleted = b'0';

DROP TEMPORARY TABLE nus_v17_menu_name;

-- ===== 字典类型名称 =====
-- 按字典 ID 和类型定位；备注与旧名称相同的随名称一起改；改名后会与其他字典重名的跳过。
DROP TEMPORARY TABLE IF EXISTS nus_v17_dict_type_name;
CREATE TEMPORARY TABLE nus_v17_dict_type_name (
    id BIGINT PRIMARY KEY,
    type VARCHAR(100) NOT NULL,
    old_name VARCHAR(100) NOT NULL,
    new_name VARCHAR(100) NOT NULL
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin;

INSERT INTO nus_v17_dict_type_name (id, type, old_name, new_name) VALUES
    (104, 'system_login_result', '登陆结果', '登录结果'),
    (116, 'system_login_type', '登陆日志的类型', '登录日志的类型'),
    (167, 'system_notify_template_type', '站内信模版的类型', '通知模板的类型'),
    (2009, 'edu_education_level', 'EDU教育层次', '教育层次'),
    (2010, 'edu_teacher_title', 'EDU教师职称', '教师职称'),
    (2011, 'edu_homework_type', 'EDU作业类型', '实训任务类型'),
    (2012, 'edu_homework_level', 'EDU作业等级', '实训任务等级'),
    (2013, 'edu_homework_status', 'EDU作业状态', '实训任务状态');

DELETE candidate FROM nus_v17_dict_type_name candidate
JOIN system_dict_type other ON other.id <> candidate.id AND other.deleted = b'0'
    AND other.name = candidate.new_name COLLATE utf8mb4_unicode_ci;

-- 多表 UPDATE 不保证赋值顺序，备注只与映射表中的旧名称比较。
UPDATE system_dict_type dict
JOIN nus_v17_dict_type_name candidate ON candidate.id = dict.id AND candidate.type = dict.type
    AND candidate.old_name = dict.name
SET dict.remark = IF(dict.remark = candidate.old_name, candidate.new_name, dict.remark),
    dict.name = candidate.new_name
WHERE dict.deleted = b'0';

DROP TEMPORARY TABLE nus_v17_dict_type_name;

-- ===== 字典数据标签 =====
-- 只改标签，字典值不变。
UPDATE system_dict_data SET label = '新建'
WHERE id = 18 AND dict_type = 'infra_operate_type' AND value = '2' AND label = '新增' AND deleted = b'0';

UPDATE system_dict_data SET label = '系统通知'
WHERE id = 1228 AND dict_type = 'system_notify_template_type' AND value = '2' AND label = '系统消息'
  AND deleted = b'0';

-- ===== 教学业务通知模板 =====
-- 各库模板 ID 不同（V7 按编码补建），一律按模板编码定位；名称、内容、备注分别仅在仍为默认值时改写。
-- 课程、任务等名称用「」括起，代替原来两侧的空格；参数列表和占位符逐个保留。
DROP TEMPORARY TABLE IF EXISTS nus_v17_notify_template;
CREATE TEMPORARY TABLE nus_v17_notify_template (
    code VARCHAR(64) PRIMARY KEY,
    old_name VARCHAR(63) NOT NULL,
    new_name VARCHAR(63) NOT NULL,
    old_content VARCHAR(1024) NOT NULL,
    new_content VARCHAR(1024) NOT NULL
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin;

INSERT INTO nus_v17_notify_template (code, old_name, new_name, old_content, new_content) VALUES
    ('edu.student.class.joined', '学生被加入班级', '学生被加入班级',
     '你已被加入班级：{className}。',
     '你已被加入班级「{className}」。'),
    ('edu.student.course.assigned', '学生被分配课程', '学生被分配课程',
     '你所在班级 {className} 已分配课程：{courseName}。',
     '你所在班级「{className}」已分配课程「{courseName}」。'),
    ('edu.student.homework.created', '新作业发布', '新任务发布',
     '课程 {courseName} 发布了新作业：{homeworkName}，截止时间：{deadline}。',
     '课程「{courseName}」发布了新任务「{homeworkName}」，截止时间：{deadline}。'),
    ('edu.student.homework.updated', '作业变更', '任务变更',
     '课程 {courseName} 的作业 {homeworkName} 已更新，请及时查看，截止时间：{deadline}。',
     '课程「{courseName}」的任务「{homeworkName}」已更新，请及时查看，截止时间：{deadline}。'),
    ('edu.student.homework.corrected', '作业批改完成', '任务批改完成',
     '作业 {homeworkName} 已批改完成，得分：{score}，评语：{comment}。',
     '任务「{homeworkName}」已批改完成，得分：{score} 分，评语：{comment}。'),
    ('edu.student.homework.redo', '作业打回重做', '任务打回重做',
     '作业 {homeworkName} 已被打回重做，请根据要求重新提交。',
     '任务「{homeworkName}」已被打回重做，请根据要求重新提交。'),
    ('edu.student.homework.remind', '作业催交', '任务催交',
     '作业 {homeworkName} 尚未提交，请在 {deadline} 前完成提交。',
     '任务「{homeworkName}」尚未提交，请在 {deadline} 前完成提交。'),
    ('edu.student.message.reply', '教师回复留言', '教师回复留言',
     '课程 {courseName} 收到教师回复：{content}',
     '教师回复了你在课程「{courseName}」的留言：{content}'),
    ('edu.teacher.message.created', '学生发起留言', '学生发起留言',
     '学生 {studentName} 在课程 {courseName} 发起留言：{content}',
     '{studentName}在课程「{courseName}」留言：{content}'),
    ('edu.admin.public-course.pending', '公共课待审批', '公共课待审批',
     '公共课 {courseName} 已提交审批，提交教师：{teacherName}。',
     '公共课「{courseName}」已提交审批，提交教师：{teacherName}。'),
    ('edu.teacher.public-course.audit-result', '公共课审批结果', '公共课审批结果',
     '公共课 {courseName} 审批结果：{auditResult}，审批意见：{auditRemark}。',
     '公共课「{courseName}」审批结果：{auditResult}，审批意见：{auditRemark}。');

UPDATE system_notify_template notify_tpl
JOIN nus_v17_notify_template candidate ON candidate.code = notify_tpl.code
SET notify_tpl.name = candidate.new_name
WHERE notify_tpl.deleted = b'0' AND notify_tpl.name = candidate.old_name;

UPDATE system_notify_template notify_tpl
JOIN nus_v17_notify_template candidate ON candidate.code = notify_tpl.code
SET notify_tpl.content = candidate.new_content
WHERE notify_tpl.deleted = b'0' AND notify_tpl.content = candidate.old_content;

-- 备注有两种默认值：基线库为「教学业务 P0 站内信模板」，经 V7 补建的为「教学业务站内信模板」。
UPDATE system_notify_template notify_tpl
JOIN nus_v17_notify_template candidate ON candidate.code = notify_tpl.code
SET notify_tpl.remark = '教学业务通知模板'
WHERE notify_tpl.deleted = b'0' AND notify_tpl.remark IN ('教学业务 P0 站内信模板', '教学业务站内信模板');

DROP TEMPORARY TABLE nus_v17_notify_template;

-- ===== 留言互动所需的班课查询授权 =====
-- 只补按钮 5066 本身；它所在的「实训课程管理」页面和上级目录不随之授予。
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted)
SELECT DISTINCT role.id, query_menu.id, 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM system_role role
JOIN system_role_menu message_grant ON message_grant.role_id = role.id AND message_grant.deleted = b'0'
JOIN system_menu message_menu ON message_menu.id = message_grant.menu_id AND message_menu.id = 5107
    AND message_menu.permission = 'edu:message:query' AND message_menu.deleted = b'0'
JOIN system_menu query_menu ON query_menu.id = 5066 AND query_menu.type = 3
    AND query_menu.permission = 'edu:class-course:query'
    AND query_menu.status = 0 AND query_menu.deleted = b'0'
WHERE role.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu granted
                  WHERE granted.role_id = role.id AND granted.menu_id = query_menu.id
                    AND granted.deleted = b'0');

-- ===== 补齐上级目录授权 =====
-- 从角色已授权、启用的目录或页面出发，沿上级逐级向上，只经过未删除、启用的目录；
-- 遇到页面、按钮或已停用目录即停止。按钮不作为起点：按钮只控制接口，不出现在侧边栏。
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted)
WITH RECURSIVE ancestor_dir (role_id, menu_id) AS (
    SELECT granted.role_id, parent.id
    FROM system_role_menu granted
    JOIN system_menu child ON child.id = granted.menu_id AND child.deleted = b'0'
        AND child.status = 0 AND child.type IN (1, 2)
    JOIN system_menu parent ON parent.id = child.parent_id AND parent.deleted = b'0'
        AND parent.status = 0 AND parent.type = 1
    WHERE granted.deleted = b'0'
    UNION
    SELECT found.role_id, parent.id
    FROM ancestor_dir found
    JOIN system_menu dir ON dir.id = found.menu_id
    JOIN system_menu parent ON parent.id = dir.parent_id AND parent.deleted = b'0'
        AND parent.status = 0 AND parent.type = 1
)
SELECT DISTINCT found.role_id, found.menu_id, 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, b'0'
FROM ancestor_dir found
JOIN system_role role ON role.id = found.role_id AND role.deleted = b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_role_menu granted
                  WHERE granted.role_id = found.role_id AND granted.menu_id = found.menu_id
                    AND granted.deleted = b'0');
