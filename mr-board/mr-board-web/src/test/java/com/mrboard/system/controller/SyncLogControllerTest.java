package com.mrboard.system.controller;

import com.mrboard.system.BaseIntegrationTest;
import com.mrboard.system.entity.SyncLog;
import com.mrboard.system.mapper.SyncLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 同步日志 E2E 测试
 * 覆盖：同步日志列表查询
 */
@Transactional
class SyncLogControllerTest extends BaseIntegrationTest {

    @Autowired
    private SyncLogMapper syncLogMapper;

    @BeforeEach
    void seedData() {
        SyncLog log = new SyncLog();
        log.setProjectId(1L);
        log.setGitSourceId(1L);
        log.setSyncType("incremental");
        log.setTriggerType("manual");
        log.setStatus("success");
        log.setMrCount(5);
        log.setCiCount(3);
        log.setCreatedAt(LocalDateTime.now());
        log.setFinishedAt(LocalDateTime.now());
        syncLogMapper.insert(log);
    }

    @Test
    void listSyncLogs_shouldReturnPage() throws Exception {
        mockMvc.perform(get("/api/admin/sync/logs")
                        .with(bearerToken())
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.records[?(@.status=='success')]").exists());
    }

    @Test
    void listSyncLogs_shouldFilterByStatus() throws Exception {
        mockMvc.perform(get("/api/admin/sync/logs")
                        .with(bearerToken())
                        .param("page", "1")
                        .param("size", "20")
                        .param("status", "success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(greaterThanOrEqualTo(1)));
    }
}
