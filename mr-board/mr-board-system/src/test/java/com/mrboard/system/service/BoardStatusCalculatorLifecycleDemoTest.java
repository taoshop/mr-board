package com.mrboard.system.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    @DisplayName("场景1：新建 MR → 待 Review (pending_review)")
    void scenario1_newMr_shouldBePendingReview() {
        String status = calculator.calculate("opened", false, "unknown", true, "feat: payment module", "pending", List.of());
        assertEquals("pending_review", status);
    }

    @Test
    @DisplayName("场景2：Draft MR → 待 Review (pending_review)")
    void scenario2_draftMr_shouldBePendingReview() {
        String status = calculator.calculate("opened", false, "unknown", true, "draft: payment module", "pending", List.of());
        assertEquals("pending_review", status);
    }

    @Test
    @DisplayName("场景3：WIP MR → 待 Review (pending_review)")
    void scenario3_wipMr_shouldBePendingReview() {
        String status = calculator.calculate("opened", false, "success", true, "WIP: payment module", "pending", List.of());
        assertEquals("pending_review", status);
    }

    @Test
    @DisplayName("场景4：CI 运行中 + Review 中 → Review 中 (reviewing)（CI 作为独立维度不驱动列）")
    void scenario4_ciRunning_shouldBeReviewing() {
        String status = calculator.calculate("opened", false, "running", true, "feat: payment module", "reviewing", List.of("alice"));
        assertEquals("reviewing", status);
    }

    @Test
    @DisplayName("场景5：CI 等待中 + Review 中 → Review 中 (reviewing)（CI 作为独立维度不驱动列）")
    void scenario5_ciPending_shouldBeReviewing() {
        String status = calculator.calculate("opened", false, "pending", true, "feat: payment module", "reviewing", List.of("alice"));
        assertEquals("reviewing", status);
    }

    @Test
    @DisplayName("场景6：CI 失败 + Review 中 → 冲突待解决 (conflict)（CI failed 归入 conflict 列）")
    void scenario6_ciFailed_shouldBeConflict() {
        // CI 失败归入冲突待解决列，卡片上展示 CI 失败标识
        String status = calculator.calculate("opened", false, "failed", true, "feat: payment module", "reviewing", List.of("alice"));
        assertEquals("conflict", status);
    }

    @Test
    @DisplayName("场景7：产生合并冲突 → 冲突待解决 (conflict)")
    void scenario7_hasConflict_shouldBeConflict() {
        String status = calculator.calculate("opened", true, "success", false, "feat: payment module", "approved", List.of("alice"));
        assertEquals("conflict", status);
    }

    @Test
    @DisplayName("场景8：平台标记不可合并 → 冲突待解决 (conflict)")
    void scenario8_notMergeable_shouldBeConflict() {
        String status = calculator.calculate("opened", false, "success", false, "feat: payment module", "approved", List.of("alice"));
        assertEquals("conflict", status);
    }

    @Test
    @DisplayName("场景9：无冲突 + CI 通过 + Review 通过 → 可合并 (ready)")
    void scenario9_allGreen_shouldBeReady() {
        String status = calculator.calculate("opened", false, "success", true, "feat: payment module", "approved", List.of("alice"));
        assertEquals("ready", status);
    }

    @Test
    @DisplayName("场景10：GitLab 合并 → 已合并 (merged)")
    void scenario10_gitlabMerged_shouldBeMerged() {
        String status = calculator.calculate("merged", false, "success", false, "feat: payment module", "approved", List.of("alice"));
        assertEquals("merged", status);
    }

    @Test
    @DisplayName("场景11：GitHub 合并（state=closed + merged_at 存在）→ 已合并 (merged)")
    void scenario11_githubMerged_shouldBeMerged() {
        // 注意：GitHub API 对 merged PR 返回 state=closed，由 GitHubClient.mapGitHubState 映射为 "merged"
        // 此处模拟映射后的 platformStatus
        String status = calculator.calculate("merged", false, "success", false, "feat: payment module", "approved", List.of("alice"));
        assertEquals("merged", status);
    }

    @Test
    @DisplayName("场景12：关闭（未合并）→ 已关闭 (closed)")
    void scenario12_closed_shouldBeClosed() {
        String status = calculator.calculate("closed", false, "failed", false, "feat: payment module", "reviewing", List.of("alice"));
        assertEquals("closed", status);
    }

    @Test
    @DisplayName("场景13：已合并 MR 忽略其他条件 → 始终已合并 (merged)")
    void scenario13_mergedIgnoresOtherFlags_shouldBeMerged() {
        // 即使 hasConflict=true, ciStatus=failed, mergeable=false，终态锁定为 merged
        String status = calculator.calculate("merged", true, "failed", false, "feat: payment module", "approved", List.of("alice"));
        assertEquals("merged", status);
    }

    @Test
    @DisplayName("场景14：已关闭 MR 忽略其他条件 → 始终已关闭 (closed)")
    void scenario14_closedIgnoresOtherFlags_shouldBeClosed() {
        // 即使 hasConflict=true, ciStatus=failed, mergeable=false，终态锁定为 closed
        String status = calculator.calculate("closed", true, "failed", false, "feat: payment module", "approved", List.of("alice"));
        assertEquals("closed", status);
    }

    @Test
    @DisplayName("场景15：冲突解决后 CI 重新运行 + Review 中 → Review 中 (reviewing)（CI 不驱动列）")
    void scenario15_conflictResolvedCiRunning_shouldBeReviewing() {
        // 冲突解决后，CI 重新触发，此时仍在 Review 中列，卡片显示 CI 运行中标识
        String status = calculator.calculate("opened", false, "running", true, "feat: payment module", "reviewing", List.of("alice"));
        assertEquals("reviewing", status);
    }

    @Test
    @DisplayName("场景16：有 Reviewer 但未完成 → Review 中 (reviewing)")
    void scenario16_hasReviewerNotApproved_shouldBeReviewing() {
        String status = calculator.calculate("opened", false, "success", true, "feat: payment module", "reviewing", List.of("alice"));
        assertEquals("reviewing", status);
    }

    @Test
    @DisplayName("场景17：CI 失败 + approved + 不可合并 → 冲突待解决 (conflict)（CI failed 优先归入 conflict）")
    void scenario17_ciFailedNotMergeable_shouldBeConflict() {
        // CI 失败归入冲突待解决列，卡片展示 CI 失败标识
        String status = calculator.calculate("opened", false, "failed", false, "feat: payment module", "approved", List.of("alice"));
        assertEquals("conflict", status);
    }

    @Test
    @DisplayName("场景18：CI 取消 (cancelled) → 不冲突，视为 unknown 无 CI 状态 → pending_review")
    void scenario18_ciCancelled_shouldMapToPendingReview() {
        // cancelled/skipped 不会触发冲突处理，按无 CI 处理
        String status = calculator.calculate("opened", false, "cancelled", true, "feat: payment module", "pending", List.of());
        assertEquals("pending_review", status);
    }

    @Test
    @DisplayName("场景19：CI 被跳过 (skipped) → 同 cancelled 处理 → pending_review")
    void scenario19_ciSkipped_shouldMapToPendingReview() {
        String status = calculator.calculate("opened", false, "skipped", true, "feat: payment module", "pending", List.of());
        assertEquals("pending_review", status);
    }

    @Test
    @DisplayName("场景20：CI unknown → 按无 CI 处理 → pending_review")
    void scenario20_ciUnknown_shouldMapToPendingReview() {
        String status = calculator.calculate("opened", false, "unknown", true, "feat: payment module", "pending", List.of());
        assertEquals("pending_review", status);
    }

    @Test
    @DisplayName("场景21：冲突 + CI 运行中 → 冲突优先（冲突优先级高于 CI）")
    void scenario21_conflictWithCiRunning_shouldBeConflict() {
        // 冲突是最高优先级阻塞态，优先于 CI 状态
        String status = calculator.calculate("opened", true, "running", true, "feat: payment module", "reviewing", List.of("alice"));
        assertEquals("conflict", status);
    }

    @Test
    @DisplayName("场景22：冲突 + CI 通过，但不可合并 → 冲突待解决")
    void scenario22_conflictCiPassed_shouldBeConflict() {
        String status = calculator.calculate("opened", true, "success", false, "feat: payment module", "reviewing", List.of("alice"));
        assertEquals("conflict", status);
    }
}
