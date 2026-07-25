package com.mrboard.system.controller;

import com.mrboard.system.BaseIntegrationTest;
import com.mrboard.system.entity.Project;
import com.mrboard.system.mapper.GitSourceMapper;
import com.mrboard.system.mapper.ProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Webhook E2E 测试
 * 覆盖：GitLab Webhook、GitHub Webhook 接收与处理
 */
@Transactional
class WebhookControllerTest extends BaseIntegrationTest {

    @Autowired
    private GitSourceMapper gitSourceMapper;

    @Autowired
    private ProjectMapper projectMapper;

    private Long gitSourceId;

    @BeforeEach
    void seedGitSourceAndProject() {
        com.mrboard.system.entity.GitSource source = new com.mrboard.system.entity.GitSource();
        source.setName("webhook-test");
        source.setPlatformType(1);
        source.setApiBaseUrl("https://gitlab.example.com/api/v4");
        source.setAccessToken("encrypted-token");
        source.setWebhookSecret("my-secret");
        source.setIsActive(1);
        gitSourceMapper.insert(source);
        this.gitSourceId = source.getId();

        Project project = new Project();
        project.setGitSourceId(gitSourceId);
        project.setPlatformProjectId("group/demo");
        project.setProjectPath("group/demo");
        project.setName("demo");
        project.setIsActive(1);
        projectMapper.insert(project);
    }

    @Test
    void gitlabWebhook_shouldReturn200_whenTokenValid() throws Exception {
        mockMvc.perform(post("/api/webhook/gitlab")
                        .header("X-Gitlab-Token", "my-secret")
                        .header("X-Gitlab-Event", "Merge Request Hook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "project", Map.of("path_with_namespace", "group/demo"),
                                "object_attributes", Map.of("iid", 1)
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void gitlabWebhook_shouldReturn401_whenTokenInvalid() throws Exception {
        mockMvc.perform(post("/api/webhook/gitlab")
                        .header("X-Gitlab-Token", "bad-secret")
                        .header("X-Gitlab-Event", "Merge Request Hook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "project", Map.of("path_with_namespace", "group/demo")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void githubWebhook_shouldReturn200_whenSignatureValid() throws Exception {
        String payload = "{\"repository\":{\"full_name\":\"group/demo\"},\"pull_request\":{\"number\":1}}";
        String signature = computeGithubSignature(payload, "my-secret");

        mockMvc.perform(post("/api/webhook/github")
                        .header("X-Hub-Signature-256", signature)
                        .header("X-GitHub-Event", "pull_request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void githubWebhook_shouldReturn401_whenSignatureInvalid() throws Exception {
        mockMvc.perform(post("/api/webhook/github")
                        .header("X-Hub-Signature-256", "sha256=bad")
                        .header("X-GitHub-Event", "pull_request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repository\":{\"full_name\":\"group/demo\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    private String computeGithubSignature(String payload, String secret) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        javax.crypto.spec.SecretKeySpec key = new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(key);
        byte[] hash = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return "sha256=" + sb;
    }
}
