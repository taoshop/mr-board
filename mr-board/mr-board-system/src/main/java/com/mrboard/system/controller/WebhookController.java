package com.mrboard.system.controller;

import com.mrboard.common.result.Result;
import com.mrboard.system.entity.GitSource;
import com.mrboard.system.entity.Project;
import com.mrboard.system.entity.WebhookEventLog;
import com.mrboard.system.mapper.GitSourceMapper;
import com.mrboard.system.mapper.ProjectMapper;
import com.mrboard.system.mapper.WebhookEventLogMapper;
import com.mrboard.system.service.SyncService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Tag(name = "Webhook", description = "接收Git平台Webhook事件，触发即时同步")
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final ProjectMapper projectMapper;
    private final GitSourceMapper gitSourceMapper;
    private final WebhookEventLogMapper webhookLogMapper;
    private final SyncService syncService;

    @PostMapping("/gitlab")
    public Result<Void> gitlab(@RequestBody Map<String, Object> payload,
                             @RequestHeader(value = "X-Gitlab-Token", required = false) String token,
                             @RequestHeader(value = "X-Gitlab-Event", required = false) String event,
                             HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        log.info("Received GitLab webhook, event={}, ip={}", event, ip);

        String projectPath = extractGitLabProjectPath(payload);
        boolean processed = false;
        String errorMsg = null;

        try {
            if (projectPath == null) {
                log.warn("GitLab webhook missing project path");
                return Result.success();
            }

            Project project = findProjectByPath(projectPath);
            if (project == null) {
                log.warn("GitLab webhook project not found: {}", projectPath);
                return Result.success();
            }

            GitSource source = gitSourceMapper.selectById(project.getGitSourceId());
            if (source != null && source.getWebhookSecret() != null && !source.getWebhookSecret().isEmpty()) {
                if (!source.getWebhookSecret().equals(token)) {
                    log.warn("GitLab webhook token mismatch for project: {}", projectPath);
                    return Result.error(401, "Invalid webhook token");
                }
            }

            if (isRelevantGitLabEvent(event)) {
                syncService.syncProject(project.getId(), "webhook");
                processed = true;
                log.info("GitLab webhook triggered sync for project {}", project.getId());
            } else {
                log.debug("GitLab webhook event ignored: {}", event);
            }
            return Result.success();
        } catch (Exception e) {
            errorMsg = e.getMessage();
            log.error("GitLab webhook processing failed", e);
            return Result.error(500, "Webhook processing failed");
        } finally {
            saveLog("gitlab", event, projectPath, payload, token, ip, processed, errorMsg);
        }
    }

    @PostMapping("/github")
    public Result<Void> github(@RequestBody Map<String, Object> payload,
                            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
                            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
                            HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        log.info("Received GitHub webhook, event={}, ip={}", event, ip);

        String repoFullName = extractGitHubRepoName(payload);
        boolean processed = false;
        String errorMsg = null;

        try {
            if (repoFullName == null) {
                log.warn("GitHub webhook missing repository name");
                return Result.success();
            }

            Project project = findProjectByPath(repoFullName);
            if (project == null) {
                log.warn("GitHub webhook project not found: {}", repoFullName);
                return Result.success();
            }

            GitSource source = gitSourceMapper.selectById(project.getGitSourceId());
            if (source != null && source.getWebhookSecret() != null && !source.getWebhookSecret().isEmpty()) {
                String body = toJsonString(payload);
                if (!verifyGithubSignature(body, source.getWebhookSecret(), signature)) {
                    log.warn("GitHub webhook signature mismatch for project: {}", repoFullName);
                    return Result.error(401, "Invalid webhook signature");
                }
            }

            if (isRelevantGitHubEvent(event)) {
                syncService.syncProject(project.getId(), "webhook");
                processed = true;
                log.info("GitHub webhook triggered sync for project {}", project.getId());
            } else {
                log.debug("GitHub webhook event ignored: {}", event);
            }
            return Result.success();
        } catch (Exception e) {
            errorMsg = e.getMessage();
            log.error("GitHub webhook processing failed", e);
            return Result.error(500, "Webhook processing failed");
        } finally {
            saveLog("github", event, repoFullName, payload, signature, ip, processed, errorMsg);
        }
    }

    private String extractGitLabProjectPath(Map<String, Object> payload) {
        Object projectObj = payload.get("project");
        if (projectObj instanceof Map<?, ?> projectMap) {
            return (String) projectMap.get("path_with_namespace");
        }
        return null;
    }

    private String extractGitHubRepoName(Map<String, Object> payload) {
        Object repoObj = payload.get("repository");
        if (repoObj instanceof Map<?, ?> repoMap) {
            return (String) repoMap.get("full_name");
        }
        return null;
    }

    private Project findProjectByPath(String path) {
        // 精确匹配或模糊匹配
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Project>();
        wrapper.eq(Project::getProjectPath, path);
        Project project = projectMapper.selectOne(wrapper);
        if (project != null) {
            return project;
        }
        // 尝试去掉 .git 后缀匹配
        if (path.endsWith(".git")) {
            wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Project>();
            wrapper.eq(Project::getProjectPath, path.substring(0, path.length() - 4));
            return projectMapper.selectOne(wrapper);
        }
        return null;
    }

    private boolean isRelevantGitLabEvent(String event) {
        return "Merge Request Hook".equalsIgnoreCase(event)
                || "Push Hook".equalsIgnoreCase(event)
                || "Pipeline Hook".equalsIgnoreCase(event);
    }

    private boolean isRelevantGitHubEvent(String event) {
        return "pull_request".equalsIgnoreCase(event)
                || "push".equalsIgnoreCase(event)
                || "check_run".equalsIgnoreCase(event);
    }

    private boolean verifyGithubSignature(String payload, String secret, String signature) {
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }
        String expected = "sha256=" + hmacSha256(payload, secret);
        return expected.equals(signature);
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            log.error("HMAC-SHA256 calculation failed", e);
            return "";
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String toJsonString(Map<String, Object> payload) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialize payload", e);
            return "";
        }
    }

    private void saveLog(String platformType, String eventType, String projectPath,
                         Map<String, Object> payload, String signature, String ip,
                         boolean processed, String errorMsg) {
        try {
            WebhookEventLog logRecord = new WebhookEventLog();
            logRecord.setPlatformType(platformType);
            logRecord.setEventType(eventType);
            logRecord.setProjectPath(projectPath);
            String payloadJson = toJsonString(payload);
            if (payloadJson.length() > 4096) {
                payloadJson = payloadJson.substring(0, 4096);
            }
            logRecord.setPayload(payloadJson);
            logRecord.setSignature(signature);
            logRecord.setIpAddress(ip);
            logRecord.setProcessed(processed ? 1 : 0);
            logRecord.setErrorMsg(errorMsg);
            webhookLogMapper.insert(logRecord);
        } catch (Exception e) {
            log.error("Failed to save webhook log", e);
        }
    }
}
