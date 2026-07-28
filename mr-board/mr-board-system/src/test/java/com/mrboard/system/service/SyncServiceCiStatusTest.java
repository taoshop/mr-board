package com.mrboard.system.service;

import com.mrboard.system.sync.dto.CiDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link SyncService#calculateOverallCiStatus} 的单元测试（通过反射调用 private 方法）。
 *
 * <p>覆盖场景：空列表、所有 CI 通过、有运行中、有失败、有 cancelled/skipped、混合状态。</p>
 */
class SyncServiceCiStatusTest {

    private final SyncService syncService = new SyncService(null, null, null, null, null, null, null, null, null, null);

    /**
     * 通过反射调用 SyncService.calculateOverallCiStatus(List<CiDTO>)
     */
    private String calculateOverallCiStatus(List<CiDTO> ciList) throws Exception {
        Method method = SyncService.class.getDeclaredMethod("calculateOverallCiStatus", List.class);
        method.setAccessible(true);
        return (String) method.invoke(syncService, ciList);
    }

    // ---------- helper ----------

    private static CiDTO ci(String status) {
        CiDTO ci = new CiDTO();
        ci.setName("test-job");
        ci.setStage("test");
        ci.setPlatformJobId("job-" + status);
        ci.setStatus(status);
        return ci;
    }

    // ========== 测试用例 ==========

    @Test
    @DisplayName("null 列表 → unknown")
    void nullList_shouldReturnUnknown() throws Exception {
        assertEquals("unknown", calculateOverallCiStatus(null));
    }

    @Test
    @DisplayName("空列表 → unknown")
    void emptyList_shouldReturnUnknown() throws Exception {
        assertEquals("unknown", calculateOverallCiStatus(Collections.emptyList()));
    }

    @Test
    @DisplayName("全部成功 → success")
    void allSuccess_shouldReturnSuccess() throws Exception {
        List<CiDTO> list = Arrays.asList(ci("success"), ci("success"));
        assertEquals("success", calculateOverallCiStatus(list));
    }

    @Test
    @DisplayName("有运行中的 → running")
    void hasRunning_shouldReturnRunning() throws Exception {
        List<CiDTO> list = Arrays.asList(ci("running"), ci("success"));
        assertEquals("running", calculateOverallCiStatus(list));
    }

    @Test
    @DisplayName("有 pending → running")
    void hasPending_shouldReturnRunning() throws Exception {
        List<CiDTO> list = Arrays.asList(ci("pending"), ci("success"));
        assertEquals("running", calculateOverallCiStatus(list));
    }

    @Test
    @DisplayName("运行中优先级高于失败 → running")
    void runningOverFailed_shouldReturnRunning() throws Exception {
        List<CiDTO> list = Arrays.asList(ci("running"), ci("failed"));
        assertEquals("running", calculateOverallCiStatus(list));
    }

    @Test
    @DisplayName("有失败的（无运行中）→ failed")
    void hasFailed_shouldReturnFailed() throws Exception {
        List<CiDTO> list = Arrays.asList(ci("failed"), ci("success"));
        assertEquals("failed", calculateOverallCiStatus(list));
    }

    @Test
    @DisplayName("全部 cancelled → unknown")
    void allCancelled_shouldReturnUnknown() throws Exception {
        List<CiDTO> list = Arrays.asList(ci("cancelled"), ci("cancelled"));
        assertEquals("unknown", calculateOverallCiStatus(list));
    }

    @Test
    @DisplayName("全部 skipped → unknown")
    void allSkipped_shouldReturnUnknown() throws Exception {
        List<CiDTO> list = Arrays.asList(ci("skipped"), ci("skipped"));
        assertEquals("unknown", calculateOverallCiStatus(list));
    }

    @Test
    @DisplayName("全部 canceled（美式拼写）→ unknown")
    void allCanceled_shouldReturnUnknown() throws Exception {
        List<CiDTO> list = Arrays.asList(ci("canceled"), ci("canceled"));
        assertEquals("unknown", calculateOverallCiStatus(list));
    }

    @Test
    @DisplayName("cancelled + success → success（有成功的不算纯取消）")
    void cancelledWithSuccess_shouldReturnSuccess() throws Exception {
        // 因为有成功的 job，所以整体是 success
        List<CiDTO> list = Arrays.asList(ci("cancelled"), ci("success"));
        assertEquals("success", calculateOverallCiStatus(list));
    }

    @Test
    @DisplayName("cancelled + failed → failed（失败优先级高于取消）")
    void cancelledWithFailed_shouldReturnFailed() throws Exception {
        List<CiDTO> list = Arrays.asList(ci("cancelled"), ci("failed"));
        assertEquals("failed", calculateOverallCiStatus(list));
    }

    @Test
    @DisplayName("cancelled + running → running（运行中优先级最高）")
    void cancelledWithRunning_shouldReturnRunning() throws Exception {
        List<CiDTO> list = Arrays.asList(ci("cancelled"), ci("running"));
        assertEquals("running", calculateOverallCiStatus(list));
    }

    @Test
    @DisplayName("mixed cancelled + skipped → unknown")
    void mixedCancelStatuses_shouldReturnUnknown() throws Exception {
        List<CiDTO> list = Arrays.asList(ci("cancelled"), ci("skipped"));
        assertEquals("unknown", calculateOverallCiStatus(list));
    }
}
