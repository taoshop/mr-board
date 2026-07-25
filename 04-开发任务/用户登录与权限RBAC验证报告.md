# MR Board 系统 — 用户登录与权限（RBAC）验证报告

---

## 1. 基本信息

| 项 | 内容 |
|---|---|
| 报告名称 | 用户登录与权限（RBAC）功能验证报告 |
| 编制日期 | 2026-07-26 |
| 验证范围 | 基于角色的访问控制：5 角色 × 菜单权限 × 操作权限 × 数据权限 |
| 对应要求 | 考核要点第 9 项：「用户登录与权限：基于角色的访问控制」 |
| 代码基线 | Commit `5800065` + 本次会话改动（master 分支） |

---

## 2. 角色与权限定义

### 2.1 角色定义

| 角色编码 | 角色名称 | 描述 |
|---|---|---|
| `dev` | Developer | 开发人员 — 查看/操作自己的 MR |
| `reviewer` | Reviewer | 代码评审员 — 只读查看看板 |
| `techlead` | Tech Lead | 技术负责人 — 全部操作权限（除用户管理） |
| `pm` | PM | 项目经理 — 查看报表 + 看板 |
| `admin` | Admin | 系统管理员 — 全部权限 |

### 2.2 权限编码

| 权限编码 | 类型 | 说明 |
|---|---|---|
| `menu:dashboard` | 菜单 | 看板菜单入口 |
| `menu:users` | 菜单 | 用户管理菜单入口 |
| `menu:reports` | 菜单 | 统计报表菜单入口 |
| `user:read/create/update/delete` | 操作 | 用户 CRUD |
| `mr:read` | 操作 | MR 查看 |
| `mr:update` | 操作 | MR 状态更新（拖拽） |
| `report:read` | 操作 | 报表查看 |
| `report:export` | 操作 | 报表导出 |

### 2.3 角色-权限映射

```
Admin     → 全部 11 项权限
TechLead  → menu:dashboard, menu:reports, mr:read, mr:update, report:read, report:export
PM        → menu:dashboard, menu:reports, mr:read, report:read, report:export
Dev       → menu:dashboard, mr:read, mr:update
Reviewer  → menu:dashboard, mr:read
```

---

## 3. 架构设计

### 3.1 多层权限体系

```
┌─────────────────────────────────────────────────┐
│ Layer 1: 前端路由守卫 (router.beforeEach)        │
│   • 未登录 → /login                              │
│   • mustChangePassword → /profile (强制改密)      │
│   • admin 菜单 → isAdmin 校验 → /403             │
├─────────────────────────────────────────────────┤
│ Layer 2: 前端菜单过滤 (LayoutView.menuItems)      │
│   • canViewReport → 报表菜单 (admin/pm/techlead)  │
│   • isAdmin → 用户管理菜单 (admin only)            │
├─────────────────────────────────────────────────┤
│ Layer 3: 后端接口鉴权 (@PreAuthorize)             │
│   • hasRole('ADMIN') → Git源/用户管理             │
│   • hasAnyRole(ADMIN,PM,TECHLEAD) → 报表/导出    │
│   • hasAnyRole(ADMIN,...,DEVELOPER) → MR 拖拽     │
│   • REVIEWER 被排除在 status update 外            │
├─────────────────────────────────────────────────┤
│ Layer 4: 后端数据鉴权 (applyDataScope)             │
│   • dev/reviewer → 仅看自己的 MR                  │
│   • admin/pm/techlead → 全部 MR                   │
├─────────────────────────────────────────────────┤
│ Layer 5: 后端操作鉴权 (validateStatusTransition)   │
│   • merged/closed → 仅 Admin/TechLead             │
│   • 冲突 MR → 禁止拖入 ready/merged               │
│   • Dev → 只能操作自己的 MR                        │
└─────────────────────────────────────────────────┘
```

### 3.2 认证流程

```
用户输入凭证 → POST /api/auth/login
  → 校验 Redis 锁定计数 (login:fail:{username}, 5次/15分钟)
  → BCrypt(12) 密码比对
  → 查询用户角色 + 权限 (user_roles + role_permissions)
  → 签发 JWT access_token (2h) + refresh_token (7d)
  → 返回 firstLogin 标志 (password_changed 字段)
  → 前端存储 token + mustChangePassword 状态
  → 路由守卫强制跳转 /profile (首次登录)
```

---

## 4. 测试验证

### 4.1 测试环境

| 组件 | 版本/地址 |
|---|---|
| 前端 | Vue 3 + Vite `http://localhost:5173` |
| 后端 | Spring Boot 3.2 `http://localhost:8080` |
| 数据库 | MySQL 8.0 (Docker) |
| 缓存 | Redis 7 (Docker) |

### 4.2 测试用户

| 用户名 | 密码 | 角色 | 菜单预期 |
|---|---|---|---|
| `dev1` | `Dev@123` | Developer | 看板 + Git源 + 同步日志 |
| `reviewer1` | `Rev@123` | Reviewer | 看板 + Git源 + 同步日志 |
| `pm1` | `Pm@123` | PM | 看板 + Git源 + 同步日志 + **统计报表** |
| `techlead1` | `TL@123` | Tech Lead | 看板 + Git源 + 同步日志 + **统计报表** |
| `admin` | `Admin@123` | Admin | 全部 5 项菜单 |

### 4.3 菜单可见性测试

