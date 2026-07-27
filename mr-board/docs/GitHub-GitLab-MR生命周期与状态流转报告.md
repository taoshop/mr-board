# GitHub / GitLab MR 状态流转规则与生命周期报告

**编制日期：** 2026-07-26  
**适用范围：** mr-board 看板系统对接层设计参考  
**涉及平台：** GitHub (Pull Request) / GitLab (Merge Request)

---

## 1. 概述

GitHub 与 GitLab 的代码评审流程在概念上高度相似，但在**状态模型、Review 机制、CI 触发策略**上存在关键差异。理解这些差异是 mr-board 看板正确映射平台状态的必要前提。

| 维度 | GitHub Pull Request | GitLab Merge Request |
|------|---------------------|----------------------|
| 核心对象 | Pull Request (PR) | Merge Request (MR) |
| 生命周期状态 | `open` / `closed` / `merged` | `opened` / `closed` / `locked` / `merged` |
| Draft 机制 | `draft` 字段 + title 前缀 | `work_in_progress` 字段 + title 前缀 |
| Review 模型 | PR Review (含多轮 review) | Approval Rule + MR-level Approval |
| CI 触发粒度 | Check Run (per commit) | Pipeline → Job (per MR / per branch) |
| 合并冲突字段 | 无原生字段，需调 Compare API | `has_conflicts` / `merge_status` |

---

## 2. GitHub Pull Request 生命周期

### 2.1 原生状态模型

GitHub PR 的**数据库级状态**只有三种：

| API 返回值 (`state`) | 含义 | 如何区分 merged |
|----------------------|------|-----------------|
| `open` | PR 打开中，可继续推送、Review、CI | — |
| `closed` | PR 已关闭 | 需结合 `merged_at` 字段判断 |
| `merged` | **逻辑状态**，API 返回 `state=closed` + `merged_at!=null` | `merged_at` 有值 |

> **关键细节：** GitHub REST API 的 `state` 字段永远不会返回 `"merged"`。mr-board 中 `mapGitHubState()` 通过 `merged_at != null` 来区分 "merged closed" 和 "abandoned closed"。

### 2.2 完整生命周期流转图

```
创建 PR
   │
   ├─── draft=true ──────────────→ Draft PR（不可合并，不通知 reviewer）
   │                                 │
   │                                 ▼ 标记 Ready for review
   │                               open（正常流程）
   │
   └─── draft=false ─────────────→ open
                                     │
        ┌────────────────────────────┼────────────────────────────┐
        │                            │                            │
        ▼                            ▼                            ▼
   推送新 commit                  Reviewer 操作                作者操作
        │                            │                            │
        ▼                            ▼                            ▼
   CI 自动触发                 APPROVED /                      关闭 PR
   (Check Runs)                CHANGES_REQUESTED                   │
        │                            │                            │
        ▼                            ▼                            ▼
   ┌─────────┐               ┌─────────────┐                closed (abandoned)
   │ success │──────────────→│ mergeable   │─── 点击 Merge ──→ closed + merged_at
   │ failed  │──── 阻塞合并   │ = true      │                      (逻辑 merged)
   │ pending │               └─────────────┘
   └─────────┘
```

### 2.3 Draft PR 机制

| 特性 | 说明 |
|------|------|
| 创建方式 | GitHub UI "Create draft PR" 或 API `draft=true` |
| title 前缀 | 部分用户手动加 `Draft:` / `WIP:` |
| 合并按钮 | 禁用，显示 "This pull request is still a work in progress" |
| CI 触发 | 默认仍会触发（取决于仓库设置），但 reviewer 不会收到通知 |
| 转正式 | 点击 "Ready for review" 后 `draft=false` |

### 2.4 Review 机制详解

GitHub 的 Review 是**PR-level 的多轮评审记录**，通过 `GET /repos/{owner}/{repo}/pulls/{number}/reviews` 获取。

#### Review State 类型

