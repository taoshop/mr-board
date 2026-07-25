#!/bin/bash
# ============================================================
# MR Board - JMeter 并发压测执行脚本
# ============================================================
# Prerequisites:
#   - JMeter 5.6+ installed (https://jmeter.apache.org/)
#   - Or run: docker run --network=host -v $(pwd):/test -it justb4/jmeter
#
# Usage:
#   ./run-stress-test.sh                          # default: 100 users, localhost:8080
#   ./run-stress-test.sh http://192.168.1.100:8080  # custom base URL
#   ./run-stress-test.sh http://prod:443 admin password123 50 30 120
#
# Parameters (positional):
#   1: BASE_URL          (default: http://localhost:8080)
#   2: ADMIN_USER        (default: admin)
#   3: ADMIN_PASS        (default: Admin@123)
#   4: CONCURRENT_USERS  (default: 100)
#   5: RAMP_UP_SECONDS   (default: 30)
#   6: DURATION_SECONDS  (default: 120)
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RESULT_DIR="${SCRIPT_DIR}/results"
mkdir -p "$RESULT_DIR"

BASE_URL="${1:-http://localhost:8080}"
ADMIN_USER="${2:-admin}"
ADMIN_PASS="${3:-Admin@123}"
CONCURRENT_USERS="${4:-100}"
RAMP_UP_SECONDS="${5:-30}"
DURATION_SECONDS="${6:-120}"

echo "============================================="
echo " MR Board - 并发压力测试"
echo "============================================="
echo "  Base URL:           $BASE_URL"
echo "  Admin User:         $ADMIN_USER"
echo "  Concurrent Users:   $CONCURRENT_USERS"
echo "  Ramp-up Seconds:    $RAMP_UP_SECONDS"
echo "  Duration Seconds:   $DURATION_SECONDS"
echo "  Result Directory:   $RESULT_DIR"
echo "============================================="
echo ""

JMETER_CMD="jmeter"
if command -v jmeter &> /dev/null; then
  JMETER_CMD="jmeter"
elif [ -n "$JMETER_HOME" ] && [ -f "$JMETER_HOME/bin/jmeter" ]; then
  JMETER_CMD="$JMETER_HOME/bin/jmeter"
else
  echo "[WARN] JMeter not found in PATH. Trying Docker..."
  # Docker-based execution
  docker run --rm --network=host \
    -v "$SCRIPT_DIR:/test" \
    -v "$RESULT_DIR:/results" \
    -it justb4/jmeter:latest \
    -n -t /test/mr-board-stress-test.jmx \
    -JbaseUrl="$BASE_URL" \
    -JadminUser="$ADMIN_USER" \
    -JadminPass="$ADMIN_PASS" \
    -JconcurrentUsers="$CONCURRENT_USERS" \
    -JrampUpSeconds="$RAMP_UP_SECONDS" \
    -JdurationSeconds="$DURATION_SECONDS" \
    -JresultDir="/results" \
    -l "/results/stress-test-$(date +%Y%m%d-%H%M%S).jtl" \
    -e -o "/results/html-report"
  exit $?
fi

# Native JMeter execution
$JMETER_CMD -n -t "$SCRIPT_DIR/mr-board-stress-test.jmx" \
  -JbaseUrl="$BASE_URL" \
  -JadminUser="$ADMIN_USER" \
  -JadminPass="$ADMIN_PASS" \
  -JconcurrentUsers="$CONCURRENT_USERS" \
  -JrampUpSeconds="$RAMP_UP_SECONDS" \
  -JdurationSeconds="$DURATION_SECONDS" \
  -JresultDir="$RESULT_DIR" \
  -l "$RESULT_DIR/stress-test-$(date +%Y%m%d-%H%M%S).jtl" \
  -e -o "$RESULT_DIR/html-report"

echo ""
echo "[OK] Stress test completed!"
echo "  JTL results: $RESULT_DIR/"
echo "  HTML report: $RESULT_DIR/html-report/index.html"
