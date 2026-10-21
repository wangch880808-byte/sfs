-- 师砺平台 V1.0.1.4 升级前只读核查
-- 只包含 SELECT，不修改任何数据；不输出口令、密钥或口令哈希。
-- 用法示例：在生产 MySQL 容器内以只读账号执行，并把结果交给实施人员。

-- 1. Flyway 当前版本（预期 14）
SELECT version, description, success, installed_on
FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;

-- 2. 注册开关与初始密码状态（初始密码只显示是否为上游默认值）
SELECT config_key,
       CASE
         WHEN config_key = 'system.user.init-password' THEN
           CASE WHEN value = '123456' THEN 'UPSTREAM_DEFAULT' WHEN value = '' THEN 'EMPTY' ELSE 'CUSTOM' END
         ELSE value
       END AS value_state
FROM infra_config
WHERE deleted = 0 AND config_key IN ('system.user.register-enabled', 'system.user.init-password');

-- 3. OAuth2 客户端（关注是否存在上游演示客户端与 password/client_credentials 授权）
SELECT client_id, name, status, authorized_grant_types, redirect_uris
FROM system_oauth2_client WHERE deleted = 0;

-- 4. AI 工具（关注 directory_list / weather_query / user_profile_query）
SELECT id, name, status FROM ai_tool WHERE deleted = 0;

-- 5. 各角色的菜单授权数量
SELECT r.id, r.code, r.name, r.data_scope, COUNT(rm.id) AS menu_grants
FROM system_role r
LEFT JOIN system_role_menu rm ON rm.role_id = r.id AND rm.deleted = 0
WHERE r.deleted = 0
GROUP BY r.id, r.code, r.name, r.data_scope
ORDER BY r.id;

-- 6. 教师与教务管理员角色是否具备任务页、班级页依赖的权限码
SELECT r.code AS role_code, m.id AS menu_id, m.permission
FROM system_role_menu rm
JOIN system_role r ON r.id = rm.role_id AND r.deleted = 0
JOIN system_menu m ON m.id = rm.menu_id AND m.deleted = 0
WHERE rm.deleted = 0
  AND r.code IN ('teacher', 'school_admin', 'college_admin')
  AND m.permission IN ('system:user:query', 'edu:class:query', 'edu:class-course:query',
                       'edu:class-course:bind', 'edu:homework:query', 'edu:homework-record:correct')
ORDER BY r.code, m.permission;

-- 7. V15 将处理的菜单当前值（与基线不一致的行，V15 会按精确匹配跳过）
SELECT id, name, parent_id, path, component, permission, status, deleted
FROM system_menu
WHERE id IN (1093, 2130, 1263, 2920, 114, 116, 1255, 1256, 1257, 1258, 1259, 1260, 5110, 5112, 5103, 5105)
ORDER BY id;

-- 8. 登录页功能开关
SELECT label, value, status FROM system_dict_data
WHERE dict_type = 'system_login_feature' AND deleted = 0 ORDER BY sort;

-- 9. 已登记的定时任务
SELECT id, name, handler_name, handler_param, cron_expression, status FROM infra_job WHERE deleted = 0;
