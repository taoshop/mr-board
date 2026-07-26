# MR 状态生命周期与流转规则

## 1. 概述

本文档描述 Git MR 看板系统中 **Merge Request（MR）** 的完整状态生命周期，涵盖从创建到最终合并或关闭的全流程。看板以 **7 列** 形式展示 MR 当前所处阶段，支持自动同步与手动拖拽两种状态驱动方式。

---

## 2. 看板七列定义

| 看板列名 | 内部状态码 | 触发条件 | 颜色标识 |
|---|---|---|---|
| **待 Review** | `open` | MR 新建、Draft 转 Ready、Reopen | 默认白色/灰色 |
| **Review 中** | `reviewing` | 开发者手动拖拽进入（人工状态） | 蓝色 |
| **CI 检查中** | `testing` / `failed` | CI 流水线运行中或最近一次失败 | 橙色（进行中）/ 红色（失败） |
| **冲突待解决** | `conflict` | 存在合并冲突或 Git 平台标记为不可合并 | 红色高亮 |
| **可合并** | `ready` | 无冲突、CI 通过、非 Draft、可合并 | 绿色 |
| **已合并** | `merged` | Git 平台状态为 merged | 紫色（锁定） |
| **已关闭** | `closed` | Git 平台状态为 closed（未合并） | 深灰色（锁定） |

> **锁定列**：已合并 / 已关闭两列为**终态锁定列**，卡片不可拖拽，状态只能通过 Git 平台变更触发同步更新。

---

## 3. 状态流转图

```
┌─────────────┐     开始Review      ┌─────────────┐
│  待 Review   │ ──────────────────▶ │  Review 中   │
│   (open)    │                     │ (reviewing) │
└──────┬──────┘                     └──────┬──────┘
       │                                   │
       │ CI触发/运行中                        │ CI触发/运行中
       ▼                                   ▼
┌─────────────┐     CI失败/修复中      ┌─────────────┐
│ CI检查中     │ ◀──────────────────▶ │ CI检查中     │
│ (testing)   │                      │  (failed)   │
└──────┬──────┘                      └──────┬──────┘
       │                                   │
       │ 发现冲突                            │ 发现冲突
       ▼                                   ▼
┌─────────────┐     冲突解决+CI通过   ┌─────────────┐
│ 冲突待解决   │ ──────────────────▶ │   可合并     │
│  (conflict) │ ◀──────────────────  │   (ready)   │
└─────────────┘     新冲突产生         └──────┬──────┘
                                             │
                    ┌────────────────────────┘
                    │ 拖拽合并 / Git平台合并
                    ▼
            ┌─────────────┐
            │   已合并     │
            │  (merged)   │  ◀── 终态锁定
            └─────────────┘
                    ▲
                    │ 拖拽关闭 / Git平台关闭
                    │
            ┌─────────────┐
            │   已关闭     │
            │  (closed)   │  ◀── 终态锁定
            └─────────────┘
```

---

## 4. 状态计算规则（自动）

`BoardStatusCalculator` 根据以下 **5 个输入维度** 自动计算 MR 看板状态：

1. **platformStatus** — Git 平台原始状态（`open` / `closed` / `merged` / `opened`）
2. **hasConflict** — 是否存在合并冲突（`true` / `false`）
3. **ciStatus** — CI 流水线状态（`success` / `failed` / `running` / `pending` / `unknown`）
4. **mergeable** — Git 平台是否标记为可合并（`true` / `false`）
5. **title** — MR 标题（用于识别 Draft / WIP）

### 计算优先级（从高到低）

```
platformStatus == "merged"        → 已合并 (merged)
platformStatus == "closed"        → 已关闭 (closed)
hasConflict == true               → 冲突待解决 (conflict)
ciStatus == "failed"              → CI检查中 (failed)
ciStatus == "running"/"pending"   → CI检查中 (testing)
ciStatus != "success" && != "unknown" → CI检查中 (testing)
mergeable == false                → 冲突待解决 (conflict)
title startsWith "draft:" / "wip:" → 待 Review (open)
默认                              → 可合并 (ready)
```

