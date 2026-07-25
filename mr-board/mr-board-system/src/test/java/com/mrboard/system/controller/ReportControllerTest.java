package com.mrboard.system.controller;

import com.mrboard.system.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportControllerTest extends BaseIntegrationTest {

    @Test
    void getOverview_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/reports/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getTrend_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/reports/trend")
                        .param("start", "2024-01-01")
                        .param("end", "2024-01-31"))
                .andExpect(status().isUnauthorized());
    }
}
