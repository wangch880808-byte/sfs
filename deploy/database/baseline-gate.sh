#!/bin/sh
set -eu

: "${MYSQL_CONTAINER:?MYSQL_CONTAINER is required}"
: "${NUS_DB_ROOT_PASSWORD:?NUS_DB_ROOT_PASSWORD is required}"
NUS_DB_NAME=${NUS_DB_NAME:-nus}
BASELINE_VERSION=${BASELINE_VERSION:-21}

table_count=$(MYSQL_PWD=$NUS_DB_ROOT_PASSWORD docker exec -e MYSQL_PWD "$MYSQL_CONTAINER" mysql --protocol=socket -uroot -Nse \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${NUS_DB_NAME}';")
flyway_count=$(MYSQL_PWD=$NUS_DB_ROOT_PASSWORD docker exec -e MYSQL_PWD "$MYSQL_CONTAINER" mysql --protocol=socket -uroot -Nse \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${NUS_DB_NAME}' AND table_name='flyway_schema_history';")

case "$1" in
  check-empty)
    test "$table_count" -eq 0
    ;;
  check-baselined)
    test "$flyway_count" -eq 1
    MYSQL_PWD=$NUS_DB_ROOT_PASSWORD docker exec -e MYSQL_PWD "$MYSQL_CONTAINER" mysql --protocol=socket -uroot -Nse \
      "SELECT COUNT(*) FROM ${NUS_DB_NAME}.flyway_schema_history WHERE version='${BASELINE_VERSION}' AND success=1;" | awk '$1 == 1 { ok=1 } END { exit(ok ? 0 : 1) }'
    ;;
  *)
    printf '%s\n' 'usage: baseline-gate.sh check-empty|check-baselined' >&2
    exit 2
    ;;
esac
