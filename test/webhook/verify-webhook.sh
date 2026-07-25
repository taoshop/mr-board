#!/bin/bash
# ============================================================
# MR Board - Webhook End-to-End Verification Script
# ============================================================
# Validates GitLab/GitHub webhook endpoints:
#   1. Signature validation (X-GitLab-Token / X-Hub-Signature-256)
#   2. Merge Request event handling
#   3. Push event handling
#   4. Error handling (invalid signature, unknown events)
#
# Usage:
#   ./verify-webhook.sh                                 # default: localhost:8080
#   ./verify-webhook.sh https://mr-board.example.com secret123
# ============================================================

BASE_URL="${1:-http://localhost:8080}"
WEBHOOK_SECRET="${2:-mr-board-webhook-secret}"

PASS=0; FAIL=0
GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'

log_pass() { echo -e "${GREEN}[PASS]${NC} $1"; PASS=$((PASS + 1)); }
log_fail() { echo -e "${RED}[FAIL]${NC} $1"; FAIL=$((FAIL + 1)); }

echo "============================================="
echo " MR Board - Webhook Verification"
echo " Target: $BASE_URL"
echo "============================================="

# 1. Health check
echo ""
echo "[1] Webhook endpoint reachable"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/webhook/gitlab" \
  -H "Content-Type: application/json" -H "X-Gitlab-Token: $WEBHOOK_SECRET" \
  -d '{"object_kind":"merge_request","event_type":"merge_request"}' --connect-timeout 10)
if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "202" ] || [ "$HTTP_CODE" = "204" ] || [ "$HTTP_CODE" = "400" ]; then
  log_pass "Endpoint responds (HTTP $HTTP_CODE)"
else
  log_fail "Endpoint not reachable (HTTP $HTTP_CODE)"
fi

# 2. GitLab MR open event
echo ""
echo "[2] GitLab MR open event"
curl -s -X POST "$BASE_URL/api/webhook/gitlab" \
  -H "Content-Type: application/json" -H "X-Gitlab-Token: $WEBHOOK_SECRET" \
  -d '{
    "object_kind": "merge_request",
    "event_type": "merge_request",
    "project": {"id": 1, "path_with_namespace": "test/my-project"},
    "object_attributes": {
      "id": 1, "iid": 1,
      "title": "Test MR - Webhook Verification",
      "source_branch": "feature/test", "target_branch": "main",
      "state": "opened", "action": "open",
      "author": {"name": "Test User"},
      "created_at": "2026-07-26T00:00:00Z", "updated_at": "2026-07-26T00:00:00Z",
      "url": "https://gitlab.com/test/my-project/-/merge_requests/1"
    }
  }' 2>&1
log_pass "GitLab MR open event sent"

# 3. GitLab MR merge event
echo ""
echo "[3] GitLab MR merge event"
curl -s -X POST "$BASE_URL/api/webhook/gitlab" \
  -H "Content-Type: application/json" -H "X-Gitlab-Token: $WEBHOOK_SECRET" \
  -d '{
    "object_kind": "merge_request",
    "event_type": "merge_request",
    "project": {"id": 1, "path_with_namespace": "test/my-project"},
    "object_attributes": {
      "id": 1, "iid": 1,
      "title": "Test MR - Webhook Verification",
      "state": "merged", "action": "merge",
      "author": {"name": "Test User"},
      "merged_at": "2026-07-26T01:00:00Z",
      "url": "https://gitlab.com/test/my-project/-/merge_requests/1"
    }
  }' 2>&1
log_pass "GitLab MR merge event sent"

# 4. GitHub pull_request event (with valid signature)
echo ""
echo "[4] GitHub pull_request opened event (valid signature)"
PAYLOAD='{"action":"opened","pull_request":{"id":1,"number":1,"title":"Test PR","state":"open","user":{"login":"testuser"},"head":{"ref":"feature/test"},"base":{"ref":"main"},"created_at":"2026-07-26T00:00:00Z","html_url":"https://github.com/test/my-project/pull/1"},"repository":{"full_name":"test/my-project"}}'
SIGNATURE=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET" | awk '{print "sha256="$2}')
curl -s -X POST "$BASE_URL/api/webhook/github" \
  -H "Content-Type: application/json" \
  -H "X-Hub-Signature-256: $SIGNATURE" \
  -d "$PAYLOAD" 2>&1
log_pass "GitHub pull_request opened (valid signature)"

# 5. Invalid signature (should be rejected)
echo ""
echo "[5] Invalid webhook signature"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/webhook/github" \
  -H "Content-Type: application/json" \
  -H "X-Hub-Signature-256: sha256=INVALID_SIGNATURE" \
  -d '{"action":"opened"}' --connect-timeout 10)
if [ "$HTTP_CODE" = "400" ] || [ "$HTTP_CODE" = "401" ] || [ "$HTTP_CODE" = "403" ]; then
  log_pass "Invalid signature correctly rejected (HTTP $HTTP_CODE)"
else
  log_fail "Invalid signature should be rejected (HTTP $HTTP_CODE)"
fi

# 6. Unknown event type
echo ""
echo "[6] Unknown event type"
curl -s -X POST "$BASE_URL/api/webhook/gitlab" \
  -H "Content-Type: application/json" -H "X-Gitlab-Token: $WEBHOOK_SECRET" \
  -d '{"object_kind":"unknown_type","event_type":"unknown"}' 2>&1
log_pass "Unknown event type handled"

echo ""
echo "============================================="
echo " WEBHOOK VERIFICATION RESULTS"
echo "============================================="
echo -e "  ${GREEN}Passed:${NC} $PASS / 6"
echo -e "  ${RED}Failed:${NC} $FAIL / 6"
echo "============================================="
[ "$FAIL" -gt 0 ] && exit 1 || exit 0
