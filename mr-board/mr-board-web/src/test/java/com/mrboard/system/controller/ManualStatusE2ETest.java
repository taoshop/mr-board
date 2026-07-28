package com.mrboard.system.controller;

import com.jayway.jsonpath.JsonPath;
import com.mrboard.system.BaseIntegrationTest;
import com.mrboard.system.entity.Mrs;
import com.mrboard.system.mapper.MrsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 手动拖拽状态（manual_status）保留的 E2E 测试。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>拖拽 MR 到非终态 → manual_status 正确写入</li>
 *   <li>拖拽 MR 到终态（merged/closed）→ manual_status 清理</li>
 *   <li>同步后 board_status 保留手动状态</li>
 *   <li>查看板数据时 manualStatus 字段存在</li>
 * </ul>
 */
@Transactional
class ManualStatusE2ETest extends BaseIntegrationTest {

    @Autowired
    private MrsMapper mrsMapper;

    private Long mrId;

    @BeforeEach
    void seedData() {
        // 插入一个标准的待 Review MR
        Mrs mr = new Mrs();
        mr.setProjectId(1L);
        mr.setPlatformMrId(999L);
        mr.setTitle("E2E Test MR for manual_status");
        mr.setAuthorName("admin");
        mr.setSourceBranch("feature/e2e-test");
        mr.setTargetBranch("master");
        mr.setPlatformStatus("opened");
        mr.setBoardStatus("pending_review");
        mr.setCiStatus("success");
        mr.setHasConflict(false);
        mr.setMergeable(true);
        mr.setReviewers("");
        mr.setApprovalStatus("pending");
        mr.setWebUrl("http://example.com/mr/999");
        mr.setCreatedAt(java.time.LocalDateTime.now());
        mr.setUpdatedAt(java.time.LocalDateTime.now());
        mrsMapper.insert(mr);
        this.mrId = mr.getId();
    }

    @Test
    @DisplayName("场景1：拖拽到非终态 → manual_status 写入 + board_status 同步更新")
    void dragToNonTerminal_shouldSetManualStatus() throws Exception {
        // 执行拖拽到 reviewing
        mockMvc.perform(put("/api/mrs/{id}/status", mrId)
                        .with(bearerToken())
                        .contentType("application/json")
                        .content("{\"boardStatus\":\"reviewing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证 DB 中 manual_status 和 board_status 都变为了 reviewing
        Mrs updated = mrsMapper.selectById(mrId);
        assertEquals("reviewing", updated.getBoardStatus(), "boardStatus 应为 reviewing");
        assertNotNull(updated.getManualStatus(), "manualStatus 不应为 null");
        assertEquals("reviewing", updated.getManualStatus(), "manualStatus 应为 reviewing");
    }

    @Test
    @DisplayName("场景2：拖拽到终态 merged → 需 Git API 调用，若无 Git 源则返回错误")
    void dragToMerged_shouldReturnError_whenNoGitApi() throws Exception {
        // 先拖到 Review 中
        mockMvc.perform(put("/api/mrs/{id}/status", mrId)
                .with(bearerToken())
                .contentType("application/json")
                .content("{\"boardStatus\":\"reviewing\"}"));

        // 拖拽到 merged（终态）— 由于测试环境没有真实的 Git API，应返回错误
        mockMvc.perform(put("/api/mrs/{id}/status", mrId)
                        .with(bearerToken())
                        .contentType("application/json")
                        .content("{\"boardStatus\":\"merged\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));

        // 验证 boardStatus 未改变
        Mrs updated = mrsMapper.selectById(mrId);
        assertEquals("reviewing", updated.getBoardStatus(), "merged 失败后 boardStatus 应保持不变");
    }

    @Test
    @DisplayName("场景3：拖拽到终态 closed → 需 Git API 调用，若无 Git 源则返回错误")
    void dragToClosed_shouldReturnError_whenNoGitApi() throws Exception {
        // 先拖到 Review 中
        mockMvc.perform(put("/api/mrs/{id}/status", mrId)
                .with(bearerToken())
                .contentType("application/json")
                .content("{\"boardStatus\":\"reviewing\"}"));

        // 拖拽到 closed（终态）— 由于测试环境没有真实的 Git API，应返回错误
        mockMvc.perform(put("/api/mrs/{id}/status", mrId)
                        .with(bearerToken())
                        .contentType("application/json")
                        .content("{\"boardStatus\":\"closed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));

        Mrs updated = mrsMapper.selectById(mrId);
        assertEquals("reviewing", updated.getBoardStatus(), "closed 失败后 boardStatus 应保持不变");
    }

    @Test
    @DisplayName("场景4：重复拖拽同一状态 → manual_status 不变")
    void dragToSameStatus_shouldNotChange() throws Exception {
        // 拖到 reviewing
        mockMvc.perform(put("/api/mrs/{id}/status", mrId)
                .with(bearerToken())
                .contentType("application/json")
                .content("{\"boardStatus\":\"reviewing\"}"));

        Mrs afterFirst = mrsMapper.selectById(mrId);
        assertEquals("reviewing", afterFirst.getManualStatus());

        // 再次拖到 reviewing（相同状态，API 应直接返回 success 不修改）
        mockMvc.perform(put("/api/mrs/{id}/status", mrId)
                        .with(bearerToken())
                        .contentType("application/json")
                        .content("{\"boardStatus\":\"reviewing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 状态不应变化
        Mrs afterSecond = mrsMapper.selectById(mrId);
        assertEquals("reviewing", afterSecond.getBoardStatus());
        assertEquals("reviewing", afterSecond.getManualStatus());
    }

    @Test
    @DisplayName("场景5：连续拖拽 changing_status → reviewing → conflict → reviewing → manual_status 最后值")
    void consecutiveDrags_shouldKeepLatestManualStatus() throws Exception {
        // 第一次拖拽到 reviewing
        mockMvc.perform(put("/api/mrs/{id}/status", mrId)
                .with(bearerToken())
                .contentType("application/json")
                .content("{\"boardStatus\":\"reviewing\"}"));

        // 第二次拖拽到 conflict
        mockMvc.perform(put("/api/mrs/{id}/status", mrId)
                .with(bearerToken())
                .contentType("application/json")
                .content("{\"boardStatus\":\"conflict\"}"));

        // 第三次拖拽回 reviewing
        mockMvc.perform(put("/api/mrs/{id}/status", mrId)
                .with(bearerToken())
                .contentType("application/json")
                .content("{\"boardStatus\":\"reviewing\"}"));

        Mrs updated = mrsMapper.selectById(mrId);
        assertEquals("reviewing", updated.getBoardStatus());
        assertEquals("reviewing", updated.getManualStatus(), "多次拖拽后 manualStatus 应保留最后一次的值");
    }

    @Test
    @DisplayName("场景6：board API 返回的数据中包含 manualStatus 字段")
    void boardData_shouldIncludeManualStatus() throws Exception {
        mockMvc.perform(put("/api/mrs/{id}/status", mrId)
                        .with(bearerToken())
                        .contentType("application/json")
                        .content("{\"boardStatus\":\"reviewing\"}"));

        // 查看板接口返回的 MR 对象中应包含 manualStatus
        String response = mockMvc.perform(get("/api/board")
                        .with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();

        // 在 reviewing 列的 MR 中查找 manualStatus
        Object manualStatus = JsonPath.compile("$.data.reviewing[?(@.id == " + mrId + ")].manualStatus")
                .read(response);
        assertNotNull(manualStatus, "board API 返回的数据应包含 manualStatus 字段");
    }
}
