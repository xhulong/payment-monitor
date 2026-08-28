#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
COMPOSE_FILE=${COMPOSE_FILE:-"$ROOT/deploy/docker-compose.production.yml"}
ENV_FILE=${ENV_FILE:-"$ROOT/deploy/.env.production"}
OUTPUT_DIR=${OUTPUT_DIR:-"$ROOT/backups/production"}
PASSWORD=${BACKUP_ENCRYPTION_PASSWORD:-}
case "$OUTPUT_DIR" in
  "$ROOT"/backups/*) ;;
  *) echo "backup output must stay under $ROOT/backups" >&2; exit 1 ;;
esac
test -f "$COMPOSE_FILE" -a -f "$ENV_FILE"
test "${#PASSWORD}" -ge 16 || { echo "BACKUP_ENCRYPTION_PASSWORD must be at least 16 characters" >&2; exit 1; }
command -v openssl >/dev/null 2>&1 || { echo "openssl is required" >&2; exit 1; }
mkdir -p "$OUTPUT_DIR"

STAMP=$(date -u +%Y%m%d-%H%M%S)
STAGE="$OUTPUT_DIR/.staging-$STAMP"
BUNDLE="$OUTPUT_DIR/bundle-$STAMP.tar.gz"
ENCRYPTED="$OUTPUT_DIR/daily-$STAMP.pmbak"
mkdir -p "$STAGE/database" "$STAGE/storage/minio-bucket" "$STAGE/config"

env_value() {
  sed -n "s/^$1=//p" "$ENV_FILE" | head -n 1
}
CONTAINER=$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q postgres)
REDIS_CONTAINER=$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q redis)
MINIO_CONTAINER=$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q minio)
test -n "$CONTAINER" -a -n "$REDIS_CONTAINER" -a -n "$MINIO_CONTAINER"

PG_REMOTE="/tmp/payment-monitor-$STAMP.dump"
REDIS_REMOTE="/tmp/payment-monitor-$STAMP.rdb"
cleanup() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T postgres rm -f "$PG_REMOTE" >/dev/null 2>&1 || true
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T redis rm -f "$REDIS_REMOTE" >/dev/null 2>&1 || true
  rm -rf "$STAGE" "$BUNDLE"
}
trap cleanup EXIT

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T postgres \
  pg_dump -U payment_monitor -d payment_monitor -Fc -f "$PG_REMOTE"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T postgres \
  pg_restore --list "$PG_REMOTE" >/dev/null
docker cp "$CONTAINER:$PG_REMOTE" "$STAGE/database/postgres.dump"

REDIS_PASSWORD=$(env_value REDIS_PASSWORD || true)
if [ -n "$REDIS_PASSWORD" ]; then
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T redis \
    sh -c "redis-cli -a '$REDIS_PASSWORD' --rdb '$REDIS_REMOTE'"
else
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T redis \
    redis-cli --rdb "$REDIS_REMOTE"
fi
docker cp "$REDIS_CONTAINER:$REDIS_REMOTE" "$STAGE/database/redis.rdb"

MINIO_BUCKET=$(awk -F= '/^MINIO_BUCKET=/{print substr($0,index($0,"=")+1)}' "$ENV_FILE" | tail -1)
MINIO_BUCKET=${MINIO_BUCKET:-payment-monitor-private}
docker run --rm \
  --network "container:$MINIO_CONTAINER" \
  --env-file "$ENV_FILE" \
  --env "MINIO_BUCKET=$MINIO_BUCKET" \
  --mount "type=bind,source=$STAGE/storage/minio-bucket,target=/backup" \
  --entrypoint /bin/sh \
  minio/mc:RELEASE.2025-04-16T18-13-26Z \
  -c 'mc alias set source http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null && mc mirror --overwrite --preserve "source/$MINIO_BUCKET" /backup'
printf '{"bucket":"%s","mode":"mc-mirror","mirroredAtUtc":"%s"}\n' \
  "$MINIO_BUCKET" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  > "$STAGE/storage/minio-bucket-manifest.json"

cp "$ENV_FILE" "$STAGE/config/env-production.snapshot"
CERT_DIR=$(dirname "$COMPOSE_FILE")/production/certs
{
  find "$CERT_DIR" -maxdepth 1 -type f -printf '%f %s ' 2>/dev/null || true
  find "$CERT_DIR" -maxdepth 1 -type f -exec sha256sum {} \; 2>/dev/null || true
} > "$STAGE/config/certificate-metadata.txt"
find "$STAGE" -type f -exec sha256sum {} \; > "$STAGE/manifest.sha256"

tar -czf "$BUNDLE" -C "$STAGE" .
PAYMENT_BACKUP_PASSWORD_TMP="$PASSWORD" \
  openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt \
    -in "$BUNDLE" -out "$ENCRYPTED" \
    -pass env:PAYMENT_BACKUP_PASSWORD_TMP
sha256sum "$ENCRYPTED" > "$ENCRYPTED.sha256"
openssl dgst -sha256 -hmac "$PASSWORD" -out "$ENCRYPTED.hmac" "$ENCRYPTED"

if [ "$(date -u +%u)" = "7" ]; then
  cp "$ENCRYPTED" "$OUTPUT_DIR/weekly-$STAMP.pmbak"
fi
if [ "$(date -u +%u)" = "7" ] && [ "$(date -u +%d)" -le 07 ] &&
   { [ "$(date -u +%m)" = "01" ] || [ "$(date -u +%m)" = "04" ] ||
     [ "$(date -u +%m)" = "07" ] || [ "$(date -u +%m)" = "10" ]; }; then
  cp "$ENCRYPTED" "$OUTPUT_DIR/quarterly-$STAMP.pmbak"
fi

ls -1t "$OUTPUT_DIR"/daily-*.pmbak 2>/dev/null | tail -n +8 | xargs -r rm -f
ls -1t "$OUTPUT_DIR"/daily-*.pmbak.sha256 2>/dev/null | tail -n +8 | xargs -r rm -f
ls -1t "$OUTPUT_DIR"/daily-*.pmbak.hmac 2>/dev/null | tail -n +8 | xargs -r rm -f
ls -1t "$OUTPUT_DIR"/weekly-*.pmbak 2>/dev/null | tail -n +5 | xargs -r rm -f
ls -1t "$OUTPUT_DIR"/quarterly-*.pmbak 2>/dev/null | tail -n +4 | xargs -r rm -f
printf '%s\n' "$ENCRYPTED"
