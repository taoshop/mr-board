#!/bin/bash
# ============================================================
# 远程 BuildKit (nerdctl) 镜像构建脚本
# BuildKit 服务端: 192.168.71.250
# MR Board System
# ============================================================

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 配置
export BUILDKIT_HOST="tcp://192.168.71.250:1234"
TAG=${1:-latest}
REGISTRY=${2:-""}

# 镜像名
if [ -n "$REGISTRY" ]; then
    BACKEND_IMAGE="${REGISTRY}/mr-board-backend:${TAG}"
    FRONTEND_IMAGE="${REGISTRY}/mr-board-frontend:${TAG}"
else
    BACKEND_IMAGE="mr-board-backend:${TAG}"
    FRONTEND_IMAGE="mr-board-frontend:${TAG}"
fi

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}  MR Board - nerdctl + 远程 BuildKit${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""
echo -e "BuildKit 服务端: ${BLUE}${BUILDKIT_HOST}${NC}"
echo -e "后端镜像:        ${GREEN}${BACKEND_IMAGE}${NC}"
echo -e "前端镜像:        ${GREEN}${FRONTEND_IMAGE}${NC}"
echo ""

# ============================================================
# 检查 nerdctl 是否可用
# ============================================================
if ! command -v nerdctl &> /dev/null; then
    echo -e "${RED}错误: nerdctl 未安装${NC}"
    exit 1
fi

echo -e "${YELLOW}[1/3] nerdctl 版本信息:${NC}"
nerdctl version

echo ""

# ============================================================
# 构建后端镜像（使用远程 BuildKit）
# ============================================================
echo -e "${YELLOW}[2/3] 构建后端镜像...${NC}"
cd "$(dirname "$0")/mr-board"

# nerdctl 通过环境变量 BUILDKIT_HOST 或 --buildkit-host 指定远程 BuildKit
echo -e "${GREEN}执行: nerdctl build --buildkit-host=${BUILDKIT_HOST} ...${NC}"
nerdctl build \
    --buildkit-host "${BUILDKIT_HOST}" \
    -f Dockerfile.backend \
    -t "${BACKEND_IMAGE}" \
    --build-arg BUILDKIT_INLINE_CACHE=1 \
    --progress=plain \
    .

echo -e "${GREEN}后端镜像构建完成: ${BACKEND_IMAGE}${NC}"
echo ""

# ============================================================
# 构建前端镜像（使用远程 BuildKit）
# ============================================================
cd "$(dirname "$0")/mr-board-frontend"

echo -e "${YELLOW}[3/3] 构建前端镜像...${NC}"
echo -e "${GREEN}执行: nerdctl build --buildkit-host=${BUILDKIT_HOST} ...${NC}"
nerdctl build \
    --buildkit-host "${BUILDKIT_HOST}" \
    -f Dockerfile \
    -t "${FRONTEND_IMAGE}" \
    --build-arg BUILDKIT_INLINE_CACHE=1 \
    --progress=plain \
    .

echo -e "${GREEN}前端镜像构建完成: ${FRONTEND_IMAGE}${NC}"
echo ""

# ============================================================
# 构建结果
# ============================================================
echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}  远程 BuildKit 构建完成！${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""
echo "使用方式:"
echo -e "  后端: ${GREEN}nerdctl run -p 8080:8080 ${BACKEND_IMAGE}${NC}"
echo -e "  前端: ${GREEN}nerdctl run -p 80:80 ${FRONTEND_IMAGE}${NC}"