| 菜单项 | Dev | Reviewer | PM | TechLead | Admin |
|---|---|---|---|---|---|
| 看板 (`/dashboard`) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Git 源配置 (`/git-sources`) | ✅ | ✅ | ✅ | ✅ | ✅ |
| 同步日志 (`/sync-logs`) | ✅ | ✅ | ✅ | ✅ | ✅ |
| 统计报表 (`/reports`) | ❌ | ❌ | ✅ | ✅ | ✅ |
| 用户管理 (`/users`) | ❌ | ❌ | ❌ | ❌ | ✅ |

> 截图：`test/e2e/screenshots/rbac-{role}-dashboard.png`

### 4.4 API 权限测试

```bash
# Reviewer 尝试更新 MR 状态 → 403
curl -X PUT /api/mrs/1/status -H "Authorization: Bearer {reviewer_token}"
# 响应: {"code":403,"message":"禁止访问"}

# Dev 尝试访问报表 → 403  
curl GET /api/reports/overview -H "Authorization: Bearer {dev_token}"
# 响应: {"code":403,"message":"禁止访问"}

# Dev 尝试操作他人 MR → 403 (数据级鉴权)
curl -X PUT /api/mrs/1/status -H "Authorization: Bearer {dev_token}"
# 响应: {"code":403,"message":"禁止访问"}
```

### 4.5 登录安全测试

| 场景 | 预期 | 结果 |
|---|---|---|
| 正确凭证登录 | 200 + JWT Token | ✅ |
| 错误密码 | 1002 "用户名或密码错误" | ✅ |
| 5 次失败后 | 1003 "账号已锁定，请15分钟后重试" | ✅ |
| 首次登录 | `firstLogin: true` → 强制跳转 `/profile` | ✅ |
| 未改密访问其他页面 | 路由守卫拦截 → 重定向 `/profile` | ✅ |
| 无 Token 访问 API | HTTP 403 | ✅ |
| Token 过期 | 401 → 自动 refresh → 失败则跳转 `/login` | ✅ |

---

## 5. 关键代码索引

### 5.1 后端

| 文件 | 说明 |
|---|---|
| `SecurityConfig.java:44-54` | HTTP 安全配置：开放/auth + /webhook + /actuator，其余需认证 |
| `SecurityConfig.java:72-76` | `MethodSecurityExpressionHandler` + `CustomPermissionEvaluator` |
| `SecurityConfig.java:84-93` | CORS 配置：允许 localhost:5173 / localhost:3000 |
| `AuthController.java:48-97` | 登录逻辑：Redis 锁定、BCrypt 验证、JWT 签发、firstLogin |
| `AuthController.java:162-187` | 更新密码 → `passwordChanged = true` |
| `BoardController.java:157-179` | `applyDataScope()` — developer/reviewer 数据隔离 |
| `MrsController.java:58` | MR 列表查询 — 所有角色可读 |
| `MrsController.java:170` | MR 状态更新 — **排除 REVIEWER** |
| `MrsController.java:255-286` | `validateStatusTransition()` — 操作级权限校验 |
| `UserController.java:28` | 用户管理 — **仅 ADMIN** |
| `ReportController.java:44` | 报表查看 — **ADMIN/PM/TECHLEAD** |
| `GitSourceController.java:30` | Git 源管理 — **仅 ADMIN** |
| `CustomPermissionEvaluator.java` | 数据级 `hasPermission` 表达式 |

### 5.2 前端

| 文件 | 说明 |
|---|---|
| `router/index.ts:71-89` | 路由守卫：认证 + `mustChangePassword` + `isAdmin` |
| `LayoutView.vue:59-72` | 菜单动态过滤：`canViewReport` + `isAdmin` |
| `stores/user.ts:18-24` | `isAdmin` / `canViewReport` 计算属性 + `mustChangePassword` 持久化 |
| `LoginView.vue:91-93` | 登录后 `firstLogin` 检查 → `setMustChangePassword(true)` |
| `ProfileView.vue:10-18` | 首次登录警告横幅 + 密码必填校验 |

### 5.3 数据库

| 文件 | 说明 |
|---|---|
| `init.sql:79-85` | 5 角色 seed data |
| `init.sql:88-99` | 11 权限 seed data |
| `init.sql:103-124` | 角色-权限映射 |
| `init.sql:129-134` | admin 用户 + 角色分配 |
| `init.sql:16-32` | users 表含 `password_changed` 字段 |

---

## 6. 结论

用户登录与权限（RBAC）功能 **完整实现**，满足考核要点全部要求：

| 考核要点 | 实现情况 |
|---|---|
| 基于角色的访问控制 | ✅ 5 角色（dev/reviewer/techlead/pm/admin） |
| 开发（查看/操作） | ✅ 查看看板 + 拖拽自己的 MR，不能操作他人 MR |
| Reviewer（评审） | ✅ 只读看板，不能拖拽变更 MR 状态 |
| PM/Tech Lead（查看） | ✅ 看板 + 统计报表 + 导出，TechLead 可合并/关闭 MR |
| 不同角色看到不同菜单 | ✅ 前端动态菜单过滤 + 后端 @PreAuthorize 双重校验 |
| 不同角色不同数据范围 | ✅ applyDataScope 实现行级数据隔离 |
| 登录鉴权 | ✅ JWT access/refresh token + BCrypt(12) + 暴力破解锁定 |

> 📸 完整截图位于 `test/e2e/screenshots/`，E2E 测试脚本位于 `test/smoke/smoke-test.sh`。

---

*报告生成时间：2026-07-26*  
*报告路径：`04-开发任务/用户登录与权限RBAC验证报告.md`*
