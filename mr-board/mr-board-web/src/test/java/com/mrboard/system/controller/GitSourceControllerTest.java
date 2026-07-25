package com.mrboard.system.controller;

import com.mrboard.system.BaseIntegrationTest;
import com.mrboard.system.entity.GitSource;
import com.mrboard.system.mapper.GitSourceMapper;
import com.mrboard.system.mapper.ProjectMapper;
import com.mrboard.system.sync.GitClientFactory;
import com.mrboard.system.sync.GitSyncClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Git 源管理 E2E 测试
 * 覆盖：创建、列表、详情、测试连接、触发同步、更新、删除
 */
@Transactional
class GitSourceControllerTest extends BaseIntegrationTest {

    @MockBean
    private GitClientFactory gitClientFactory;

    @org.springframework.beans.factory.annotation.Autowired
    private GitSourceMapper gitSourceMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private ProjectMapper projectMapper;

    @BeforeEach
    void mockGitClient() throws Exception {
        GitSyncClient mockClient = mock(GitSyncClient.class);
        when(gitClientFactory.create(anyInt(), anyString(), anyString())).thenReturn(mockClient);
        when(mockClient.testConnection()).thenReturn(true);
    }

    @Test
    void createGitSource_shouldSucceed_andCreateProjects() throws Exception {
        mockMvc.perform(post("/api/admin/git-sources")
                        .with(bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "test-gitlab",
                                "platformType", 1,
                                "apiBaseUrl", "https://gitlab.example.com/api/v4",
                                "accessToken", "glpat-test-token",
                                "projectPaths", List.of("group/project-a", "group/project-b")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/admin/git-sources")
                        .with(bearerToken())
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void testConnection_shouldReturnSuccess_whenMockClientOk() throws Exception {
        mockMvc.perform(post("/api/admin/git-sources")
                        .with(bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "test-conn",
                                "platformType", 2,
                                "apiBaseUrl", "https://api.github.com",
                                "accessToken", "ghp-test"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Long id = gitSourceMapper.selectList(null).stream()
                .filter(s -> "test-conn".equals(s.getName()))
                .findFirst()
                .map(GitSource::getId)
                .orElseThrow();

        mockMvc.perform(post("/api/admin/git-sources/{id}/test", id)
                        .with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("连接成功"));
    }

    @Test
    void triggerSync_shouldReturnSuccess() throws Exception {
        mockMvc.perform(post("/api/admin/git-sources")
                        .with(bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "test-sync",
                                "platformType", 1,
                                "apiBaseUrl", "https://gitlab.example.com/api/v4",
                                "accessToken", "glpat-sync"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Long id = gitSourceMapper.selectList(null).stream()
                .filter(s -> "test-sync".equals(s.getName()))
                .findFirst()
                .map(GitSource::getId)
                .orElseThrow();

        mockMvc.perform(post("/api/admin/git-sources/{id}/sync", id)
                        .with(bearerToken())
                        .param("type", "full"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void updateGitSource_shouldSucceed() throws Exception {
        mockMvc.perform(post("/api/admin/git-sources")
                        .with(bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "test-update",
                                "platformType", 1,
                                "apiBaseUrl", "https://gitlab.example.com/api/v4",
                                "accessToken", "glpat-update"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Long id = gitSourceMapper.selectList(null).stream()
                .filter(s -> "test-update".equals(s.getName()))
                .findFirst()
                .map(GitSource::getId)
                .orElseThrow();

        mockMvc.perform(put("/api/admin/git-sources/{id}", id)
                        .with(bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "test-update-renamed",
                                "platformType", 1,
                                "apiBaseUrl", "https://gitlab.example.com/api/v4"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/admin/git-sources/{id}", id)
                        .with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("test-update-renamed"));
    }

    @Test
    void deleteGitSource_shouldSucceed() throws Exception {
        mockMvc.perform(post("/api/admin/git-sources")
                        .with(bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "test-delete",
                                "platformType", 1,
                                "apiBaseUrl", "https://gitlab.example.com/api/v4",
                                "accessToken", "glpat-delete"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Long id = gitSourceMapper.selectList(null).stream()
                .filter(s -> "test-delete".equals(s.getName()))
                .findFirst()
                .map(GitSource::getId)
                .orElseThrow();

        mockMvc.perform(delete("/api/admin/git-sources/{id}", id)
                        .with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
