-- The admin login flow always issues tokens for client_id "default". Keep this
-- record as system configuration; no user or business test data belongs here.
UPDATE system_oauth2_client
SET status = 0,
    deleted = b'0',
    update_time = CURRENT_TIMESTAMP
WHERE client_id = 'default'
  AND deleted = b'0';

INSERT INTO system_oauth2_client (
  client_id,
  secret,
  name,
  logo,
  description,
  status,
  access_token_validity_seconds,
  refresh_token_validity_seconds,
  redirect_uris,
  authorized_grant_types,
  scopes,
  auto_approve_scopes,
  authorities,
  resource_ids,
  additional_information,
  creator,
  updater,
  deleted
)
SELECT
  'default',
  'internal-login-only',
  '师砺平台默认客户端',
  '',
  '管理端登录令牌签发所需的系统配置',
  0,
  1800,
  2592000,
  '[]',
  '[]',
  '[]',
  '[]',
  '[]',
  '[]',
  '{}',
  'system',
  'system',
  b'0'
WHERE NOT EXISTS (
  SELECT 1
  FROM system_oauth2_client
  WHERE client_id = 'default'
    AND deleted = b'0'
);
