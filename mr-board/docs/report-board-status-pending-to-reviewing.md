# 看板状态流转规则报告：pending_review → reviewing

## 一、问题概述

针对「待 Review（`pending_review`）」列中的 MR 何时会被系统分配或允许流转到「Review 中（`reviewing`）」列，分别梳理了**系统自动分配逻辑**与**手动拖拽权限规则**。

---

## 二、系统自动分配规则（`BoardStatusCalculator.calculate`）

系统在同步/刷新时，通过 `BoardStatusCalculator.calculate()` 重新计算 MR 的看板状态。`pending_review` → `reviewing` 的判定条件如下：

| 优先级 | 条件 | 字段/值 | 说明 |
|--------|------|---------|------|
| 1 | 非终态 | `platformStatus` ≠ `merged` / `closed` | 已合并/已关闭的 MR 直接进入终态列，不再参与后续判断 |
| 2 | 非 Draft/WIP | `title` 不以 `draft:` / `wip:` 开头 | Draft 标题的 MR 强制留在 `pending_review` |
| 3 | 无代码冲突 | `hasConflict` ≠ `true` | 存在冲突直接进入 `conflict` 列 |
| 4 | CI 未失败 | `ciStatus` ≠ `failed` | CI 失败直接进入 `conflict` 列 |
| 5 | **有 reviewer** | `reviewers` 列表非空 | 无 reviewer 留在 `pending_review` |
| 6 | **approvalStatus 为 `reviewing` 或 `changes_requested`** | `approvalStatus ∈ {reviewing, changes_requested}` | `pending` 留在 `pending_review`；`approved` 进入 `ready` 或 `conflict`（若不可合并） |

### 2.1 关键代码路径

```java
// BoardStatusCalculator.java:51-58
boolean hasReviewer = reviewers != null && !reviewers.isEmpty();

if (!hasReviewer || "pending".equalsIgnoreCase(approvalStatus)) {
    return "pending_review";
}

if ("changes_requested".equalsIgnoreCase(approvalStatus) || "reviewing".equalsIgnoreCase(approvalStatus)) {
    return "reviewing";
}
```

### 2.2 特殊说明：CI 运行中的处理

当前实现中，CI 状态为 `running` 或 `pending` 时，**不会**将 MR 分配到 `ci_checking` 列，而是继续按上述 Review 维度判断。这意味着：

- CI 在跑 + 有 reviewer + approvalStatus = `reviewing` → 分到 `reviewing`
- CI 在跑 + 无 reviewer → 分到 `pending_review`

---

## 三、手动拖拽权限规则（`MrsController.validateStatusTransition`）

用户在看板上通过拖拽手动改变 MR 状态时，后端校验规则如下：

| 规则 | 限制内容 | 影响 |
|------|---------|------|
| 角色准入 | `ADMIN` / `PM` / `TECHLEAD` / `DEVELOPER` | `REVIEWER` 角色无法拖拽 |
| 终态权限 | `merged` / `closed` 仅允许 `ADMIN` / `TECHLEAD` 操作 | 普通开发者无法直接拖入已合并/已关闭 |
| 冲突拦截 | `hasConflict == true` 的 MR 禁止拖入 `ready` / `merged` | 必须先解决冲突 |
| 归属限制 | `DEVELOPER` 只能拖拽自己创建的 MR | 防止误操作他人 MR |

### 3.1 结论：pending_review → reviewing 的手动拖拽

**无额外限制**。只要用户具备上述角色权限，`pending_review` 与 `reviewing` 之间可自由拖拽流转。

---

## 四、当前发现的不一致问题

### 4.1 `ci_checking` 列定义与状态机不匹配

`BoardController.COLUMNS` 已定义 `ci_checking`（CI 检查中）列，但 `BoardStatusCalculator` **没有任何逻辑会将 MR 分配到 `ci_checking`**。导致：

- `ci_checking` 列永远为空；
- CI running/pending 的 MR 实际散落在 `pending_review` 或 `reviewing` 列中；
- 用户无法在独立列中直观看到「CI 正在运行」的 MR。

### 4.2 修复建议（如需）

在 `BoardStatusCalculator` 第 4 步 CI 状态判断中，增加对 `ciRunning` 的处理：

```java
if (ciRunning) {
    return "ci_checking";
}
```

并将该分支置于 Review 状态判断之前，确保 CI 未结束时 MR 优先进入 `ci_checking`。

---

## 五、总结

| 场景 | pending_review → reviewing 条件 |
|------|--------------------------------|
| **系统自动分配** | 有 reviewer 且 approvalStatus 为 `reviewing` / `changes_requested`，同时无冲突、CI 未失败、非 Draft |
| **手动拖拽** | 用户具备 `ADMIN`/`PM`/`TECHLEAD`/`DEVELOPER` 角色，且目标列不是 `merged`/`closed` |

---

*报告生成日期：2026-07-27*  
*关联代码文件：*
- `mr-board-system/src/main/java/com/mrboard/system/service/BoardStatusCalculator.java`
- `mr-board-system/src/main/java/com/mrboard/system/controller/MrsController.java`
- `mr-board-system/src/main/java/com/mrboard/system/controller/BoardController.java`
