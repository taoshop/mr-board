#!/bin/bash
# ============================================================
# nerdctl 构建镜像脚本
# BuildKit 服务端: 192.168.71.250:1234
# 使用说明: ./build.sh [tag] [registry]
# ============================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 远程 BuildKit 地址
export BUILDKIT_HOST="tcp://192.168.71.250:1234"
TAG=${1:-latest}
REGISTRY=${2:-""}

if [ -n "$REGISTRY" ]; then
    BACKEND_IMAGE="${REGISTRY}/mr-board-backend:${TAG}"
    FRONTEND_IMAGE="${REGISTRY}/mr-board-frontend:${TAG}"
else
    BACKEND_IMAGE="mr-board-backend:${TAG}"
    FRONTEND_IMAGE="mr-board-frontend:${TAG}"
fi

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}  nerdctl 构建镜像${NC}"
echo -e "${YELLOW}========================================${NC}"
echo -e "BuildKit: ${BLUE}${BUILDKIT_HOST}${NC}"
echo -e "后端:     ${GREEN}${BACKEND_IMAGE}${NC}"
echo -e "前端:     ${GREEN}${FRONTEND_IMAGE}${NC}"
echo ""

# 后端
echo -e "${YELLOW}[1/2] 构建后端镜像...${NC}"
cd "$(dirname "$0")/mr-board"
nerdctl build \
    --buildkit-host "${BUILDKIT_HOST}" \
    -f Dockerfile.backend \
    -t "${BACKEND_IMAGE}" \
    --build-arg BUILDKIT_INLINE_CACHE=1 \
    .

echo -e "${GREEN}✓ 后端构建完成${NC}"
echo ""

# 前端
echo -e "${YELLOW}[2/2] 构建前端镜像...${NC}"
cd "$(dirname "$0")/mr-board-frontend"
nerdctl build \
    --buildkit-host "${BUILDKIT_HOST}" \
    -f Dockerfile \
    -t "${FRONTEND_IMAGE}" \
    --build-arg BUILDKIT_INLINE_CACHE=1 \
    .

echo -e "${GREEN}✓ 前端构建完成${NC}"
echo ""

# 结果
echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}  构建完成${NC}"
echo -e "${YELLOW}========================================${NC}"
nerdctl images | grep "mr-board" || true