| state | 含义 | mr-board 映射 |
|-------|------|---------------|
| `APPROVED` | 评审人批准 PR | `approved` |
| `CHANGES_REQUESTED` | 评审人要求修改 | `reviewing` |
| `COMMENTED` | 仅发表评论，无明确态度 | **忽略**（视为未形成实质评审） |
| `DISMISSED` | 之前的 review 被撤销 | 不参与计算 |
| `PENDING` | review 草稿未提交 | API 不返回 |

#### 多轮 Review 的处理逻辑

```
reviews = fetchReviews(prNumber)

hasApproved          = 存在任意 APPROVED
hasChangesRequested  = 存在任意 CHANGES_REQUESTED
hasMeaningfulReview  = 存在非 COMMENTED 的 review

if hasApproved && !hasChangesRequested:
    → "approved"
elif hasChangesRequested:
    → "reviewing"        (changes requested 优先级高于 approved)
elif hasMeaningfulReview:
    → "reviewing"        (有 reviewer 参与但未形成明确结论)
else:
    → "pending_review"   (只有 COMMENTED 或无 review)
```

> **注意：** GitHub 允许多人 review，一人 APPROVED + 另一人 CHANGES_REQUESTED 时，整体状态应为 `reviewing`（以阻塞合并的评审为准）。

### 2.5 CI 触发机制 (Check Runs)

GitHub CI 通过 **Check Runs / Check Suites** 模型与 PR 关联：

| 层级 | 说明 | API |
|------|------|-----|
| Check Suite | 一组 Check Runs 的集合（通常对应一个 CI 服务如 GitHub Actions） | `/repos/{repo}/commits/{sha}/check-suites` |
| Check Run | 单个 CI 任务（如 "build", "test", "lint"） | `/repos/{repo}/commits/{sha}/check-runs` |

#### Check Run 状态映射

GitHub Check Run 有两个关键字段：`status` + `conclusion`。

| status | conclusion | mr-board 映射 | 业务含义 |
|--------|------------|---------------|---------|
| `queued` | — | `pending` | 等待调度 |
| `in_progress` | — | `running` | 正在执行 |
| `completed` | `success` | `success` | 通过 |
| `completed` | `failure` | `failed` | 失败 |
| `completed` | `cancelled` | `canceled` | 取消 |
| `completed` | `skipped` | `canceled` | 跳过 |
| `completed` | `timed_out` | `failed` | 超时 |
| `completed` | `action_required` | `failed` | 需要人工干预 |

#### CI 触发时机

1. **push 到 PR 分支** → 自动触发（默认）
2. **PR 创建/更新** → 自动触发
3. **手动触发** → `workflow_dispatch` 或 CI 界面重跑
4. **Draft PR** → 通常触发，但仓库可配置 "Skip draft PRs"

### 2.6 Mergeable 判定

GitHub 不提供直接的 `mergeable` 布尔字段（早期 API 有但已废弃）。mr-board 当前通过 `state != "closed"` 简单推断，实际应关注：

- `mergeable`（boolean，v3 API 已废弃，建议用 GraphQL `mergeable`）
- `mergeable_state`：`clean` / `blocked` / `dirty` / `unstable` / `behind`

| mergeable_state | 含义 |
|-----------------|------|
| `clean` | 无冲突，所有检查通过，可合并 |
| `blocked` | 分支保护规则阻止合并（如需要 review、CI 未通过） |
| `dirty` | 有代码冲突 |
| `unstable` | CI 有失败或进行中 |
| `behind` | 目标分支有更新，需要 rebase / merge |

---

## 3. GitLab Merge Request 生命周期

### 3.1 原生状态模型

GitLab MR 的状态更丰富，API 直接返回 `state`：

| API 返回值 (`state`) | 含义 | mr-board 映射 |
|----------------------|------|---------------|
| `opened` / `open` | MR 打开中 | `open` |
| `closed` | MR 已关闭（未合并） | `closed` |
| `merged` | MR 已合并 | `merged` |
| `locked` | 讨论被锁定（较少见） | 通常按 `open` 处理 |

