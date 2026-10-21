-- Dedicated client for the WeChat mini program. Its scope is enforced by the
-- security framework and only permits student-homework APIs.
UPDATE system_oauth2_client
SET status = 0,
    scopes = '["miniapp:student"]',
    auto_approve_scopes = '["miniapp:student"]',
    deleted = b'0',
    update_time = CURRENT_TIMESTAMP
WHERE client_id = 'wechat-mini-program'
  AND deleted = b'0';

INSERT INTO system_oauth2_client (
  client_id, secret, name, logo, description, status,
  access_token_validity_seconds, refresh_token_validity_seconds,
  redirect_uris, authorized_grant_types, scopes, auto_approve_scopes,
  authorities, resource_ids, additional_information, creator, updater, deleted
)
SELECT
  'wechat-mini-program', 'internal-mini-program-only', '微信小程序学生端', '',
  '仅用于学生作业查询与提交的受限令牌', 0,
  1800, 604800, '[]', '[]', '["miniapp:student"]', '["miniapp:student"]',
  '[]', '[]', '{}', 'system', 'system', b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_oauth2_client
  WHERE client_id = 'wechat-mini-program' AND deleted = b'0'
);
