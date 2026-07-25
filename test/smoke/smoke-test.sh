#!/bin/bash
# ============================================================
# MR Board System - Smoke Test Script
# ============================================================
# Verifies the core flow after deployment:
#   Health → Login → Board → Projects → Reports → Users → Export
#
# Usage:
#   ./smoke-test.sh                        # default: localhost:8080
#   ./smoke-test.sh https://mr-board.example.com  # custom URL
#   ./smoke-test.sh http://localhost:8080 admin Admin@123
#
# Exit codes: 0 = all passed, 1 = one or more tests failed
# ============================================================

set -e

BASE_URL="${1:-http://localhost:8080}"
ADMIN_USER="${2:-admin}"
ADMIN_PASS="${3:-Admin@123}"

PASS=0
FAIL=0
TOKEN=""
RESULT_FILE="/tmp/mr-board-smoke-$$.log"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_pass() { echo -e "${GREEN}[PASS]${NC} $1"; PASS=$((PASS + 1)); }
log_fail() { echo -e "${RED}[FAIL]${NC} $1 — $2"; FAIL=$((FAIL + 1)); }
log_info() { echo -e "${YELLOW}[INFO]${NC} $1"; }

header() {
  echo ""
  echo "============================================="
  echo " $1"
  echo "============================================="
}

cleanup() { rm -f "$RESULT_FILE"; }
trap cleanup EXIT

# -----------------------------------------------------------
# 1. Health Check
# -----------------------------------------------------------
header "1. Health Check"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api" --connect-timeout 10 || echo "000")
if [ "$HTTP_CODE" -ge 200 ] && [ "$HTTP_CODE" -lt 500 ]; then
  log_pass "Backend reachable (HTTP $HTTP_CODE)"
else
  log_fail "Backend not reachable" "HTTP $HTTP_CODE"
fi

# -----------------------------------------------------------
# 2. Login
# -----------------------------------------------------------
header "2. Login"
LOGIN_RESP=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" \
  --connect-timeout 10 2>&1) || LOGIN_RESP=""

TOKEN=$(echo "$LOGIN_RESP" | grep -o '"accessToken":"[^"]*"' | head -1 | sed 's/"accessToken":"//;s/"//')
if [ -n "$TOKEN" ]; then
  log_pass "Login successful, got access token"
else
  log_fail "Login failed" "Response: ${LOGIN_RESP:0:200}"
fi

AUTH_HEADER="Authorization: Bearer $TOKEN"

# -----------------------------------------------------------
# 3. Current User Info
# -----------------------------------------------------------
header "3. Current User"
ME_RESP=$(curl -s "$BASE_URL/api/auth/me" -H "$AUTH_HEADER" --connect-timeout 10)
ME_CODE=$(echo "$ME_RESP" | grep -o '"code":\s*[0-9]*' | head -1 | grep -o '[0-9]*')
if [ "$ME_CODE" = "200" ]; then
  log_pass "GET /api/auth/me → 200"
else
  log_fail "GET /api/auth/me" "code=$ME_CODE"
fi

# -----------------------------------------------------------
# 4. Board Columns
# -----------------------------------------------------------
header "4. Board Columns"
COL_RESP=$(curl -s "$BASE_URL/api/board/columns" -H "$AUTH_HEADER" --connect-timeout 10)
COL_CODE=$(echo "$COL_RESP" | grep -o '"code":\s*[0-9]*' | head -1 | grep -o '[0-9]*')
if [ "$COL_CODE" = "200" ]; then
  COL_COUNT=$(echo "$COL_RESP" | grep -o '"key"' | wc -l)
  log_pass "GET /api/board/columns → 200 ($COL_COUNT columns)"
else
  log_fail "GET /api/board/columns" "code=$COL_CODE"
fi

# -----------------------------------------------------------
# 5. Board Data (Dashboard)
# -----------------------------------------------------------
header "5. Board Dashboard"

