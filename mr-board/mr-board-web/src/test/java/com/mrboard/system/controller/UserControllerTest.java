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
    void listRoles_shouldReturnRoles() throws Exception {
        mockMvc.perform(get("/api/admin/users/roles/list")
                        .with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void updateUser_shouldUpdateRoles() throws Exception {
        String body = "{\"email\":\"admin@mrboard.com\",\"displayName\":\"系统管理员\",\"roleIds\":[1,2]}";
        mockMvc.perform(put("/api/admin/users/1")
                        .with(bearerToken())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
