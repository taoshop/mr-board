#!/bin/bash
# ============================================================
# 远程 BuildKit (buildctl) 镜像构建脚本
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
BUILDKIT_HOST="tcp://192.168.71.250:1234"
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
echo -e "${YELLOW}  MR Board - 远程 BuildKit 镜像构建${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""
echo -e "BuildKit 服务端: ${BLUE}${BUILDKIT_HOST}${NC}"
echo -e "后端镜像:        ${GREEN}${BACKEND_IMAGE}${NC}"
echo -e "前端镜像:        ${GREEN}${FRONTEND_IMAGE}${NC}"
echo ""

# ============================================================
# 检查 buildctl 是否可用
# ============================================================
if ! command -v buildctl &> /dev/null; then
    echo -e "${RED}错误: buildctl 未安装${NC}"
    echo "安装方式:"
    echo "  1. 下载: wget https://github.com/moby/buildkit/releases/download/v0.13.2/buildkit-v0.13.2.linux-amd64.tar.gz"
    echo "  2. 解压: tar -xzf buildkit-v0.13.2.linux-amd64.tar.gz -C /usr/local"
    exit 1
fi

echo -e "${YELLOW}[1/4] buildctl 版本信息:${NC}"
buildctl --version

echo ""

# ============================================================
# 测试 BuildKit 连接
# ============================================================
echo -e "${YELLOW}[2/4] 测试 BuildKit 服务端连接...${NC}"
if ! buildctl --addr "${BUILDKIT_HOST}" debug workers &> /dev/null; then
    echo -e "${RED}错误: 无法连接到 BuildKit 服务端 ${BUILDKIT_HOST}${NC}"
    echo "排查建议:"
    echo "  1. 确认服务端 buildkitd 已启动: sudo systemctl status buildkit"
    echo "  2. 确认防火墙放行端口: sudo ufw allow 1234/tcp"
    echo "  3. 测试连通性: telnet 192.168.71.250 1234"
    exit 1
fi
echo -e "${GREEN}BuildKit 服务端连接正常${NC}"
echo ""

# ============================================================
# 构建后端镜像
# ============================================================
echo -e "${YELLOW}[3/4] 构建后端镜像...${NC}"
cd "$(dirname "$0")/mr-board"

# buildctl 构建命令：
# --addr          : 远程 BuildKit 地址
# --frontend      : 使用 dockerfile.v0 前端
# --local context : 构建上下文（当前目录）
# --local dockerfile : Dockerfile 所在目录
# --opt target    : 指定构建阶段（可选）
# --output        : 输出到本地 nerdctl/docker 镜像仓库
# --export-cache  : 导出缓存层
# --import-cache  : 导入缓存层

echo -e "${GREEN}执行 buildctl 构建后端...${NC}"
buildctl \
    --addr "${BUILDKIT_HOST}" \
    build \
    --frontend dockerfile.v0 \
    --local context=. \
    --local dockerfile=. \
    --opt filename=Dockerfile.backend \
    --opt build-arg:BUILDKIT_INLINE_CACHE=1 \
    --output type=image,name="${BACKEND_IMAGE}",push=false \
    --export-cache type=inline \
    --import-cache type=inline

echo -e "${GREEN}后端镜像构建完成: ${BACKEND_IMAGE}${NC}"
echo ""

# ============================================================
# 构建前端镜像
# ============================================================
cd "$(dirname "$0")/mr-board-frontend"

echo -e "${YELLOW}[4/4] 构建前端镜像...${NC}"
echo -e "${GREEN}执行 buildctl 构建前端...${NC}"
buildctl \
    --addr "${BUILDKIT_HOST}" \
    build \
    --frontend dockerfile.v0 \
    --local context=. \
    --local dockerfile=. \
    --opt filename=Dockerfile \
    --opt build-arg:BUILDKIT_INLINE_CACHE=1 \
    --output type=image,name="${FRONTEND_IMAGE}",push=false \
    --export-cache type=inline \
    --import-cache type=inline

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
