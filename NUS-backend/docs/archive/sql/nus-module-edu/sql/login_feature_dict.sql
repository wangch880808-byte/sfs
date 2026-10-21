-- =============================================
-- 登录功能开关字典
-- 字典类型：system_login_feature
-- 通过修改 value 为 'true'/'false' 控制登录页功能显示
-- =============================================

-- 1. 字典类型
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES ('登录功能开关', 'system_login_feature', 0, '控制登录页面各功能的显示/隐藏', 'admin', NOW(), 'admin', NOW(), 0);

-- 2. 字典数据（value='false' 表示关闭，'true' 表示开启）
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(1, 'mobile_login', 'false', 'system_login_feature', 0, 'info', '手机号登录开关', 'admin', NOW(), 'admin', NOW(), 0),
(2, 'qrcode_login', 'false', 'system_login_feature', 0, 'info', '扫码登录开关', 'admin', NOW(), 'admin', NOW(), 0),
(3, 'third_party_login', 'false', 'system_login_feature', 0, 'info', '第三方登录开关(微信/QQ/GitHub)', 'admin', NOW(), 'admin', NOW(), 0),
(4, 'register', 'false', 'system_login_feature', 0, 'info', '注册功能开关', 'admin', NOW(), 'admin', NOW(), 0);
