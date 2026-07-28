# BoardStatusCalculator Review 状态边界模糊问题分析报告

> 分析日期：2026-07-29  
> 相关分支：`feature/requirement-verify`  
> 相关 PR：#12  

---

## 一、问题概述

在 MR 看板系统中，`BoardStatusCalculator` 负责将 Git 平台的原始状态映射到看板六列之一。其中 **`pending_review`（待 Review）** 与 **`reviewing`（Review 中）** 两个状态的边界存在**平台语义不一致**和**判断逻辑不精确**的问题，导致同步后看板列归属与业务直觉不符，用户不得不通过拖拽设置 `manualStatus` 进行人工修正。

---

## 二、现状代码梳理

### 2.1 BoardStatusCalculator 核心逻辑

```java
boolean hasReviewer = reviewers != null && !reviewers.isEmpty();

if (!hasReviewer || "pending".equalsIgnoreCase(approvalStatus)) {
    return "pending_review";
}

if ("changes_requested".equalsIgnoreCase(approvalStatus) || "reviewing".equalsIgnoreCase(approvalStatus)) {
    return "reviewing";
}
```

**判断路径（简化）：**

| 条件命中 | 输出状态 |
|---------|---------|
| `!hasReviewer` 或 `approvalStatus == "pending"` | `pending_review` |
| `approvalStatus == "changes_requested"` / `"reviewing"` | `reviewing` |
| `approvalStatus == "approved"` + `mergeable == true` | `ready` |
| `approvalStatus == "approved"` + `mergeable == false` | `conflict` |
| 默认兜底 | `pending_review` |

### 2.2 双平台 `fetchApprovalStatus()` 返回值对比

#### GitHubClient

```java
if (reviews == null || reviews.isEmpty()) {
    return "pending";
}
// ...
if (hasApproved && !hasChangesRequested) {
    return "approved";
}
if (hasChangesRequested) {
    return "reviewing";
}
return hasMeaningfulReview ? "reviewing" : "pending_review";
```

| 平台 review 状态 | 返回值 |
|----------------|--------|
| 无 review | `"pending"` |
| 只有 `COMMENTED` | `"pending_review"` |
| 有 `CHANGES_REQUESTED` | `"reviewing"` |
| 有 `APPROVED` 且无拒绝 | `"approved"` |
| 异常 | `"pending"` |

#### GitLabClient

```java
if (Boolean.TRUE.equals(approved)) {
    return "approved";
}
if (approvedBy != null && !approvedBy.isEmpty()) {
    return "reviewing";
}
return "pending";
```

| 平台审批状态 | 返回值 |
|------------|--------|
| `approved == true` | `"approved"` |
| `approvedBy` 非空（包括仅评论） | `"reviewing"` |
| 其他 | `"pending"` |

---

## 三、边界模糊场景对照表

| 业务场景 | GitHub 返回值 | GitLab 返回值 | BoardStatusCalculator 结果 | 一致性 |
|---------|--------------|--------------|--------------------------|--------|
| 指派了 reviewer，但**尚未开始** review | `"pending_review"` | `"pending"` | GitHub → `pending_review` ✅<br>GitLab → `pending_review` ✅ | ✅ 一致 |
| 有人**仅评论**（COMMENTED / 评论式审批） | `"pending_review"` | `"reviewing"` | GitHub → `pending_review`<br>GitLab → `reviewing` | ❌ **不一致** |
| 有人请求修改（CHANGES_REQUESTED） | `"reviewing"` | `"reviewing"` | `reviewing` | ✅ 一致 |
| 已批准且可合并 | `"approved"` | `"approved"` | `ready` | ✅ 一致 |

### 核心矛盾点

**场景：有人评论但未批准 / 未请求修改**

- **GitHub 视角**：`COMMENTED` 不算有意义的 review → `"pending_review"` → 看板落入 **待 Review**
- **GitLab 视角**：`approved_by` 列表非空（评论也会进列表）→ `"reviewing"` → 看板落入 **Review 中**

**同一业务事实，在两个平台下会被分到不同列。**

---

## 四、manualStatus 的兜底价值分析

`manualStatus` 字段的设计意图是：当用户通过拖拽将 MR 移动到某列时，记录这个手动选择，后续同步不再覆盖。

