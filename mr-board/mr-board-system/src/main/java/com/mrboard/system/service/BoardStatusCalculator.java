package com.mrboard.system.service;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BoardStatusCalculator {

    public String calculate(String platformStatus, Boolean hasConflict, String ciStatus, Boolean mergeable) {
        return calculate(platformStatus, hasConflict, ciStatus, mergeable, "", "pending", null);
    }

    public String calculate(String platformStatus, Boolean hasConflict, String ciStatus,
                            Boolean mergeable, String title, String approvalStatus,
                            List<String> reviewers) {
        if (platformStatus == null) platformStatus = "";
        if (ciStatus == null) ciStatus = "unknown";
        if (approvalStatus == null) approvalStatus = "pending";

        // 1. 终态
        if ("merged".equalsIgnoreCase(platformStatus)) {
            return "merged";
        }
        if ("closed".equalsIgnoreCase(platformStatus)) {
            return "closed";
        }

        // 2. 冲突（最高优先级阻塞态）
        if (Boolean.TRUE.equals(hasConflict)) {
            return "conflict";
        }

        // 3. CI 状态
        boolean ciRunning = "running".equalsIgnoreCase(ciStatus) || "pending".equalsIgnoreCase(ciStatus);
        boolean ciFailed = "failed".equalsIgnoreCase(ciStatus);

        if (ciRunning) {
            return "ci_checking";
        }
        if (ciFailed) {
            return "conflict"; // 构建失败归入冲突待解决
        }

        // 4. Review 状态
        boolean hasReviewer = reviewers != null && !reviewers.isEmpty();

        if (!hasReviewer || "pending".equalsIgnoreCase(approvalStatus)) {
            return "pending_review";
        }

        if ("changes_requested".equalsIgnoreCase(approvalStatus) || "reviewing".equalsIgnoreCase(approvalStatus)) {
            return "reviewing";
        }

        // 5. 可合并态
        if ("approved".equalsIgnoreCase(approvalStatus)) {
            if (Boolean.TRUE.equals(mergeable)) {
                return "ready";
            }
            return "conflict"; // approved 但不可合并（非冲突原因）
        }

        return "pending_review";
    }
}