### 3.2 完整生命周期流转图

```
创建 MR
   │
   ├─── work_in_progress=true ───→ WIP MR（不可合并，pipeline 可能仍运行）
   │                                 │
   │                                 ▼ 移除 WIP 前缀或取消标记
   │                               opened（正常流程）
   │
   └─── work_in_progress=false ──→ opened
                                     │
        ┌────────────────────────────┼────────────────────────────┐
        │                            │                            │
        ▼                            ▼                            ▼
   推送新 commit                  Reviewer 操作                作者操作
        │                            │                            │
        ▼                            ▼                            ▼
   Pipeline 触发              Approval Rule                 关闭 MR
   (GitLab CI)                状态变化                          │
        │                            │                            │
        ▼                            ▼                            ▼
   ┌─────────┐               ┌─────────────┐                closed (abandoned)
   │ success │──────────────→│ approved    │─── 点击 Merge ──→ merged
   │ failed  │──── 阻塞合并   │ = true      │
   │ pending │               └─────────────┘
   │ running │
   └─────────┘
```

### 3.3 WIP / Draft MR 机制

| 特性 | GitLab 传统 WIP | GitLab 现代 Draft |
|------|----------------|-------------------|
| title 前缀 | `WIP:` | `Draft:` |
| 字段标识 | `work_in_progress=true` | `draft=true` |
| 合并按钮 | 禁用 | 禁用 |
| 流水线 | 默认仍运行 | 默认仍运行 |
| 转正式 | 编辑标题移除前缀 | 点击 "Mark as ready" |

### 3.4 Review / Approval 机制详解

GitLab 的 Approval 是 **MR-level 的审批规则**，与 GitHub 的 Review 记录有本质区别。

#### 两种 Approval 模式

| 模式 | 说明 | API |
|------|------|-----|
| **简易 Approval**（Premium+） | 设置 "Approvals required = N"，N 人 approve 后才可合并 | `/projects/{id}/merge_requests/{iid}/approvals` |
| **Approval Rule**（Ultimate） | 按规则分组（如 Code Owner、Security），每组需 N 人 | 同上，返回 `rules` 数组 |

#### Approval API 响应解析

```json
{
  "id": 5,
  "iid": 1,
  "project_id": 1,
  "title": "...",
  "approved": false,           ← 是否满足所有 approval rules
  "approvals_required": 2,
  "approvals_left": 1,
  "approved_by": [             ← 已 approve 的用户列表
    { "user": { "username": "alice" } }
  ]
}
```

#### mr-board 映射逻辑

```
approval = fetchApprovalStatus(projectPath, mrIid)

approved    = response.approved == true
approvedBy  = response.approved_by 非空

if approved:
    → "approved"
elif approvedBy 非空:
    → "reviewing"        (有人 review 过但未满足全部规则)
else:
    → "pending"          (无人 review)
```

> **与 GitHub 的关键差异：**
> - GitLab 的 `approved` 是**规则级结论**（是否满足合并条件），不是个人行为；
> - GitHub 的 `APPROVED` 是**个人评审结论**，多人需分别 review；
> - GitLab `approved_by` 非空但 `approved=false` 表示 "有人看过，但还不够"。

### 3.5 CI 触发机制 (Pipeline / Job)

GitLab CI 通过 **Pipeline → Stage → Job** 三级模型运行：

| 层级 | 说明 | API |
|------|------|-----|
| Pipeline | 一次 CI 执行的整体（对应一个 commit 或 MR） | `/projects/{id}/merge_requests/{iid}/pipelines` |
| Job | Pipeline 内的单个任务 | `/projects/{id}/pipelines/{pid}/jobs` |

#### Pipeline/Job 状态映射

