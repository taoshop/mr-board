package com.mrboard.system.controller;

import com.mrboard.system.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证授权 E2E 测试
 * 覆盖：登录、Token刷新、获取当前用户、未认证访问拦截
 */
class AuthControllerTest extends BaseIntegrationTest {

    @Test
    void login_shouldReturnTokens_whenCredentialsValid() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.user.username").value("admin"))
                .andExpect(jsonPath("$.data.user.roles[0]").value("admin"));
    }

    @Test
    void login_shouldReturnError_whenCredentialsInvalid() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void refresh_shouldReturnNewAccessToken_whenRefreshTokenValid() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn().getResponse().getContentAsString();

        String refreshToken = objectMapper.readTree(response).path("data").path("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").exists());
    }

    @Test
    void me_shouldReturnCurrentUser_whenAuthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    void protectedEndpoint_shouldReturn403_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }
}
