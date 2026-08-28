#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BACKUP=${1:?usage: test-production-restore.sh BACKUP_FILE}
COMPOSE_FILE=${COMPOSE_FILE:-"$ROOT/deploy/docker-compose.production.yml"}
ENV_FILE=${ENV_FILE:-"$ROOT/deploy/.env.production"}
PASSWORD=${BACKUP_ENCRYPTION_PASSWORD:-}
CONTAINER=$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q postgres)
test -n "$CONTAINER"

WORK=$(mktemp -d)
cleanup() {
  rm -rf "$WORK"
}
trap cleanup EXIT

DUMP="$BACKUP"
case "$BACKUP" in
  *.pmbak)
  test "${#PASSWORD}" -ge 16
  if [ -f "$BACKUP.hmac" ]; then
    EXPECTED_HMAC=$(sed 's/.*= //' "$BACKUP.hmac")
    ACTUAL_HMAC=$(openssl dgst -sha256 -hmac "$PASSWORD" "$BACKUP" | sed 's/.*= //')
    test "$EXPECTED_HMAC" = "$ACTUAL_HMAC"
  fi
  PAYMENT_BACKUP_PASSWORD_TMP="$PASSWORD" openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 \
    -in "$BACKUP" -out "$WORK/archive" -pass env:PAYMENT_BACKUP_PASSWORD_TMP
  DUMP_DIR="$WORK/extract"
  mkdir -p "$DUMP_DIR"
  tar -xzf "$WORK/archive" -C "$DUMP_DIR"
  test -s "$DUMP_DIR/database/postgres.dump"
  test -s "$DUMP_DIR/database/redis.rdb"
  test -s "$DUMP_DIR/storage/minio-bucket-manifest.json"
  test -d "$DUMP_DIR/storage/minio-bucket"
  DUMP="$DUMP_DIR/database/postgres.dump"
  ;;
esac

DB="payment_monitor_restore_$(date -u +%Y%m%d%H%M%S)"
docker cp "$DUMP" "$CONTAINER:/tmp/payment-monitor-restore.dump"
cleanup_remote() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T postgres \
    dropdb -U payment_monitor --if-exists "$DB" >/dev/null 2>&1 || true
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T postgres \
    rm -f /tmp/payment-monitor-restore.dump >/dev/null 2>&1 || true
}
trap cleanup_remote EXIT
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T postgres \
  createdb -U payment_monitor "$DB"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T postgres \
  pg_restore -U payment_monitor -d "$DB" --clean --if-exists /tmp/payment-monitor-restore.dump
VERSION=$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T postgres \
  psql -U payment_monitor -d "$DB" -Atc \
  "select version from pm_flyway_schema_history where success order by installed_rank desc limit 1")
test -n "$VERSION"
echo "restore rehearsal passed, Flyway version $VERSION"