| GitLab status | mr-board 映射 | 业务含义 |
|---------------|---------------|---------|
| `created` | `pending` | 刚创建，等待 runner |
| `pending` | `pending` | 等待可用 runner |
| `running` | `running` | 正在执行 |
| `success` | `success` | 通过 |
| `failed` | `failed` | 失败 |
| `canceled` / `cancelled` | `canceled` | 取消 |
| `skipped` | `canceled` | 跳过 |
| `manual` | `pending` | 需手动触发 |

#### CI 触发时机

1. **push 到源分支** → 自动触发（`.gitlab-ci.yml` `only: merge_requests` 或分支规则）
2. **MR 创建/更新** → 自动触发（如果配置了 `merge_request_event`）
3. **手动触发** → CI/CD → Pipelines → Run pipeline
4. **WIP MR** → 默认触发，可配置 `skip_worktree_check` 等规则跳过

### 3.6 合并冲突与 Merge Status

GitLab 在 MR API 中直接提供冲突信息：

| 字段 | 类型 | 含义 |
|------|------|------|
| `has_conflicts` | Boolean | 是否存在代码冲突 |
| `merge_status` | String | 合并检查状态：`can_be_merged` / `cannot_be_merged` / `unchecked` / `checking` |

mr-board 当前映射：
```java
dto.setHasConflict(conflict != null && Boolean.parseBoolean(String.valueOf(conflict)));
dto.setMergeable("can_be_merged".equals(String.valueOf(mergeableObj)));
```

> **注意：** `merge_status` 为 `checking` 时，GitLab 正在后台计算冲突，此时应视为 "暂不可合并" 或等待下次同步。

---

## 4. GitHub vs GitLab 关键差异对照

### 4.1 状态与字段差异

| 能力 | GitHub | GitLab | mr-board 适配策略 |
|------|--------|--------|-------------------|
| 原生 merged 状态 | `state=closed` + `merged_at` | `state=merged` | `GitHubClient.mapGitHubState()` 做逻辑转换 |
| Draft/WIP 检测 | `draft` 字段 + title 前缀 | `work_in_progress` + title 前缀 | 统一按 title 前缀兜底，`draft` 字段做精确判断 |
| 代码冲突字段 | 无直接字段，需 Compare API | `has_conflicts` | GitHub 暂无法获取真实冲突状态（当前写死 `false`） |
| 可合并状态 | `mergeable_state`（v3 废弃） | `merge_status` | GitHub 用 `state!="closed"` 简单推断 |
| Reviewer 列表 | `requested_reviewers` | `reviewers` | 字段名不同，分别解析 |
| Approval 状态 | 遍历 reviews[] 的 state | `approved` + `approved_by` | 分别实现 `fetchApprovalStatus()` |

### 4.2 Review 语义差异

| 场景 | GitHub 行为 | GitLab 行为 | mr-board 统一映射 |
|------|-------------|-------------|-------------------|
| 1 人 APPROVED | `approved` | `approved` (如果满足规则) | `approved` |
| 1 人 CHANGES_REQUESTED | `reviewing` | N/A（GitLab 没有 changes_requested 状态） | `reviewing` |
| 2 人 review，1 APPROVED + 1 CHANGES_REQUESTED | `reviewing`（以阻塞为准） | N/A | `reviewing` |
| 只有 COMMENTED | `pending_review`（不算实质 review） | `reviewing`（如果有人点了 approve 但未满足规则，可能是 `reviewing`） | GitHub 忽略 COMMENTED；GitLab 以 `approved_by` 为准 |
| 无 reviewer | `pending` | `pending` | `pending_review` |

### 4.3 CI 触发差异

| 维度 | GitHub | GitLab |
|------|--------|--------|
| 核心概念 | Check Run | Pipeline Job |
| 与 commit 关系 | 绑定到 PR head commit 的 sha | 绑定到 MR 的 pipeline |
| 状态字段 | `status` + `conclusion` | 单一 `status` |
| 获取 API | `/commits/{sha}/check-runs` | `/merge_requests/{iid}/pipelines` → `/pipelines/{id}/jobs` |
| 重跑机制 | Re-run jobs / Re-run all jobs | Retry job / Retry pipeline |

