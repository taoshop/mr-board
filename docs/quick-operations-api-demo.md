# MR 快速操作 API 演示

本文档演示 PR #8 新增的 4 个快速操作 API 的完整调用流程，包含 cURL 示例、请求/响应格式及调用时序。

---

## 1. 重跑 CI

**用途**：触发 Git 平台重新执行该 MR 的 CI Pipeline  
**权限**：ADMIN / PM / TECHLEAD / DEVELOPER  
**接口**：`POST /api/mrs/{id}/rerun-ci`

### 请求示例

```bash
curl -X POST "http://localhost:8080/api/mrs/42/rerun-ci" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json"
```

### 成功响应

```json
{
  "code": 200,
  "message": "success"
}
```

### 失败响应

```json
{
  "code": 500,
  "message": "Git平台操作失败: Git平台重跑CI失败: 该MR暂无CI Pipeline"
}
```

### 流程说明

1. 前端调用 `/api/mrs/42/rerun-ci`
2. `MrsController.rerunCi()` 解析 MR 主键 ID
3. `callGitApi()` 获取项目的 Git 源配置并解密 Token
4. 平台客户端执行：
   - **GitHub**：获取 PR head sha → 查询 check suites → 调用 `/check-suites/{id}/rerequest`
   - **GitLab**：查询 MR 最新 pipeline → 调用 `/pipelines/{id}/retry`
5. 成功后清除看板缓存（`@CacheEvict("board")`）

---

## 2. 指派 Reviewer

**用途**：在 Git 平台上为 MR 指派评审人  
**权限**：ADMIN / PM / TECHLEAD / DEVELOPER  
**接口**：`POST /api/mrs/{id}/assign-reviewer`

### 请求示例

```bash
curl -X POST "http://localhost:8080/api/mrs/42/assign-reviewer" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "reviewers": ["zhangsan", "lisi"]
  }'
```

### 成功响应

```json
{
  "code": 200,
  "message": "success"
}
```

### 参数校验

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `id` | Path | Long | 是 | MR 主键 ID |
| `reviewers` | Body | String[] | 是 | 评审人用户名列表，不能为空 |

### 流程说明

1. 前端调用 `/api/mrs/42/assign-reviewer`，携带 reviewer 列表
2. 后端校验 `reviewers` 不为空
3. `callGitApi()` → 平台客户端执行：
   - **GitHub**：`POST /repos/{repo}/pulls/{number}/requested_reviewers`
   - **GitLab**：`PUT /projects/{id}/merge_requests/{iid}` 更新 `reviewer_ids`

---

## 3. 提醒 Reviewer

**用途**：在 MR 下发表评论 @ 所有 Reviewer 提醒评审  
**权限**：ADMIN / PM / TECHLEAD / DEVELOPER  
**接口**：`POST /api/mrs/{id}/remind-reviewers`

### 请求示例

```bash
curl -X POST "http://localhost:8080/api/mrs/42/remind-reviewers" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json"
```

### 成功响应

```json
{
  "code": 200,
  "message": "success"
}
```

### 失败响应（无 Reviewer）

```json
{
  "code": 400,
  "message": "该MR尚未指派Reviewer"
}
```

### 流程说明

1. 前端调用 `/api/mrs/42/remind-reviewers`
2. 后端从数据库 `mrs.reviewers` 字段解析 reviewer 列表
3. 若 reviewer 列表为空，返回 400
4. `callGitApi()` → 平台客户端在 MR 下创建评论：
   - **GitHub**：`POST /repos/{repo}/issues/{number}/comments`，内容如 `@zhangsan @lisi 请尽快评审此PR，谢谢！`
   - **GitLab**：`POST /projects/{id}/merge_requests/{iid}/notes`，内容同上

---

## 4. 重新打开 MR

**用途**：将已关闭的 MR 在 Git 平台上重新打开  
**权限**：ADMIN / PM / TECHLEAD  
**接口**：`POST /api/mrs/{id}/reopen`

### 请求示例

```bash
curl -X POST "http://localhost:8080/api/mrs/42/reopen" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json"
```

### 成功响应

```json
{
  "code": 200,
  "message": "success"
}
```

### 失败响应（非 closed 状态）

```json
{
  "code": 409,
  "message": "仅已关闭的MR可重新打开"
}
```

### 流程说明

1. 前端调用 `/api/mrs/42/reopen`
2. 后端校验 `boardStatus == "closed"`，否则返回 409
3. `callGitApi()` → 平台客户端执行：
   - **GitHub**：`PATCH /repos/{repo}/pulls/{number}`，设置 `state=open`
   - **GitLab**：`PUT /projects/{id}/merge_requests/{iid}`，设置 `state_event=reopen`
4. 本地状态更新：
   - `boardStatus` → `pending_review`
   - `platformStatus` → `opened`（根据平台类型）
   - `closedAt` → null
5. 记录状态变更历史
6. 清除看板缓存（`@CacheEvict("board")`）

---

## 5. 完整调用流程演示

以下是一个 MR 从"已关闭"→"重新打开"→"指派 Reviewer"→"提醒 Reviewer"→"重跑 CI"的完整时序：

```
状态: closed
    │
    ├── POST /api/mrs/42/reopen
    │   → Git平台 reopen
    │   → boardStatus = pending_review
    │   → closedAt = null
    │   → 记录历史
    │   ────────────── Git 同步后 → boardStatus = pending_review
    │
    ├── POST /api/mrs/42/assign-reviewer
    │   Body: {"reviewers": ["zhangsan", "lisi"]}
    │   → Git平台指派 reviewer
    │   ────────────── Git 同步后 → reviewers = "zhangsan,lisi"
    │                                    boardStatus = reviewing
    │
    ├── POST /api/mrs/42/remind-reviewers
    │   → Git平台发表 @评论
    │   → reviewers 收到通知
    │
    └── POST /api/mrs/42/rerun-ci
        → Git平台重跑 CI Pipeline
        ────────────── CI 完成 → ciStatus = success
                                    boardStatus = ready（可合并）
```

---

## 6. 演示测试

执行以下测试可验证快速操作 API 的接口定义与权限校验：

```bash
cd mr-board/mr-board-system
mvn test -Dtest=MrsControllerStatusTransitionTest
```

该测试覆盖了状态变更、权限拦截等场景。

---

## 7. 相关代码位置

| 文件 | 说明 |
|---|---|
| `MrsController.java` | 4 个快速操作端点定义 |
| `GitHubClient.java` | GitHub 平台实现（rerunCI / assignReviewer / remindReviewers / reopenMR） |
| `GitLabClient.java` | GitLab 平台实现（同上） |
| `GitSyncClient.java` | 接口定义 |
| `MrsControllerStatusTransitionTest.java` | 状态变更测试 |
