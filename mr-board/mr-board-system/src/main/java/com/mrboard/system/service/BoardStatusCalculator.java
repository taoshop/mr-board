package com.mrboard.system.service;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 看板状态计算器（v2 — CI 作为独立维度）。
 *
 * <p>根据 Git 平台原始状态、冲突标识、CI 状态、可合并标识、标题、评审状态及评审人列表，
 * 自动映射到看板六列之一：待 Review / Review 中 / 冲突待解决 / 可合并 / 已合并 / 已关闭。
 *
 * <p><strong>与 v1 的关键区别：</strong>CI 不再驱动列归属。CI 状态作为卡片上的独立标识展示，
 * 不再有单独的「CI 检查中」列。CI 失败作为冲突原因进入 conflict 列。
 *
 * <p><strong>计算优先级（从高到低）：</strong>
 * <ol>
 *   <li>platformStatus == "merged" → 已合并 (merged)</li>
 *   <li>platformStatus == "closed" → 已关闭 (closed)</li>
 *   <li>title startsWith "draft:" / "wip:" → 待 Review (pending_review)</li>
 *   <li>hasConflict == true → 冲突待解决 (conflict)</li>
 *   <li>ciStatus == "failed" → 冲突待解决 (conflict)，原因 = CI 失败</li>
 *   <li>无 reviewer 或 approvalStatus == "pending" → 待 Review (pending_review)</li>
 *   <li>approvalStatus == "changes_requested"/"reviewing" → Review中 (reviewing)</li>
 *   <li>approvalStatus == "approved" 且 mergeable == true → 可合并 (ready)</li>
 *   <li>approvalStatus == "approved" 但不可合并 → 冲突待解决 (conflict)</li>
 *   <li>默认 → 待 Review (pending_review)</li>
 * </ol>
 *
 * <p>CI running / pending 不决定列归属 — 卡片在 review 或 pending_review 列显示 CI 运行图标。
 *
 * @see <a href="../../../../../../../../docs/mr-status-lifecycle.md">MR 状态生命周期文档</a>
 */
@Component
public class BoardStatusCalculator {

    /**
     * 计算看板状态（简化版，无标题检查）。
     */
    public String calculate(String platformStatus, Boolean hasConflict, String ciStatus, Boolean mergeable) {
        return calculate(platformStatus, hasConflict, ciStatus, mergeable, "", "pending", null);
    }

    /**
     * 计算看板状态（完整版）。
     *
     * @param platformStatus Git 平台原始状态（open/closed/merged/opened）
     * @param hasConflict    是否存在合并冲突
     * @param ciStatus       CI 流水线状态（success/failed/running/pending/unknown）
     * @param mergeable      Git 平台是否标记为可合并
     * @param title          MR 标题（用于识别 Draft/WIP）
     * @param approvalStatus 评审状态（pending/reviewing/approved）
     * @param reviewers      评审人列表
     * @return 看板状态码：merged / closed / conflict / pending_review / reviewing / ready
     */
    public String calculate(String platformStatus, Boolean hasConflict, String ciStatus,
                            Boolean mergeable, String title, String approvalStatus,
                            List<String> reviewers) {
        if (platformStatus == null) platformStatus = "";
        if (ciStatus == null) ciStatus = "unknown";
        if (approvalStatus == null) approvalStatus = "pending";

        // 1. 终态锁定列：已合并 / 已关闭 —— 优先级最高
        if ("merged".equalsIgnoreCase(platformStatus)) {
            return "merged";
        }
        if ("closed".equalsIgnoreCase(platformStatus)) {
            return "closed";
        }

        // 2. Draft / WIP 拦截
        String titleLower = title != null ? title.toLowerCase() : "";
        boolean isDraft = titleLower.startsWith("draft:") || titleLower.startsWith("wip:");
        if (isDraft) {
            return "pending_review";
        }

        // 3. 冲突（最高优先级阻塞态）
        if (Boolean.TRUE.equals(hasConflict)) {
            return "conflict";
        }

        // 4. CI 失败 → 冲突待解决（CI 作为独立维度，不单独成列）
        if ("failed".equalsIgnoreCase(ciStatus)) {
            return "conflict";
        }
        // CI running/pending 不决定列归属 — 卡片上显示 CI 状态图标

        // 5. Review 状态（CI running/pending 时不单独成列，继续按 Review 状态判断）
        boolean hasReviewer = reviewers != null && !reviewers.isEmpty();

        if (!hasReviewer || "pending".equalsIgnoreCase(approvalStatus)) {
            return "pending_review";
        }

        if ("changes_requested".equalsIgnoreCase(approvalStatus) || "reviewing".equalsIgnoreCase(approvalStatus)) {
            return "reviewing";
        }

        // 6. 可合并态
        if ("approved".equalsIgnoreCase(approvalStatus)) {
            if (Boolean.TRUE.equals(mergeable)) {
                return "ready";
            }
            return "conflict"; // approved 但不可合并（非冲突原因）
        }

        return "pending_review";
    }
}
