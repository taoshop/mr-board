#!/bin/bash
# ============================================================
# MR Board - Docker Compose E2E Integration Test
# ============================================================
# Full-stack integration test:
#   1. Start all services (mysql, redis, backend, frontend, nginx)
#   2. Wait for healthy status
#   3. Run smoke tests
#   4. Report results
#
# Usage:
#   ./docker-e2e.sh           # default: run everything
#   ./docker-e2e.sh --clean   # remove containers after test
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$(dirname "$SCRIPT_DIR")")"
SMOKE_SCRIPT="$PROJECT_DIR/test/smoke/smoke-test.sh"
CLEANUP=false

if [ "$1" = "--clean" ]; then
  CLEANUP=true
fi

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

echo "============================================="
echo " MR Board - Docker Compose E2E Test"
echo " Project: $PROJECT_DIR"
echo "============================================="

# 1. Start services
echo ""
echo -e "${YELLOW}[1/4] Starting Docker services...${NC}"
cd "$PROJECT_DIR"
docker compose up -d --wait 2>&1 || {
  echo -e "${RED}Failed to start services${NC}"
  exit 1
}
echo -e "${GREEN}Services started.${NC}"

# 2. Check healthy
echo ""
echo -e "${YELLOW}[2/4] Waiting for health checks...${NC}"
for i in $(seq 1 60); do
  MYSQL_OK=$(docker compose exec mysql mysqladmin ping -h localhost -u root --password=MrBoard@2026 2>/dev/null && echo 1 || echo 0)
  BACKEND_OK=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health --connect-timeout 3 2>/dev/null || echo "000")
  if [ "$MYSQL_OK" = "1" ] && [ "${BACKEND_OK:0:1}" = "2" ]; then
    echo -e "${GREEN}All services healthy.${NC}"
    break
  fi
  echo "  Waiting... (mysql=$MYSQL_OK backend=$BACKEND_OK)"
  sleep 2
done

# 3. Run smoke tests
echo ""
echo -e "${YELLOW}[3/4] Running smoke tests...${NC}"
if [ -f "$SMOKE_SCRIPT" ]; then
  bash "$SMOKE_SCRIPT" http://localhost:8080 2>&1
  SMOKE_RESULT=$?
else
  echo -e "${RED}Smoke test script not found: $SMOKE_SCRIPT${NC}"
  SMOKE_RESULT=1
fi

# 4. Show status
echo ""
echo -e "${YELLOW}[4/4] Service status...${NC}"
docker compose ps 2>&1

if [ "$SMOKE_RESULT" -eq 0 ]; then
  echo ""
  echo -e "${GREEN}=============================================${NC}"
  echo -e "${GREEN} E2E TEST PASSED${NC}"
  echo -e "${GREEN}=============================================${NC}"
else
  echo ""
  echo -e "${RED}=============================================${NC}"
  echo -e "${RED} E2E TEST FAILED${NC}"
  echo -e "${RED}=============================================${NC}"
fi

if [ "$CLEANUP" = true ]; then
  echo ""
  echo "Cleaning up..."
  docker compose down 2>&1
fi

exit $SMOKE_RESULT