# 5a: Board data
BOARD_RESP=$(curl -s "$BASE_URL/api/board" -H "$AUTH_HEADER" --connect-timeout 10)
BOARD_CODE=$(echo "$BOARD_RESP" | grep -o '"code":\s*[0-9]*' | head -1 | grep -o '[0-9]*')
BOARD_TIME=$(curl -s -o /dev/null -w "%{time_total}" "$BASE_URL/api/board" -H "$AUTH_HEADER" --connect-timeout 10)
if [ "$BOARD_CODE" = "200" ]; then
  log_pass "GET /api/board → 200 (${BOARD_TIME}s)"
else
  log_fail "GET /api/board" "code=$BOARD_CODE"
fi

# 5b: Board stats
STATS_RESP=$(curl -s "$BASE_URL/api/board/stats" -H "$AUTH_HEADER" --connect-timeout 10)
STATS_CODE=$(echo "$STATS_RESP" | grep -o '"code":\s*[0-9]*' | head -1 | grep -o '[0-9]*')
if [ "$STATS_CODE" = "200" ]; then
  log_pass "GET /api/board/stats → 200"
else
  log_fail "GET /api/board/stats" "code=$STATS_CODE"
fi

# 5c: Board projects
PROJ_RESP=$(curl -s "$BASE_URL/api/board/projects" -H "$AUTH_HEADER" --connect-timeout 10)
PROJ_CODE=$(echo "$PROJ_RESP" | grep -o '"code":\s*[0-9]*' | head -1 | grep -o '[0-9]*')
if [ "$PROJ_CODE" = "200" ]; then
  log_pass "GET /api/board/projects → 200"
else
  log_fail "GET /api/board/projects" "code=$PROJ_CODE"
fi

# -----------------------------------------------------------
# 6. Projects / Git Sources
# -----------------------------------------------------------
header "6. Projects & Git Sources"

GITSRC_RESP=$(curl -s "$BASE_URL/api/git-sources" -H "$AUTH_HEADER" --connect-timeout 10)
GITSRC_CODE=$(echo "$GITSRC_RESP" | grep -o '"code":\s*[0-9]*' | head -1 | grep -o '[0-9]*')
if [ "$GITSRC_CODE" = "200" ]; then
  log_pass "GET /api/git-sources → 200"
else
  log_fail "GET /api/git-sources" "code=$GITSRC_CODE"
fi

PROJLIST_RESP=$(curl -s "$BASE_URL/api/projects?page=1&size=10" -H "$AUTH_HEADER" --connect-timeout 10)
PROJLIST_CODE=$(echo "$PROJLIST_RESP" | grep -o '"code":\s*[0-9]*' | head -1 | grep -o '[0-9]*')
if [ "$PROJLIST_CODE" = "200" ]; then
  log_pass "GET /api/projects → 200"
else
  log_fail "GET /api/projects" "code=$PROJLIST_CODE"
fi

# -----------------------------------------------------------
# 7. Reports
# -----------------------------------------------------------
header "7. Reports"

# 7a: Summary
SUMMARY_RESP=$(curl -s "$BASE_URL/api/reports/summary" -H "$AUTH_HEADER" --connect-timeout 10)
SUMMARY_CODE=$(echo "$SUMMARY_RESP" | grep -o '"code":\s*[0-9]*' | head -1 | grep -o '[0-9]*')
if [ "$SUMMARY_CODE" = "200" ]; then
  log_pass "GET /api/reports/summary → 200"
else
  log_fail "GET /api/reports/summary" "code=$SUMMARY_CODE"
fi

# 7b: Daily stats
DAILY_RESP=$(curl -s "$BASE_URL/api/reports/daily" -H "$AUTH_HEADER" --connect-timeout 10)
DAILY_CODE=$(echo "$DAILY_RESP" | grep -o '"code":\s*[0-9]*' | head -1 | grep -o '[0-9]*')
if [ "$DAILY_CODE" = "200" ]; then
  log_pass "GET /api/reports/daily → 200"
else
  log_fail "GET /api/reports/daily" "code=$DAILY_CODE"
fi

# 7c: Export (async — submit only)
EXPORT_RESP=$(curl -s -X POST "$BASE_URL/api/reports/export/async" \
  -H "$AUTH_HEADER" -H "Content-Type: application/json" \
  -d '{"format":"csv"}' --connect-timeout 10)
