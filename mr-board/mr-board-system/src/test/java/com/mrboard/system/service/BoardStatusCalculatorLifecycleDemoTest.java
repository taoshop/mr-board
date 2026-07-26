package com.mrboard.system.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MR 看板状态生命周期流转演示测试。
 *
 * <p>本测试类按真实业务场景，演示一条 MR 从创建到最终合并或关闭的完整状态变更过程。
 * 每个测试方法对应生命周期中的一个关键节点，帮助理解 {@link BoardStatusCalculator} 的映射规则。
 *
 * @see BoardStatusCalculator
 * @see <a href="../../../../../../../../docs/mr-status-lifecycle.md">MR 状态生命周期文档</a>
 */
class BoardStatusCalculatorLifecycleDemoTest {

    private final BoardStatusCalculator calculator = new BoardStatusCalculator();

    @Test
    @DisplayName("场景1：新建 MR → 待 Review (open)")
    void scenario1_newMr_shouldBeOpen() {
        String status = calculator.calculate("open", false, "unknown", true, "feat: payment module");
        assertEquals("open", status);
    }

    @Test
    @DisplayName("场景2：Draft MR → 待 Review (open)")
    void scenario2_draftMr_shouldBeOpen() {
        String status = calculator.calculate("open", false, "unknown", true, "draft: payment module");
        assertEquals("open", status);
    }

    @Test
    @DisplayName("场景3：WIP MR → 待 Review (open)")
    void scenario3_wipMr_shouldBeOpen() {
        String status = calculator.calculate("open", false, "success", true, "WIP: payment module");
        assertEquals("open", status);
    }

    @Test
    @DisplayName("场景4：CI 运行中 → CI 检查中 (testing)")
    void scenario4_ciRunning_shouldBeTesting() {
        String status = calculator.calculate("open", false, "running", true, "feat: payment module");
        assertEquals("testing", status);
    }

    @Test
    @DisplayName("场景5：CI 等待中 → CI 检查中 (testing)")
    void scenario5_ciPending_shouldBeTesting() {
        String status = calculator.calculate("open", false, "pending", true, "feat: payment module");
        assertEquals("testing", status);
    }

    @Test
    @DisplayName("场景6：CI 失败 → CI 检查中 (failed)")
    void scenario6_ciFailed_shouldBeFailed() {
        String status = calculator.calculate("open", false, "failed", true, "feat: payment module");
        assertEquals("failed", status);
    }

    @Test
    @DisplayName("场景7：产生合并冲突 → 冲突待解决 (conflict)")
    void scenario7_hasConflict_shouldBeConflict() {
        String status = calculator.calculate("open", true, "success", false, "feat: payment module");
        assertEquals("conflict", status);
    }

    @Test
    @DisplayName("场景8：平台标记不可合并 → 冲突待解决 (conflict)")
    void scenario8_notMergeable_shouldBeConflict() {
        String status = calculator.calculate("open", false, "success", false, "feat: payment module");
        assertEquals("conflict", status);
    }

    @Test
    @DisplayName("场景9：无冲突 + CI 通过 + 非 Draft → 可合并 (ready)")
    void scenario9_allGreen_shouldBeReady() {
        String status = calculator.calculate("open", false, "success", true, "feat: payment module");
        assertEquals("ready", status);
    }

    @Test
    @DisplayName("场景10：GitLab 合并 → 已合并 (merged)")
    void scenario10_gitlabMerged_shouldBeMerged() {
        String status = calculator.calculate("merged", false, "success", false, "feat: payment module");
        assertEquals("merged", status);
    }

    @Test
    @DisplayName("场景11：GitHub 合并（state=closed + merged_at 存在）→ 已合并 (merged)")
    void scenario11_githubMerged_shouldBeMerged() {
        // 注意：GitHub API 对 merged PR 返回 state=closed，由 GitHubClient.mapGitHubState 映射为 "merged"
        // 此处模拟映射后的 platformStatus
        String status = calculator.calculate("merged", false, "success", false, "feat: payment module");
        assertEquals("merged", status);
    }

    @Test
    @DisplayName("场景12：关闭（未合并）→ 已关闭 (closed)")
    void scenario12_closed_shouldBeClosed() {
        String status = calculator.calculate("closed", false, "failed", false, "feat: payment module");
        assertEquals("closed", status);
    }

    @Test
    @DisplayName("场景13：已合并 MR 忽略其他条件 → 始终已合并 (merged)")
    void scenario13_mergedIgnoresOtherFlags_shouldBeMerged() {
        // 即使 hasConflict=true, ciStatus=failed, mergeable=false，终态锁定为 merged
        String status = calculator.calculate("merged", true, "failed", false, "feat: payment module");
        assertEquals("merged", status);
    }

    @Test
    @DisplayName("场景14：已关闭 MR 忽略其他条件 → 始终已关闭 (closed)")
    void scenario14_closedIgnoresOtherFlags_shouldBeClosed() {
        // 即使 hasConflict=true, ciStatus=failed, mergeable=false，终态锁定为 closed
        String status = calculator.calculate("closed", true, "failed", false, "feat: payment module");
        assertEquals("closed", status);
    }

    @Test
    @DisplayName("场景15：冲突解决后 CI 重新运行 → CI 检查中 (testing)")
    void scenario15_conflictResolvedCiRunning_shouldBeTesting() {
        // 冲突解决后，CI 重新触发，此时应显示 CI 检查中，而不是直接跳到可合并
        String status = calculator.calculate("open", false, "running", true, "feat: payment module");
        assertEquals("testing", status);
    }

    @Test
    @DisplayName("场景16：GitLab opened 状态 → 待 Review / 可合并（取决于其他条件）")
    void scenario16_gitlabOpened_shouldFollowOtherConditions() {
        // GitLab API 返回 state="opened"（注意不是 "open"），BoardStatusCalculator 不识别 "opened"
        // 因此会进入后续条件判断：无冲突、CI 通过 → ready
        String status = calculator.calculate("opened", false, "success", true, "feat: payment module");
        assertEquals("ready", status);
    }
}
