# ============================================================
# 远程 BuildKit (192.168.71.250) 配置与使用指南
# ============================================================

## 一、服务端配置（192.168.71.250）

### 1. 安装并启动 buildkitd
```bash
# 下载 buildkit
wget https://github.com/moby/buildkit/releases/download/v0.13.2/buildkit-v0.13.2.linux-amd64.tar.gz
tar -xzf buildkit-v0.13.2.linux-amd64.tar.gz -C /usr/local

# 创建 systemd 服务文件
sudo tee /etc/systemd/system/buildkit.service > /dev/null <<'EOF'
[Unit]
Description=BuildKit
Documentation=https://github.com/moby/buildkit

[Service]
ExecStart=/usr/local/bin/buildkitd --addr tcp://0.0.0.0:1234 --tlscert /etc/buildkit/buildkit.crt --tlskey /etc/buildkit/buildkit.key
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

# 启动服务
sudo systemctl daemon-reload
sudo systemctl enable --now buildkit

# 检查状态
sudo systemctl status buildkit
```

### 2. 无 TLS 快速启动（内网环境）
```bash
# 直接前台运行（测试用）
sudo buildkitd --addr tcp://0.0.0.0:1234

# 或后台运行
nohup sudo buildkitd --addr tcp://0.0.0.0:1234 > /var/log/buildkit.log 2>&1 &
```

### 3. 防火墙放行
```bash
sudo ufw allow 1234/tcp
# 或
sudo iptables -A INPUT -p tcp --dport 1234 -j ACCEPT
```

---

## 二、客户端配置（构建机）

### 方案 A: 使用 buildctl（推荐，功能最全）

```bash
# 设置环境变量（临时）
export BUILDKIT_HOST=tcp://192.168.71.250:1234

# 验证连接
buildctl debug workers

# 构建后端
cd mr-board
buildctl build \
    --frontend dockerfile.v0 \
    --local context=. \
    --local dockerfile=. \
    --opt filename=Dockerfile.backend \
    --output type=image,name=mr-board-backend:latest,push=false

# 构建前端
cd ../mr-board-frontend
buildctl build \
    --frontend dockerfile.v0 \
    --local context=. \
    --local dockerfile=. \
    --opt filename=Dockerfile \
    --output type=image,name=mr-board-frontend:latest,push=false
```

### 方案 B: 使用 nerdctl（兼容 docker CLI）

```bash
# 方式 1: 命令行参数
nerdctl build \
    --buildkit-host tcp://192.168.71.250:1234 \
    -t mr-board-backend:latest \
    -f Dockerfile.backend \
    .

# 方式 2: 环境变量
export BUILDKIT_HOST=tcp://192.168.71.250:1234
nerdctl build -t mr-board-backend:latest -f Dockerfile.backend .
```

### 方案 C: 使用 docker buildx

```bash
# 创建并切换到远程 builder
docker buildx create \
    --name remote-builder \
    --driver remote \
    tcp://192.168.71.250:1234 \
    --use

# 构建
docker buildx build \
    --builder remote-builder \
    -t mr-board-backend:latest \
    -f mr-board/Dockerfile.backend \
    mr-board/
```

---

## 三、远程缓存配置（重要！）

远程 BuildKit 的缓存策略比本地构建更重要，因为每次构建上下文都要上传到服务端。

### 1. Registry 缓存（推荐）

```bash
# 构建时导入/导出缓存到 registry
buildctl --addr tcp://192.168.71.250:1234 build \
    --frontend dockerfile.v0 \
    --local context=. \
    --local dockerfile=. \
    --opt filename=Dockerfile.backend \
    --output type=image,name=mr-board-backend:latest,push=true \
    --export-cache type=registry,ref=192.168.71.250:5000/mr-board-backend:cache \
    --import-cache type=registry,ref=192.168.71.250:5000/mr-board-backend:cache
```

### 2. 本地目录缓存

```bash
# 服务端本地缓存目录（在 192.168.71.250 上）
buildctl --addr tcp://192.168.71.250:1234 build \
    --export-cache type=local,dest=/var/cache/buildkit \
    --import-cache type=local,src=/var/cache/buildkit \
    ...
```

### 3. 内联缓存（最简单，嵌入镜像中）

```bash
# 已在 Dockerfile 和脚本中默认启用
# --opt build-arg:BUILDKIT_INLINE_CACHE=1
# --export-cache type=inline
# --import-cache type=inline
```

---

## 四、docker-compose 使用远程 BuildKit

### 配置 compose.yml
```yaml
services:
  backend:
    build:
      context: ./mr-board
      dockerfile: Dockerfile.backend
    image: mr-board-backend:latest

  frontend:
    build:
      context: ./mr-board-frontend
      dockerfile: Dockerfile
    image: mr-board-frontend:latest
```

### 构建命令
```bash
# 设置环境变量后使用 nerdctl compose
export BUILDKIT_HOST=tcp://192.168.71.250:1234
nerdctl compose -f docker-compose.yml build
nerdctl compose -f docker-compose.yml up -d
```

---

## 五、一键构建脚本

已提供两个脚本，根据你的环境选择：

| 脚本 | 工具 | 适用场景 |
|------|------|----------|
| `build-remote-buildkit.sh` | buildctl | 功能最全，推荐 |
| `build-remote-nerdctl.sh` | nerdctl | 兼容 docker CLI 习惯 |

```bash
chmod +x build-remote-*.sh

# 使用 buildctl
./build-remote-buildkit.sh v1.0.0

# 使用 nerdctl
./build-remote-nerdctl.sh v1.0.0
```

---

## 六、常见问题

### Q1: 连接被拒绝 `connection refused`
```bash
# 服务端检查
sudo netstat -tlnp | grep 1234
sudo systemctl status buildkit

# 客户端测试
nc -zv 192.168.71.250 1234
```

### Q2: 构建上下文上传太慢
- 确保 `.dockerignore` 已排除 `node_modules/`、`.git/`、`target/` 等
- 考虑使用本地缓存减少重复上传

### Q3: 镜像构建成功但本地看不到
远程 BuildKit 构建的镜像默认输出到远程的 containerd 命名空间。
如需在本地使用，需配置输出：
```bash
# 输出到本地 docker/nerdctl 镜像仓库
--output type=image,name=mr-board-backend:latest,push=false

# 或导出为 tar
--output type=docker,name=mr-board-backend:latest > image.tar
```

### Q4: 缓存未命中
1. 确认 `--export-cache` 和 `--import-cache` 参数正确
2. 检查 Dockerfile 的层顺序是否稳定
3. 使用 `buildctl build --no-cache` 强制禁用缓存测试