> **Review 中** 为纯人工状态，不由 `BoardStatusCalculator` 自动计算，只能通过看板拖拽进入。

---

## 5. 状态变更方式

### 5.1 自动同步（Git 平台驱动）

| 平台事件 | 看板状态变化 | 说明 |
|---|---|---|
| MR 新建 / Reopen | 待 Review | 首次同步或从 closed 恢复 |
| CI 流水线触发 | CI 检查中 | `ciStatus` 变为 running/pending |
| CI 成功 | 可合并 / 待 Review | 若无冲突且非 Draft → 可合并 |
| CI 失败 | CI 检查中 (failed) | 卡片显示红色失败标识 |
| 产生冲突 | 冲突待解决 | `hasConflict` 或 `mergeable=false` |
| 冲突解决 | 可合并 / CI 检查中 | 取决于 CI 状态 |
| MR 被合并 | 已合并 | 终态锁定，不可拖拽 |
| MR 被关闭 | 已关闭 | 终态锁定，不可拖拽 |

### 5.2 手动拖拽（用户驱动）

| 拖拽操作 | 权限要求 | 结果 | 约束 |
|---|---|---|---|
| 待 Review → Review 中 | Developer（自己的MR） | 状态变为 `reviewing` | — |
| Review 中 → 可合并 | Tech Lead / PM | 状态变为 `ready` | 需 CI 通过且无冲突 |
| 可合并 → 已合并 | Tech Lead | 调用 Git API 执行合并 | 弹出二次确认框 |
| 任意 → 已关闭 | Tech Lead | 调用 Git API 关闭 MR | 弹出二次确认框 |
| 冲突待解决 → 任意 | 任何人 | **被拦截** | 必须先解决冲突 |
| 已合并 / 已关闭 → 任意 | 任何人 | **被拦截** | 终态锁定 |

---

## 6. 状态历史记录

每次状态变更（无论自动同步还是手动拖拽）均在 `mr_status_history` 表中留下记录：

| 字段 | 说明 |
|---|---|
| `mr_id` | 关联 MR |
| `from_status` | 变更前状态 |
| `to_status` | 变更后状态 |
| `change_type` | `SYNC`（同步）/ `MANUAL`（手动拖拽）/ `GIT_API`（Git平台回调） |
| `operator` | 操作人（同步时为 system） |
| `changed_at` | 变更时间 |

---

## 7. 边界与异常场景

| 场景 | 预期行为 |
|---|---|
| GitHub merged PR 返回 `state=closed` | `mapGitHubState` 识别 `merged_at` 存在 → 映射为 `merged` |
| GitHub closed PR `merged_at=null` | 保持 `closed`，不进入已合并列 |
| CI 状态为 `skipped` / `canceled` | 映射为 `unknown`，不影响看板状态计算 |
| 手动拖拽时 Git API 调用失败 | 事务回滚，看板卡片回弹，状态不变 |
| 同步过程中分页失败 | 抛出异常，同步标记为 failed，`lastSyncAt` 不更新 |
| 服务器时区非 UTC | `updatedAfter` 必须携带正确时区偏移，避免增量同步遗漏 |

---

## 8. 相关代码位置

- **状态计算**：`mr-board/mr-board-system/src/main/java/com/mrboard/system/service/BoardStatusCalculator.java`
- **同步服务**：`mr-board/mr-board-system/src/main/java/com/mrboard/system/service/SyncService.java`
- **GitHub 状态映射**：`mr-board/mr-board-system/src/main/java/com/mrboard/system/sync/GitHubClient.java`
- **状态历史**：`mr-board/mr-board-system/src/main/java/com/mrboard/system/entity/MrStatusHistory.java`

---

*本文档随 `feature/mr-status-lifecycle-demo` 分支提交，用于演示和评审 MR 状态变更的完整生命周期。*
