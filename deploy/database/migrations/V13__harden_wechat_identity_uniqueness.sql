-- Canonicalize any legacy duplicates before adding active-row uniqueness.
UPDATE system_social_user_bind b
JOIN system_social_user duplicate_user ON duplicate_user.id = b.social_user_id
JOIN (
  SELECT type, openid, MIN(id) AS keep_id
  FROM system_social_user
  WHERE deleted = b'0'
  GROUP BY type, openid
  HAVING COUNT(*) > 1
) duplicate_group
  ON duplicate_group.type = duplicate_user.type
 AND duplicate_group.openid = duplicate_user.openid
SET b.social_user_id = duplicate_group.keep_id
WHERE b.deleted = b'0'
  AND duplicate_user.id <> duplicate_group.keep_id;

UPDATE system_social_user duplicate_user
JOIN (
  SELECT type, openid, MIN(id) AS keep_id
  FROM system_social_user
  WHERE deleted = b'0'
  GROUP BY type, openid
  HAVING COUNT(*) > 1
) duplicate_group
  ON duplicate_group.type = duplicate_user.type
 AND duplicate_group.openid = duplicate_user.openid
SET duplicate_user.deleted = b'1'
WHERE duplicate_user.id <> duplicate_group.keep_id;

UPDATE system_social_user_bind duplicate_bind
JOIN system_social_user_bind keep_bind
  ON duplicate_bind.user_id = keep_bind.user_id
 AND duplicate_bind.user_type = keep_bind.user_type
 AND duplicate_bind.social_type = keep_bind.social_type
 AND duplicate_bind.id > keep_bind.id
SET duplicate_bind.deleted = b'1'
WHERE duplicate_bind.deleted = b'0' AND keep_bind.deleted = b'0';

UPDATE system_social_user_bind duplicate_bind
JOIN system_social_user_bind keep_bind
  ON duplicate_bind.user_type = keep_bind.user_type
 AND duplicate_bind.social_user_id = keep_bind.social_user_id
 AND duplicate_bind.id > keep_bind.id
SET duplicate_bind.deleted = b'1'
WHERE duplicate_bind.deleted = b'0' AND keep_bind.deleted = b'0';

ALTER TABLE system_social_user
  ADD COLUMN active_identity varchar(96)
    GENERATED ALWAYS AS (CASE WHEN deleted = b'0' THEN CONCAT(type, ':', openid) ELSE NULL END) STORED,
  ADD UNIQUE KEY uk_system_social_user_active_identity (active_identity);

ALTER TABLE system_social_user_bind
  ADD COLUMN active_user_social varchar(128)
    GENERATED ALWAYS AS (CASE WHEN deleted = b'0' THEN CONCAT(user_id, ':', user_type, ':', social_type) ELSE NULL END) STORED,
  ADD COLUMN active_social_user varchar(128)
    GENERATED ALWAYS AS (CASE WHEN deleted = b'0' THEN CONCAT(user_type, ':', social_user_id) ELSE NULL END) STORED,
  ADD UNIQUE KEY uk_system_social_bind_active_user (active_user_social),
  ADD UNIQUE KEY uk_system_social_bind_active_social (active_social_user);
