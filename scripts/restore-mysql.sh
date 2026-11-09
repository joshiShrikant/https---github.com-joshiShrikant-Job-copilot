#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# EuroJob Copilot - MySQL Disaster Recovery / Restore Script
# Verifies SHA-256 checksum and gzip integrity before restoring into MySQL.
#
# Usage:
#   ./scripts/restore-mysql.sh --file ./backups/eurojob_copilot_YYYYMMDDTHHMMSSZ.sql.gz [--mode docker|native] [--yes]
# ==============================================================================

MODE="docker"
CONTAINER_NAME="eurojob-mysql"
BACKUP_FILE=""
ENV_FILE=".env"
CONFIRMED=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --file)
      BACKUP_FILE="${2:?Missing --file value}"
      shift 2
      ;;
    --mode)
      MODE="${2:?Missing --mode value}"
      shift 2
      ;;
    --container)
      CONTAINER_NAME="${2:?Missing --container value}"
      shift 2
      ;;
    --env-file)
      ENV_FILE="${2:?Missing --env-file value}"
      shift 2
      ;;
    --yes)
      CONFIRMED=true
      shift
      ;;
    -h|--help)
      sed -n '3,11p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

if [[ -z "$BACKUP_FILE" || ! -f "$BACKUP_FILE" ]]; then
  echo "ERROR: Backup file does not exist: ${BACKUP_FILE}" >&2
  exit 1
fi

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

CHECKSUM_FILE="${BACKUP_FILE}.sha256"
if [[ -f "$CHECKSUM_FILE" ]]; then
  echo "[restore] Verifying SHA-256 checksum..."
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum -c "$CHECKSUM_FILE"
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 -c "$CHECKSUM_FILE"
  fi
fi

echo "[restore] Verifying gzip archive integrity..."
gzip -t "$BACKUP_FILE"

if [[ "$CONFIRMED" != true ]]; then
  read -r -p "This will overwrite tables in '${DB_NAME}'. Continue? [y/N]: " reply
  if [[ ! "$reply" =~ ^[Yy]$ ]]; then
    echo "Restore cancelled."
    exit 1
  fi
fi

echo "[restore] Restoring ${BACKUP_FILE} into '${DB_NAME}' (mode=${MODE})..."
if [[ "$MODE" == "docker" ]]; then
  gzip -dc "$BACKUP_FILE" | docker exec -e "MYSQL_PWD=${DB_PASSWORD}" -i "$CONTAINER_NAME" \
    mysql -u "$DB_USER" "$DB_NAME"
elif [[ "$MODE" == "native" ]]; then
  gzip -dc "$BACKUP_FILE" | MYSQL_PWD="$DB_PASSWORD" mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME"
else
  echo "ERROR: Invalid mode '${MODE}'. Expected 'docker' or 'native'." >&2
  exit 1
fi

echo "[restore] Completed successfully."

