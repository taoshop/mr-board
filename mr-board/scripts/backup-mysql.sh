#!/bin/bash
# MR Board MySQL 每日全量备份脚本
# 保留最近 7 天备份，自动清理过期文件

set -e

# 配置
DB_HOST="${MYSQL_HOST:-localhost}"
DB_PORT="${MYSQL_PORT:-3306}"
DB_NAME="${MYSQL_DB:-mr_board}"
DB_USER="${MYSQL_USER:-root}"
DB_PASS="${MYSQL_PASSWORD}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/mr-board}"
RETENTION_DAYS=7
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/mr_board_${DATE}.sql"

# 创建备份目录
mkdir -p "${BACKUP_DIR}"

# 执行备份
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting backup ${DB_NAME}..."
mysqldump -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USER}" -p"${DB_PASS}" \
  --single-transaction \
  --routines \
  --triggers \
  --databases "${DB_NAME}" \
  > "${BACKUP_FILE}"

# 压缩
gzip -f "${BACKUP_FILE}"
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Backup completed: ${BACKUP_FILE}.gz"

# 清理过期备份
find "${BACKUP_DIR}" -name "mr_board_*.sql.gz" -mtime +${RETENTION_DAYS} -delete
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Cleaned up backups older than ${RETENTION_DAYS} days"
