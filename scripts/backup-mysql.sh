#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# EuroJob Copilot - MySQL Automated Backup Script
# Creates consistent compressed logical backups (--single-transaction),
# generates SHA-256 verification checksums, and prunes expired archives.
#
# Usage:
#   ./scripts/backup-mysql.sh [--mode docker|native] [--output-dir ./backups] [--retention-days 14] [--env-file .env]
# ==============================================================================

MODE="docker"
CONTAINER_NAME="eurojob-mysql"
OUTPUT_DIR="./backups"
RETENTION_DAYS=14
ENV_FILE=".env"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode)
      MODE="${2:?Missing --mode value}"
      shift 2
      ;;
    --container)
      CONTAINER_NAME="${2:?Missing --container value}"
      shift 2
      ;;
    --output-dir)
      OUTPUT_DIR="${2:?Missing --output-dir value}"
      shift 2
      ;;
    --retention-days)
      RETENTION_DAYS="${2:?Missing --retention-days value}"
      shift 2
      ;;
    --env-file)
      ENV_FILE="${2:?Missing --env-file value}"
      shift 2
      ;;
    -h|--help)
      sed -n '3,12p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

read_env_var() {
  local key="$1"
  local default_val="${2:-}"
  if [[ -f "$ENV_FILE" ]]; then
    local val
    val="$(grep -E "^${key}=" "$ENV_FILE" | tail -n 1 | cut -d '=' -f 2- | sed -E 's/^["'\'']|["'\'']$//g' || true)"
    if [[ -n "$val" ]]; then
      printf '%s' "$val"
      return 0
    fi
  fi
  printf '%s' "$default_val"
}

DB_NAME="${MYSQL_DATABASE:-$(read_env_var MYSQL_DATABASE eurojob_copilot)}"
DB_USER="${MYSQL_BACKUP_USER:-root}"
DB_PASSWORD="${MYSQL_ROOT_PASSWORD:-$(read_env_var MYSQL_ROOT_PASSWORD '')}"
DB_HOST="${DB_HOST:-$(read_env_var DB_HOST 127.0.0.1)}"
DB_PORT="${MYSQL_EXTERNAL_PORT:-$(read_env_var MYSQL_EXTERNAL_PORT 3307)}"

if [[ -z "$DB_PASSWORD" ]]; then
  echo "ERROR: MYSQL_ROOT_PASSWORD must be exported or set in ${ENV_FILE}." >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_FILE="${OUTPUT_DIR}/${DB_NAME}_${TIMESTAMP}.sql.gz"
TMP_FILE="${BACKUP_FILE}.tmp"
CHECKSUM_FILE="${BACKUP_FILE}.sha256"

echo "[backup] Starting MySQL backup (mode=${MODE}, db=${DB_NAME})..."

cleanup_tmp() {
  rm -f "$TMP_FILE"
}
trap cleanup_tmp EXIT

DUMP_ARGS=(
  --single-transaction
  --quick
  --routines
  --triggers
  --events
  --hex-blob
  --default-character-set=utf8mb4
  --no-tablespaces
  "$DB_NAME"
)

if [[ "$MODE" == "docker" ]]; then
  docker exec -e "MYSQL_PWD=${DB_PASSWORD}" -i "$CONTAINER_NAME" \
    mysqldump -u "$DB_USER" "${DUMP_ARGS[@]}" | gzip -c > "$TMP_FILE"
elif [[ "$MODE" == "native" ]]; then
  MYSQL_PWD="$DB_PASSWORD" mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" \
    "${DUMP_ARGS[@]}" | gzip -c > "$TMP_FILE"
else
  echo "ERROR: Invalid mode '${MODE}'. Expected 'docker' or 'native'." >&2
  exit 1
fi

if ! gzip -t "$TMP_FILE"; then
  echo "ERROR: Backup archive integrity check (gzip -t) failed." >&2
  exit 1
fi

mv "$TMP_FILE" "$BACKUP_FILE"

if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$BACKUP_FILE" > "$CHECKSUM_FILE"
elif command -v shasum >/dev/null 2>&1; then
  shasum -a 256 "$BACKUP_FILE" > "$CHECKSUM_FILE"
else
  echo "WARNING: Neither sha256sum nor shasum found; skipping checksum file." >&2
fi

if [[ "$RETENTION_DAYS" =~ ^[0-9]+$ ]] && [[ "$RETENTION_DAYS" -gt 0 ]]; then
  find "$OUTPUT_DIR" -maxdepth 1 -type f \( -name "${DB_NAME}_*.sql.gz" -o -name "${DB_NAME}_*.sql.gz.sha256" \) -mtime "+${RETENTION_DAYS}" -delete
fi

SIZE_BYTES="$(wc -c < "$BACKUP_FILE" | tr -d ' ')"
echo "[backup] Completed: ${BACKUP_FILE} (${SIZE_BYTES} bytes)"

