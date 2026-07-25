# ============================================================
# nerdctl 依赖缓存构建指南
# MR Board System
# ============================================================

## 一、核心优化原理

### 1. 分层缓存（Layer Caching）
Docker/nerdctl 的镜像由多层组成，每层对应 Dockerfile 中的一条指令。
当某层之前的所有层都没有变化时，该层可以直接复用缓存。

**优化策略：**
- 将「低频变更的文件」放在前面复制（pom.xml / package.json）
- 将「高频变更的文件」放在后面复制（源代码）
- 这样源码修改时，依赖层（前面几层）不会被重建

### 2. 多阶段构建 + 阶段间复制
```
deps 阶段: 只复制 pom.xml → 下载依赖 → 形成稳定缓存层
       ↓
builder 阶段: 复制 deps 阶段的 .m2 缓存 → 复制源码 → 编译
       ↓
运行阶段: 只复制编译产物，镜像体积最小
```

## 二、nerdctl 构建命令详解

### 基础构建
```bash
nerdctl build -t mr-board-backend:latest -f mr-board/Dockerfile.backend mr-board/
```

### 带缓存优化的构建（推荐）
```bash
# 启用内联缓存标记，便于后续构建识别可复用层
nerdctl build \
  -t mr-board-backend:latest \
  --build-arg BUILDKIT_INLINE_CACHE=1 \
  --progress=plain \
  -f mr-board/Dockerfile.backend \
  mr-board/
```

### 使用外部缓存（registry 缓存）
```bash
# 构建时从远程 registry 导入缓存
nerdctl build \
  -t mr-board-backend:latest \
  --cache-from=type=registry,ref=your-registry/mr-board-backend:cache \
  --cache-to=type=registry,ref=your-registry/mr-board-backend:cache,mode=max \
  -f mr-board/Dockerfile.backend \
  mr-board/
```

### 使用本地目录缓存
```bash
# 导出缓存到本地目录
nerdctl build \
  -t mr-board-backend:latest \
  --cache-to=type=local,dest=/tmp/build-cache \
  -f mr-board/Dockerfile.backend \
  mr-board/

# 下次构建时导入本地缓存
nerdctl build \
  -t mr-board-backend:latest \
  --cache-from=type=local,src=/tmp/build-cache \
  -f mr-board/Dockerfile.backend \
  mr-board/
```

## 三、缓存效果验证

### 第一次构建（无缓存）
```bash
$ time nerdctl build -t mr-board-backend -f Dockerfile.backend .
[+] Building 120.5s (15/15) FINISHED
 => [deps 4/4] RUN mvn dependency:go-offline ...    45.2s  ← 下载依赖
 => [builder 2/2] RUN mvn clean package ...          60.3s  ← 编译
```

### 第二次构建（修改源码后，依赖缓存命中）
```bash
$ # 修改了 Java 源代码...
$ time nerdctl build -t mr-board-backend -f Dockerfile.backend .
[+] Building 35.2s (15/15) FINISHED
 => [deps 4/4] RUN mvn dependency:go-offline ...     0.5s  ← CACHED！依赖未重新下载
 => [builder 2/2] RUN mvn clean package ...          25.1s  ← 只重新编译
```

### 第三次构建（源码未修改，全缓存命中）
```bash
$ time nerdctl build -t mr-board-backend -f Dockerfile.backend .
[+] Building 2.1s (15/15) FINISHED
 => [deps 4/4] RUN mvn dependency:go-offline ...     0.2s  ← CACHED
 => [builder 2/2] RUN mvn clean package ...          0.3s  ← CACHED
```

## 四、常见问题排查

### Q1: 为什么每次构建都重新下载依赖？
**原因**: `COPY . .` 把源码和 pom.xml 一起复制，源码变更导致整个层失效。
**解决**: 按本方案先单独复制 pom.xml，再复制源码。

### Q2: nerdctl 提示 buildkit 未启用？
```bash
# 检查 buildkit 是否运行
nerdctl buildkit inspect

# 启动 buildkit（如未运行）
sudo systemctl start buildkit

# 或在 nerdctl 中指定 buildkit 地址
nerdctl build --buildkit-host unix:///run/buildkit/buildkitd.sock ...
```

### Q3: 如何清理本地缓存？
```bash
# 清理 nerdctl 构建缓存
nerdctl builder prune

# 清理所有未使用的镜像（谨慎！）
nerdctl image prune -a
```

### Q4: 多模块 Maven 项目如何优化？
已在本方案中处理：
1. 先复制根 pom.xml 和各子模块 pom.xml
2. 运行 `mvn dependency:go-offline` 下载所有模块依赖
3. 再复制各模块 src 目录

## 五、快速开始

### 使用提供的脚本一键构建
```bash
chmod +x build-nerdctl.sh

# 构建 latest 标签
./build-nerdctl.sh

# 构建指定标签
./build-nerdctl.sh v1.0.0

# 构建并推送到私有仓库
./build-nerdctl.sh v1.0.0 registry.example.com
```

### 手动分步构建
```bash
# 后端
cd mr-board
nerdctl build -f Dockerfile.backend -t mr-board-backend:latest .

# 前端
cd ../mr-board-frontend
nerdctl build -t mr-board-frontend:latest .
```

### 使用 docker-compose（nerdctl compose 兼容）
```bash
# nerdctl 支持 compose 命令
nerdctl compose -f docker-compose.yml build
nerdctl compose -f docker-compose.yml up -d
```
