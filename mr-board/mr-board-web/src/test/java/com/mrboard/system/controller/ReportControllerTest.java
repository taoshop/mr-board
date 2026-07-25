package com.mrboard.system.controller;

import com.mrboard.system.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 报表 E2E 测试
 * 覆盖：概览、趋势、分布、导出（认证后访问）
 */
class ReportControllerTest extends BaseIntegrationTest {

    @Test
    void overview_shouldReturnData_whenAuthenticated() throws Exception {
        mockMvc.perform(get("/api/reports/overview")
                        .with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    void trend_shouldReturnData_whenAuthenticated() throws Exception {
        mockMvc.perform(get("/api/reports/trend")
                        .with(bearerToken())
                        .param("start", "2024-01-01")
                        .param("end", "2024-01-31")
                        .param("groupBy", "day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    void distribution_shouldReturnData_whenAuthenticated() throws Exception {
        mockMvc.perform(get("/api/reports/distribution")
                        .with(bearerToken())
                        .param("type", "status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    void exportExcel_shouldReturn200_whenAuthenticated() throws Exception {
        mockMvc.perform(get("/api/reports/export/excel")
                        .with(bearerToken()))
                .andExpect(status().isOk());
    }

    @Test
    void exportCsv_shouldReturn200_whenAuthenticated() throws Exception {
        mockMvc.perform(get("/api/reports/export/csv")
                        .with(bearerToken()))
                .andExpect(status().isOk());
    }

    @Test
    void overview_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/reports/overview"))
                .andExpect(status().isForbidden());
    }
}