EXPORT_CODE=$(echo "$EXPORT_RESP" | grep -o '"code":\s*[0-9]*' | head -1 | grep -o '[0-9]*')
if [ "$EXPORT_CODE" = "200" ]; then
  TASK_ID=$(echo "$EXPORT_RESP" | grep -o '"taskId":"[^"]*"' | head -1 | sed 's/"taskId":"//;s/"//')
  log_pass "POST /api/reports/export/async → 200 (taskId=$TASK_ID)"
else
  log_info "POST /api/reports/export/async → code=$EXPORT_CODE (may require data)"
fi

# -----------------------------------------------------------
# 8. User Management (Admin only)
# -----------------------------------------------------------
header "8. User Management"
USERS_RESP=$(curl -s "$BASE_URL/api/admin/users?page=1&size=10" -H "$AUTH_HEADER" --connect-timeout 10)
USERS_CODE=$(echo "$USERS_RESP" | grep -o '"code":\s*[0-9]*' | head -1 | grep -o '[0-9]*')
if [ "$USERS_CODE" = "200" ]; then
  log_pass "GET /api/admin/users → 200"
else
  log_fail "GET /api/admin/users" "code=$USERS_CODE"
fi

# -----------------------------------------------------------
# 9. Sync Logs
# -----------------------------------------------------------
header "9. Sync Logs"
SYNC_RESP=$(curl -s "$BASE_URL/api/admin/sync-logs?page=1&size=10" -H "$AUTH_HEADER" --connect-timeout 10)
SYNC_CODE=$(echo "$SYNC_RESP" | grep -o '"code":\s*[0-9]*' | head -1 | grep -o '[0-9]*')
if [ "$SYNC_CODE" = "200" ]; then
  log_pass "GET /api/admin/sync-logs → 200"
else
  log_fail "GET /api/admin/sync-logs" "code=$SYNC_CODE"
fi

# -----------------------------------------------------------
# 10. Auth — Token Refresh
# -----------------------------------------------------------
header "10. Token Refresh"
REFRESH_RESP=$(curl -s -X POST "$BASE_URL/api/auth/refresh" \
  -H "$AUTH_HEADER" --connect-timeout 10)
REFRESH_CODE=$(echo "$REFRESH_RESP" | grep -o '"code":\s*[0-9]*' | head -1 | grep -o '[0-9]*')
if [ "$REFRESH_CODE" = "200" ]; then
  log_pass "POST /api/auth/refresh → 200"
else
  log_fail "POST /api/auth/refresh" "code=$REFRESH_CODE"
fi

# -----------------------------------------------------------
# 11. Authorization — 403 check
# -----------------------------------------------------------
header "11. Authorization Checks"
NOAUTH_RESP=$(curl -s "$BASE_URL/api/admin/users" --connect-timeout 10)
NOAUTH_CODE=$(echo "$NOAUTH_RESP" | grep -o '"code":\s*[0-9]*' | head -1 | grep -o '[0-9]*')
if [ "$NOAUTH_CODE" != "200" ]; then
  log_pass "GET /api/admin/users (no token) → correctly rejected (code=$NOAUTH_CODE)"
else
  log_fail "GET /api/admin/users (no token)" "expected rejection, got 200"
fi

# -----------------------------------------------------------
# Summary
# -----------------------------------------------------------
TOTAL=$((PASS + FAIL))
echo ""
echo "============================================="
echo " SMOKE TEST RESULTS"
echo "============================================="
echo -e "  ${GREEN}Passed:${NC} $PASS / $TOTAL"
echo -e "  ${RED}Failed:${NC} $FAIL / $TOTAL"
echo "  Target:  $BASE_URL"
echo "============================================="

if [ "$FAIL" -gt 0 ]; then
  echo ""
  echo -e "${RED}SMOKE TEST FAILED — $FAIL checks did not pass.${NC}"
  exit 1
else
  echo ""
  echo -e "${GREEN}SMOKE TEST PASSED — All $PASS checks passed!${NC}"
  exit 0
fi
