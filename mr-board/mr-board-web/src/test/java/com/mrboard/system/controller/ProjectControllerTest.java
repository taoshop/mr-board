package com.mrboard.system.controller;

import com.mrboard.system.BaseIntegrationTest;
import com.mrboard.system.entity.Project;
import com.mrboard.system.mapper.ProjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 项目管理 E2E 测试
 * 覆盖：项目列表查询、创建、更新、删除
 */
@Transactional
class ProjectControllerTest extends BaseIntegrationTest {

    @Autowired
    private ProjectMapper projectMapper;

    @Test
    void listProjects_shouldReturnProjects() throws Exception {
        mockMvc.perform(get("/api/projects").with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void crudProject_shouldSucceed() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .with(bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gitSourceId\":1,\"platformProjectId\":\"new-proj\",\"name\":\"new-proj\",\"projectPath\":\"group/new-proj\",\"isActive\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/projects").with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[?(@.name=='new-proj')]").exists());

        Long projectId = projectMapper.selectList(null).stream()
                .filter(p -> "new-proj".equals(p.getName()))
                .findFirst()
                .map(Project::getId)
                .orElseThrow();

        mockMvc.perform(put("/api/projects/{id}", projectId)
                        .with(bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"updated-proj\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/projects").with(bearerToken()))
                .andExpect(jsonPath("$.data[?(@.name=='updated-proj')]").exists());

        mockMvc.perform(delete("/api/projects/{id}", projectId)
                        .with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/projects").with(bearerToken()))
                .andExpect(jsonPath("$.data[?(@.name=='updated-proj')]").doesNotExist());
    }
}