---

## 5. 状态流转速查表（mr-board 映射层）

### 5.1 GitHub PR → mr-board boardStatus

| PR 状态 | draft | CI status | Review 状态 | hasConflict | mergeable | boardStatus | 说明 |
|---------|-------|-----------|-------------|-------------|-----------|-------------|------|
| open | true | any | any | any | any | `pending_review` | Draft 拦截 |
| open | false | running/pending | any | true | any | `conflict` | 代码冲突优先 |
| open | false | running/pending | any | false | any | `ci_checking` | CI 进行中 |
| open | false | failed | any | false | any | `conflict` | CI 失败阻塞 |
| open | false | success | pending | false | true | `pending_review` | 待 review |
| open | false | success | reviewing | false | true | `reviewing` | review 中 |
| open | false | success | approved | false | true | `ready` | 可合并 |
| open | false | success | approved | false | false | `conflict` | approved 但不可合并 |
| closed | — | — | — | — | — | `closed` | 已关闭 |
| closed | — | — | — | — | — | `merged` | merged_at 有值 |

### 5.2 GitLab MR → mr-board boardStatus

| MR 状态 | WIP | CI status | Approval | hasConflict | mergeable | boardStatus | 说明 |
|---------|-----|-----------|----------|-------------|-----------|-------------|------|
| opened | true | any | any | any | any | `pending_review` | WIP 拦截 |
| opened | false | running/pending | any | true | any | `conflict` | 代码冲突优先 |
| opened | false | running/pending | any | false | any | `ci_checking` | CI 进行中 |
| opened | false | failed | any | false | any | `conflict` | CI 失败阻塞 |
| opened | false | success | pending | false | can_be_merged | `pending_review` | 待 review |
| opened | false | success | reviewing | false | can_be_merged | `reviewing` | review 中 |
| opened | false | success | approved | false | can_be_merged | `ready` | 可合并 |
| opened | false | success | approved | false | cannot_be_merged | `conflict` | approved 但不可合并 |
| closed | — | — | — | — | — | `closed` | 已关闭 |
| merged | — | — | — | — | — | `merged` | 已合并 |

---

## 6. CI 触发与 Review 的时序关系

### 6.1 典型开发流程时序

```
开发者推送代码到 feature 分支
        │
        ▼
   ┌─────────────────────────────────────┐
   │ 1. CI 自动触发（push event）          │
   │    - GitHub: Check Runs 创建          │
   │    - GitLab: Pipeline 创建            │
   └─────────────────────────────────────┘
        │
        ▼
   创建 PR/MR
        │
        ├─── 如果是 Draft/WIP ───→ CI 仍运行，但不进入 Review 流程
        │
        └─── 正式 PR/MR ─────────→ 通知 reviewer
                                      │
                                      ▼
                              Reviewer 收到通知
                                      │
                        ┌─────────────┼─────────────┐
                        ▼             ▼             ▼
                   尚未开始        COMMENTED      APPROVED /
                   (pending)       (仅评论)       CHANGES_REQUESTED
                        │             │             │
                        ▼             ▼             ▼
                   pending_review   reviewing     reviewing / approved
                        │             │             │
                        └─────────────┴─────────────┘
                                      │
                                      ▼
                              CI 全部通过 + Review 通过
                                      │
                                      ▼
                                   ready
                                      │
                                      ▼
                                   点击 Merge
                                      │
                                      ▼
                                   merged
```

### 6.2 边缘场景

