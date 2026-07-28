package com.mrboard.system.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GitHubClient} 中 GitHub API 状态映射逻辑的单元测试。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>mapCiStatus — GitHub check-run status/conclusion → 内部状态</li>
 *   <li>mapGitHubState — GitHub PR state + merged_at → merged/closed/open</li>
 * </ul>
 */
class GitHubClientStatusMappingTest {

    private final GitHubClient client = new GitHubClient(null, null, null, null);

    @Test
    @DisplayName("check-run completed + success → success")
    void completedSuccess_shouldReturnSuccess() throws Exception {
        assertEquals("success", invokeMapCiStatus("completed", "success"));
    }

    @Test
    @DisplayName("check-run completed + failure → failed")
    void completedFailure_shouldReturnFailed() throws Exception {
        assertEquals("failed", invokeMapCiStatus("completed", "failure"));
    }

    @Test
    @DisplayName("check-run completed + failed (conclusion variant) → failed")
    void completedFailed_shouldReturnFailed() throws Exception {
        assertEquals("failed", invokeMapCiStatus("completed", "failed"));
    }

    @Test
    @DisplayName("check-run completed + cancelled → canceled")
    void completedCancelled_shouldReturnCanceled() throws Exception {
        assertEquals("canceled", invokeMapCiStatus("completed", "cancelled"));
    }

    @Test
    @DisplayName("check-run completed + skipped → canceled")
    void completedSkipped_shouldReturnCanceled() throws Exception {
        assertEquals("canceled", invokeMapCiStatus("completed", "skipped"));
    }

    @Test
    @DisplayName("check-run completed + unknown conclusion → unknown")
    void completedUnknown_shouldReturnUnknown() throws Exception {
        assertEquals("unknown", invokeMapCiStatus("completed", "stale"));
    }

    @Test
    @DisplayName("check-run completed + null conclusion → unknown")
    void completedNullConclusion_shouldReturnUnknown() throws Exception {
        assertEquals("unknown", invokeMapCiStatus("completed", null));
    }

    @Test
    @DisplayName("check-run in_progress → pending（源码中非 literal running 均归 pending）")
    void inProgress_shouldReturnPending() throws Exception {
        assertEquals("pending", invokeMapCiStatus("in_progress", null));
    }

    @Test
    @DisplayName("check-run queued → pending")
    void queued_shouldReturnPending() throws Exception {
        assertEquals("pending", invokeMapCiStatus("queued", null));
    }

    @Test
    @DisplayName("check-run pending → pending")
    void pendingStatus_shouldReturnPending() throws Exception {
        assertEquals("pending", invokeMapCiStatus("pending", null));
    }

    @Test
    @DisplayName("check-run running → running")
    void runningStatus_shouldReturnRunning() throws Exception {
        assertEquals("running", invokeMapCiStatus("running", null));
    }

    // ==========  mapGitHubState ==========

    @Test
    @DisplayName("PR state=open → opened")
    void stateOpen_shouldReturnOpened() throws Exception {
        Map<String, Object> pr = new HashMap<>();
        pr.put("state", "open");
        pr.put("merged_at", null);
        assertEquals("open", invokeMapGitHubState(pr));
    }

    @Test
    @DisplayName("PR state=closed + merged_at 有值 → merged")
    void stateClosedWithMergedAt_shouldReturnMerged() throws Exception {
        Map<String, Object> pr = new HashMap<>();
        pr.put("state", "closed");
        pr.put("merged_at", "2026-07-26T10:00:00Z");
        assertEquals("merged", invokeMapGitHubState(pr));
    }

    @Test
    @DisplayName("PR state=closed + merged_at 为空 → closed")
    void stateClosedWithoutMergedAt_shouldReturnClosed() throws Exception {
        Map<String, Object> pr = new HashMap<>();
        pr.put("state", "closed");
        pr.put("merged_at", null);
        assertEquals("closed", invokeMapGitHubState(pr));
    }

    @Test
    @DisplayName("PR state=closed + merged_at 为空白字符串 → closed")
    void stateClosedWithBlankMergedAt_shouldReturnClosed() throws Exception {
        Map<String, Object> pr = new HashMap<>();
        pr.put("state", "closed");
        pr.put("merged_at", "");
        assertEquals("closed", invokeMapGitHubState(pr));
    }

    // ==========  helper: 反射调用 private 方法 ==========

    private String invokeMapCiStatus(String status, String conclusion) throws Exception {
        Method method = GitHubClient.class.getDeclaredMethod("mapCiStatus", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(client, status, conclusion);
    }

    private String invokeMapGitHubState(Map<String, Object> pr) throws Exception {
        Method method = GitHubClient.class.getDeclaredMethod("mapGitHubState", Map.class);
        method.setAccessible(true);
        return (String) method.invoke(client, pr);
    }
}
