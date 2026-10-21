#!/bin/sh
set -eu

: "${FLYWAY_NETWORK:?FLYWAY_NETWORK is required}"
: "${NUS_DB_USERNAME:?NUS_DB_USERNAME is required}"
: "${NUS_DB_PASSWORD:?NUS_DB_PASSWORD is required}"
NUS_DB_HOST=${NUS_DB_HOST:-mysql}
NUS_DB_PORT=${NUS_DB_PORT:-3306}
NUS_DB_NAME=${NUS_DB_NAME:-nus}
FLYWAY_IMAGE=${FLYWAY_IMAGE:-flyway/flyway:10.20.1-alpine@sha256:15e105262c902c129e1a637142065654a1fb6d40d4f28fc5c143f88f957672b7}
SCRIPT_DIR=$(cd -- "$(dirname "$0")" && pwd)
MIGRATIONS_DIR=${MIGRATIONS_DIR:-$SCRIPT_DIR/migrations}

export FLYWAY_PASSWORD=$NUS_DB_PASSWORD
exec docker run --rm --network "$FLYWAY_NETWORK" -e FLYWAY_PASSWORD \
  -v "$MIGRATIONS_DIR:/flyway/sql:ro" \
  "$FLYWAY_IMAGE" \
  -url="jdbc:mysql://${NUS_DB_HOST}:${NUS_DB_PORT}/${NUS_DB_NAME}?useSSL=false&allowPublicKeyRetrieval=true" \
  -user="$NUS_DB_USERNAME" \
  -locations=filesystem:/flyway/sql "$@"