| 场景 | 平台行为 | mr-board 表现 |
|------|---------|---------------|
| **Push 后 CI 运行中，Review 已完成** | CI 未通过前不能合并 | `ci_checking`（CI 优先级高于 Review） |
| **CI 失败，Review 已 Approved** | 不能合并，需修复 CI | `conflict`（CI 失败阻塞合并） |
| **有冲突，Review 已 Approved** | 不能合并，需解决冲突 | `conflict`（冲突优先级最高） |
| **Draft PR，CI 全部通过** | 不能合并，需标记 Ready | `pending_review`（Draft 拦截） |
| **Approved 后 push 新 commit** | GitHub: 保留 approved 状态（可配置 dismiss）；GitLab: 可能重置 approval | 同步后重新计算，可能回到 `ci_checking` 或 `reviewing` |
| **目标分支更新后** | mergeable 可能变 false | 可能从 `ready` 变 `conflict` |

---

## 7. mr-board 同步层实现要点

### 7.1 数据拉取策略

| 数据 | GitHub API | GitLab API | 调用时机 |
|------|------------|------------|---------|
| MR/PR 列表 | `GET /repos/{repo}/pulls` | `GET /projects/{id}/merge_requests` | 每次同步 |
| CI 状态 | `GET /commits/{sha}/check-runs` | `GET /projects/{id}/merge_requests/{iid}/pipelines` + `/jobs` | 每次同步 |
| Reviewer | `GET /repos/{repo}/pulls/{n}` (requested_reviewers) | `GET /projects/{id}/merge_requests/{iid}` (reviewers) | 每次同步 |
| Approval | `GET /repos/{repo}/pulls/{n}/reviews` | `GET /projects/{id}/merge_requests/{iid}/approvals` | 每次同步 |
| 代码变更 | `GET /repos/{repo}/pulls/{n}/files` | `GET /projects/{id}/merge_requests/{iid}/changes` | 按需/每次同步 |
| 评论 | `GET /repos/{repo}/issues/{n}/comments` | `GET /projects/{id}/merge_requests/{iid}/notes` | 按需 |

### 7.2 当前实现中的已知限制

| 限制 | 影响 | 建议 |
|------|------|------|
| GitHub `hasConflict` 写死 `false` | 无法真实检测 GitHub PR 的冲突状态 | 调用 `GET /repos/{repo}/compare/{base}...{head}` 或 GraphQL `mergeable` |
| GitHub `mergeable` 简化推断 | `mergeable` 不准确 | 使用 GraphQL API 获取 `mergeable_state` |
| GitLab `merge_status=checking` | 同步时可能拿到中间状态 | 增加重试逻辑或标记为 `ci_checking` 等待下次同步 |
| GitHub `COMMENTED` 过滤 | 纯评论不算 review，但如果 reviewer 只评论了，实际需求可能是 `reviewing` | 可配置化：是否将 COMMENTED 视为 `reviewing` |
| GitHub Check Runs 只取最新 | 如果历史 Check Run 失败但最新重跑通过，状态正确 | 无需改动 |

---

## 8. 结论与建议

1. **GitHub 与 GitLab 的状态模型差异主要集中在冲突检测和 Approval 机制上**。GitLab 提供更原生的冲突字段和规则化 Approval；GitHub 的 Review 更灵活但状态判断更复杂。

2. **mr-board 的 7 列状态设计是平台无关的合理抽象**，能够同时兼容 GitHub 和 GitLab 的数据模型。核心映射逻辑（`BoardStatusCalculator`）已覆盖两种平台的主要场景。

3. **GitHub 冲突检测是当前最大缺口**。建议后续通过 GraphQL API 补充 `mergeable` 和 `mergeable_state` 字段，替代当前的简化推断。

4. **Review 语义差异需要持续注意**：
   - GitHub 的 `CHANGES_REQUESTED` 在 GitLab 中没有直接对应概念；
   - GitLab 的 `approved_by` 非空但 `approved=false` 对应 GitHub "有人 review 过但还没 approved" 的状态。

5. **CI 触发对状态的影响是动态的**：一次 push 可能同时触发 CI 重新运行和 Review 状态失效，同步时需要确保按正确的优先级（终态 > Draft > 冲突 > CI > Review > 可合并）重新计算。

---

*报告完*