```java
// SyncService.java:418
if (existing.getManualStatus() != null) {
    entity.setBoardStatus(existing.getManualStatus());
}
```

### manualStatus 被高频使用的状态

| 看板状态 | 是否需要 manualStatus 修正 | 原因 |
|---------|------------------------|------|
| `merged` | ❌ 不需要 | 平台终态，硬事实 |
| `closed` | ❌ 不需要 | 平台终态，硬事实 |
| `conflict` | ⚠️ 极少需要 | `hasConflict == true` 是硬事实，但 CI 失败导致的 conflict 可能有争议 |
| `ready` | ⚠️ 极少需要 | `approved && mergeable` 是硬事实 |
| **`pending_review`** | ✅ **高频需要** | 与 `reviewing` 的边界依赖平台 API 语义，容易误判 |
| **`reviewing`** | ✅ **高频需要** | 同上，特别是"仅评论"场景的跨平台差异 |

**结论：`manualStatus` 的最大业务价值，就是修正 `pending_review` ↔ `reviewing` 之间的平台语义差异和模糊边界。**

---

## 五、修复建议（两个方向）

### 方向 A：统一双平台语义（推荐）

在 `GitHubClient` / `GitLabClient` 层增加适配逻辑，将平台原始返回值统一映射到内部标准枚举，消除平台差异。

**建议内部标准枚举：**

```
PENDING      → 待 Review（无 reviewer 或 review 尚未开始）
REVIEWING    → Review 中（有实质性 review 动作：评论+修改意见+批准）
APPROVED     → 已批准（可进入 ready/conflict 判断）
```

**适配层示例：**

| 平台原始值 | GitHub 适配后 | GitLab 适配后 |
|-----------|-------------|-------------|
| `"pending"` | `PENDING` | `PENDING` |
| `"pending_review"` | `PENDING` | — |
| `"reviewing"` | `REVIEWING` | `REVIEWING` |
| `"approved"` | `APPROVED` | `APPROVED` |

> 对于"仅评论"场景，可以统一规定：只要有非作者本人的评论，即视为 `REVIEWING`，避免 GitHub 下 COMMENTED 被归类为 `PENDING` 的业务歧义。

### 方向 B：弱化 Review 状态细分

将 `pending_review` 和 `reviewing` 合并为更粗的 `in_review` 状态，看板上不再区分"待 Review"和"Review 中"，改为：

- 展示所有"未到达终态且未 ready"的 MR 在一个统一的 **Review 区**
- 通过卡片上的 reviewer 列表 / approvalStatus 标签来区分进度
- 减少用户手动修正的需求

**影响评估：**

| 维度 | 影响 |
|-----|------|
| 产品体验 | 看板列数从 6 列减为 5 列，更简洁 |
| 拖拽逻辑 | 减少一个可拖拽目标列，降低误操作 |
| manualStatus 使用频率 | 大幅降低 |
| 需求偏离 | 原始需求要求区分"待 Review"和"Review 中"，需产品确认 |

---

## 六、结论

1. **`pending_review` 与 `reviewing` 的边界模糊是客观存在的**，根因是 GitHub / GitLab API 对"review 进度"的定义不同，而非 `BoardStatusCalculator` 本身的算法错误。
2. **`manualStatus` 当前起到了有效的兜底作用**，但依赖用户手动修正，体验成本较高。
3. **推荐优先采用方向 A（统一双平台语义）**，在不改变看板列设计的前提下，从根源消除平台差异，降低 `manualStatus` 的触发频率。
4. 若产品侧愿意简化状态模型，方向 B（合并为 `in_review`）可以进一步降低系统复杂度。

---

## 附录：相关代码文件

| 文件 | 职责 |
|-----|------|
| `BoardStatusCalculator.java` | 看板状态自动计算 |
| `GitHubClient.java:229-266` | GitHub 平台 approvalStatus 获取 |
| `GitLabClient.java:250-274` | GitLab 平台 approvalStatus 获取 |
| `SyncService.java:416-419` | manualStatus 覆盖逻辑 |
| `MrsController.java:218-220` | 拖拽时 manualStatus 写入/清除 |

---

*报告生成于 2026-07-29，基于 PR #12 代码基线。*
