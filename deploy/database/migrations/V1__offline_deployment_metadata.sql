CREATE TABLE IF NOT EXISTS nus_deployment_metadata (
  id BIGINT NOT NULL AUTO_INCREMENT,
  metadata_key VARCHAR(128) NOT NULL,
  metadata_value VARCHAR(1024) NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_nus_deployment_metadata_key (metadata_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
