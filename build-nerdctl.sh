#!/bin/bash
# ============================================================
# nerdctl 镜像构建脚本（含依赖缓存优化）
# MR Board System
# ============================================================

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 镜像标签
TAG=${1:-latest}
REGISTRY=${2:-""}

# 如果指定了仓库地址，拼接完整镜像名
if [ -n "$REGISTRY" ]; then
    BACKEND_IMAGE="${REGISTRY}/mr-board-backend:${TAG}"
    FRONTEND_IMAGE="${REGISTRY}/mr-board-frontend:${TAG}"
else
    BACKEND_IMAGE="mr-board-backend:${TAG}"
    FRONTEND_IMAGE="mr-board-frontend:${TAG}"
fi

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}  MR Board - nerdctl 镜像构建${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""
echo -e "后端镜像: ${GREEN}${BACKEND_IMAGE}${NC}"
echo -e "前端镜像: ${GREEN}${FRONTEND_IMAGE}${NC}"
echo ""

# ============================================================
# 检查 nerdctl 是否可用
# ============================================================
if ! command -v nerdctl &> /dev/null; then
    echo -e "${RED}错误: nerdctl 未安装或未在 PATH 中${NC}"
    echo "请安装 nerdctl: https://github.com/containerd/nerdctl"
    exit 1
fi

echo -e "${YELLOW}[1/3] nerdctl 版本信息:${NC}"
nerdctl version

echo ""

# ============================================================
# 构建后端镜像
# ============================================================
echo -e "${YELLOW}[2/3] 构建后端镜像...${NC}"
cd "$(dirname "$0")/mr-board"

# nerdctl build 命令说明：
# --build-arg BUILDKIT_INLINE_CACHE=1 : 启用内联缓存，便于后续构建复用
# --progress=plain : 显示详细构建日志
# --no-cache-filter=builder : 可选：强制重新编译阶段，但保留依赖阶段缓存

echo -e "${GREEN}执行: nerdctl build -f Dockerfile.backend -t ${BACKEND_IMAGE} .${NC}"
nerdctl build \
    -f Dockerfile.backend \
    -t "${BACKEND_IMAGE}" \
    --build-arg BUILDKIT_INLINE_CACHE=1 \
    --progress=plain \
    .

echo -e "${GREEN}后端镜像构建完成: ${BACKEND_IMAGE}${NC}"
echo ""

# ============================================================
# 构建前端镜像
# ============================================================
cd "$(dirname "$0")/mr-board-frontend"

echo -e "${YELLOW}[3/3] 构建前端镜像...${NC}"
echo -e "${GREEN}执行: nerdctl build -f Dockerfile -t ${FRONTEND_IMAGE} .${NC}"
nerdctl build \
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
echo -e "${YELLOW}  构建完成！${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""
nerdctl images | grep -E "mr-board"
echo ""
echo "使用方式:"
echo -e "  后端: ${GREEN}nerdctl run -p 8080:8080 ${BACKEND_IMAGE}${NC}"
echo -e "  前端: ${GREEN}nerdctl run -p 80:80 ${FRONTEND_IMAGE}${NC}"
