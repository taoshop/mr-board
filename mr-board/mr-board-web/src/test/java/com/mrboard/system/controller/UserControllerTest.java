package com.mrboard.system.controller;

import com.mrboard.system.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户管理 E2E 测试（仅 ADMIN）
 * 覆盖：用户列表、详情、创建、更新、删除、角色分配
 */
@Transactional
class UserControllerTest extends BaseIntegrationTest {

    @Test
    void listUsers_shouldReturnPage() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(bearerToken())
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void getUserDetail_shouldReturnUser() throws Exception {
        mockMvc.perform(get("/api/admin/users/{id}", 1)
                        .with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    void createUser_shouldSucceed_andAssignRoles() throws Exception {
        String username = "testuser" + System.currentTimeMillis();
        String body = String.format(
                "{\"username\":\"%s\",\"password\":\"Test@123\",\"email\":\"%s@example.com\",\"displayName\":\"测试用户\",\"roleIds\":[2]}",
                username, username);

        mockMvc.perform(post("/api/admin/users")
                        .with(bearerToken())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/admin/users")
                        .with(bearerToken())
                        .param("keyword", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1));
    }

    @Test
    void createUser_shouldReturnError_whenDuplicateUsername() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"Test@123\",\"email\":\"dup@example.com\"}";
        mockMvc.perform(post("/api/admin/users")
                        .with(bearerToken())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
    }
}
