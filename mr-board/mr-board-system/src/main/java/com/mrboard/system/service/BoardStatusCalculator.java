package com.mrboard.system.service;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 看板状态计算器。
 *
 * <p>根据 Git 平台原始状态、冲突标识、CI 状态、可合并标识及标题，
 * 自动映射到看板七列之一：待 Review / Review 中 / CI 检查中 / 冲突待解决 / 可合并 / 已合并 / 已关闭。
 *
 * <p><strong>计算优先级（从高到低）：</strong>
 * <ol>
 *   <li>platformStatus == "merged" → 已合并 (merged)</li>
 *   <li>platformStatus == "closed" → 已关闭 (closed)</li>
 *   <li>hasConflict == true → 冲突待解决 (conflict)</li>
 *   <li>ciStatus == "failed" → CI检查中-failed (failed)</li>
 *   <li>ciStatus == "running"/"pending" → CI检查中-testing (testing)</li>
 *   <li>ciStatus != "success" && != "unknown" → CI检查中-testing (testing)</li>
 *   <li>mergeable == false → 冲突待解决 (conflict)</li>
 *   <li>title startsWith "draft:" / "wip:" → 待 Review (open)</li>
 *   <li>默认 → 可合并 (ready)</li>
 * </ol>
 *
 * <p><em>注意："Review 中" (reviewing) 为纯人工状态，不由本计算器自动计算，
 * 只能通过看板手动拖拽进入。</em>
 *
 * @see <a href="../../../../../../../../docs/mr-status-lifecycle.md">MR 状态生命周期文档</a>
 */
@Component
public class BoardStatusCalculator {

    /**
     * 计算看板状态（简化版，无标题检查）。
     */
    public String calculate(String platformStatus, Boolean hasConflict, String ciStatus, Boolean mergeable) {
        return calculate(platformStatus, hasConflict, ciStatus, mergeable, "");
    }

    /**
     * 计算看板状态（完整版）。
     *
     * @param platformStatus Git 平台原始状态（open/closed/merged/opened）
     * @param hasConflict    是否存在合并冲突
     * @param ciStatus       CI 流水线状态（success/failed/running/pending/unknown）
     * @param mergeable      Git 平台是否标记为可合并
     * @param title          MR 标题（用于识别 Draft/WIP）
     * @return 看板状态码：merged / closed / conflict / failed / testing / open / ready
     */
    public String calculate(String platformStatus, Boolean hasConflict, String ciStatus, Boolean mergeable, String title) {
        if (platformStatus == null) platformStatus = "";
        if (ciStatus == null) ciStatus = "unknown";

        // 终态锁定列：已合并 / 已关闭 —— 优先级最高
        if ("merged".equalsIgnoreCase(platformStatus)) {
            return "merged";
        }
        if ("closed".equalsIgnoreCase(platformStatus)) {
            return "closed";
        }

        String titleLower = title != null ? title.toLowerCase() : "";
        boolean isDraft = titleLower.startsWith("draft:") || titleLower.startsWith("wip:");

        // 存在合并冲突 → 冲突待解决
        if (Boolean.TRUE.equals(hasConflict)) {
            return "conflict";
        }

        boolean ciFailed = "failed".equalsIgnoreCase(ciStatus);
        boolean ciRunning = "running".equalsIgnoreCase(ciStatus) || "pending".equalsIgnoreCase(ciStatus);
        boolean ciSuccess = "success".equalsIgnoreCase(ciStatus);

        // CI 失败 → CI 检查中（失败标识）
        if (ciFailed) {
            return "failed";
        }

        // CI 运行中/等待中 → CI 检查中（进行中）
        if (ciRunning) {
            return "testing";
        }

        // CI 状态非成功且非未知 → CI 检查中（进行中）
        if (!ciSuccess && !"unknown".equalsIgnoreCase(ciStatus)) {
            return "testing";
        }

        // 平台标记为不可合并 → 冲突待解决
        if (Boolean.FALSE.equals(mergeable)) {
            return "conflict";
        }

        // Draft / WIP → 待 Review
        if (isDraft) {
            return "open";
        }

        // 无冲突、CI 通过、非 Draft、可合并 → 可合并
        return "ready";
    }
}
