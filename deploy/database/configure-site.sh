#!/bin/sh
set -eu

: "${MYSQL_CONTAINER:?MYSQL_CONTAINER is required}"
: "${NUS_DB_ROOT_PASSWORD:?NUS_DB_ROOT_PASSWORD is required}"
: "${NUS_SITE_URL:?NUS_SITE_URL is required}"
: "${NUS_SKYWALKING_URL:?NUS_SKYWALKING_URL is required}"
NUS_DB_NAME=${NUS_DB_NAME:-nus}
NUS_FILE_BASE_PATH=${NUS_FILE_BASE_PATH:-/app/uploads}

escape_sql() {
  printf '%s' "$1" | sed "s/'/''/g"
}

site_url=$(escape_sql "$NUS_SITE_URL")
base_path=$(escape_sql "$NUS_FILE_BASE_PATH")
skywalking_url=$(escape_sql "$NUS_SKYWALKING_URL")

MYSQL_PWD=$NUS_DB_ROOT_PASSWORD docker exec -i -e MYSQL_PWD "$MYSQL_CONTAINER" mysql --protocol=socket -uroot "$NUS_DB_NAME" <<SQL
UPDATE infra_file_config
SET config = JSON_SET(config, '$.basePath', '$base_path', '$.domain', '$site_url')
WHERE master = 1 AND storage = 10 AND deleted = 0;
UPDATE infra_config
SET value = '$skywalking_url', remark = 'SkyWalking Horizon tracing console',
    updater = '1', update_time = CURRENT_TIMESTAMP
WHERE config_key = 'url.skywalking' AND deleted = 0;
INSERT INTO nus_deployment_metadata (metadata_key, metadata_value)
VALUES ('site.url', '$site_url')
ON DUPLICATE KEY UPDATE metadata_value = VALUES(metadata_value);
INSERT INTO nus_deployment_metadata (metadata_key, metadata_value)
VALUES ('file.basePath', '$base_path')
ON DUPLICATE KEY UPDATE metadata_value = VALUES(metadata_value);
SQL
