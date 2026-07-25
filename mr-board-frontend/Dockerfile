# ============================================================
# 前端 Dockerfile - 多阶段构建 + 依赖缓存优化
# 适用于 docker / nerdctl + BuildKit
# ============================================================

# 阶段 1: 依赖下载（独立缓存层）
FROM node:20-alpine AS deps
WORKDIR /app

# 只复制依赖描述文件（变更频率低）
COPY package*.json ./

# 使用 npm ci 替代 npm install：
# - 严格遵循 package-lock.json，构建更可靠
# - 速度更快，适合 CI/CD
# - 不会更新 package-lock.json
RUN npm ci --prefer-offline --no-audit

# ============================================================
# 阶段 2: 编译构建（源码变更时只重新编译，不重新安装依赖）
# ============================================================
FROM node:20-alpine AS builder
WORKDIR /app

# 从 deps 阶段复制已安装好的 node_modules
COPY --from=deps /app/node_modules ./node_modules

# 复制源码和配置文件（变更频率高）
COPY . .

# 构建生产包
RUN npm run build

# ============================================================
# 阶段 3: 运行镜像（最小化体积）
# ============================================================
FROM nginx:1.24-alpine

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
