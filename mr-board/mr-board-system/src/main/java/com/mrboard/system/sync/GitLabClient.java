package com.mrboard.system.sync;

import com.mrboard.system.exception.GitPlatformException;
import com.mrboard.system.sync.dto.CiDTO;
import com.mrboard.system.sync.dto.ChangeDTO;
import com.mrboard.system.sync.dto.CommentDTO;
import com.mrboard.system.sync.dto.MrDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpHost;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class GitLabClient implements GitSyncClient {

    private final RestTemplate restTemplate;
    private final String apiBaseUrl;
    private final String accessToken;

    public GitLabClient(String apiBaseUrl, String accessToken, String proxyHost, Integer proxyPort) {
        this.apiBaseUrl = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
        this.accessToken = accessToken;

        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        if (proxyHost != null && proxyPort != null) {
            httpClientBuilder.setProxy(new HttpHost("http", proxyHost, proxyPort));
            log.info("GitLabClient using proxy {}:{}", proxyHost, proxyPort);
        }
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(
                httpClientBuilder.build()
        );
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public boolean testConnection() {
        try {
            String url = apiBaseUrl + "/projects?per_page=1";
            restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, createEntity(), Map.class);
            return true;
        } catch (Exception e) {
            log.warn("GitLab connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<MrDTO> fetchMRs(String projectPath, String state, String updatedAfter) {
        List<MrDTO> result = new ArrayList<>();
        String encodedPath = projectPath.replace("/", "%2F");
        int page = 1;
        while (true) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(
                    apiBaseUrl + "/projects/" + encodedPath + "/merge_requests"
            )
                    .queryParam("per_page", 100)
                    .queryParam("page", page);
            if (state != null) {
                builder.queryParam("state", state);
            }
            if (updatedAfter != null) {
                builder.queryParam("updated_after", updatedAfter);
            }

            try {
                org.springframework.http.ResponseEntity<List> response = restTemplate.exchange(
                        builder.toUriString(),
                        org.springframework.http.HttpMethod.GET,
                        createEntity(),
                        List.class
                );
                List<Map<String, Object>> mrs = response.getBody();
                if (mrs == null || mrs.isEmpty()) break;
                for (Map<String, Object> mr : mrs) {
                    result.add(convertToDto(mr));
                }
                if (mrs.size() < 100) break;
                page++;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {}
            } catch (Exception e) {
                log.error("Failed to fetch MRs from GitLab: {}", e.getMessage());
                break;
            }
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<CiDTO> fetchCI(String projectPath, Long mrIid) {
        List<CiDTO> result = new ArrayList<>();
        String encodedPath = projectPath.replace("/", "%2F");
        String url = apiBaseUrl + "/projects/" + encodedPath + "/merge_requests/" + mrIid + "/pipelines";
        try {
            org.springframework.http.ResponseEntity<List> response = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET, createEntity(), List.class
            );
            List<Map<String, Object>> pipelines = response.getBody();
            if (pipelines == null) return result;
            for (Map<String, Object> pipeline : pipelines) {
                String pipelineId = String.valueOf(pipeline.get("id"));
                String pipelineUrl = apiBaseUrl + "/projects/" + encodedPath + "/pipelines/" + pipelineId + "/jobs";
                org.springframework.http.ResponseEntity<List> jobsResponse = restTemplate.exchange(
                        pipelineUrl, org.springframework.http.HttpMethod.GET, createEntity(), List.class
                );
                List<Map<String, Object>> jobs = jobsResponse.getBody();
                if (jobs != null) {
                    for (Map<String, Object> job : jobs) {
                        CiDTO dto = new CiDTO();
                        dto.setPlatformJobId(String.valueOf(job.get("id")));
                        dto.setName((String) job.get("name"));
                        dto.setStage((String) job.get("stage"));
                        dto.setStatus(mapCiStatus((String) job.get("status")));
                        dto.setLogUrl((String) job.get("web_url"));
                        dto.setStartedAt(parseDateTime((String) job.get("started_at")));
                        result.add(dto);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch CI from GitLab: {}", e.getMessage());
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ChangeDTO> fetchChanges(String projectPath, Long mrIid) {
        List<ChangeDTO> result = new ArrayList<>();
        String encodedPath = projectPath.replace("/", "%2F");
        String url = apiBaseUrl + "/projects/" + encodedPath + "/merge_requests/" + mrIid + "/changes";
        try {
            org.springframework.http.ResponseEntity<Map> response = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET, createEntity(), Map.class
            );
            Map<String, Object> body = response.getBody();
            if (body == null) return result;
            List<Map<String, Object>> changes = (List<Map<String, Object>>) body.get("changes");
            if (changes == null) return result;
            for (Map<String, Object> c : changes) {
                ChangeDTO dto = new ChangeDTO();
                dto.setOldPath((String) c.get("old_path"));
                dto.setNewPath((String) c.get("new_path"));
                dto.setDiff((String) c.get("diff"));
                Object newFile = c.get("new_file");
                dto.setNewFile(newFile != null && Boolean.parseBoolean(String.valueOf(newFile)));
                Object renamedFile = c.get("renamed_file");
                dto.setRenamedFile(renamedFile != null && Boolean.parseBoolean(String.valueOf(renamedFile)));
                Object deletedFile = c.get("deleted_file");
                dto.setDeletedFile(deletedFile != null && Boolean.parseBoolean(String.valueOf(deletedFile)));
                if (Boolean.TRUE.equals(dto.getNewFile())) {
                    dto.setStatus("added");
                } else if (Boolean.TRUE.equals(dto.getDeletedFile())) {
                    dto.setStatus("deleted");
                } else if (Boolean.TRUE.equals(dto.getRenamedFile())) {
                    dto.setStatus("renamed");
                } else {
                    dto.setStatus("modified");
                }
                result.add(dto);
            }
        } catch (Exception e) {
            log.error("Failed to fetch changes from GitLab: {}", e.getMessage());
        }
        return result;
    }

    @Override
    public boolean mergeMR(String projectPath, Long mrIid) {
        String encodedPath = projectPath.replace("/", "%2F");
        String url = apiBaseUrl + "/projects/" + encodedPath + "/merge_requests/" + mrIid + "/merge";
        try {
            restTemplate.exchange(url, org.springframework.http.HttpMethod.PUT, createEntity(), Map.class);
            return true;
        } catch (org.springframework.web.client.HttpClientErrorException | org.springframework.web.client.HttpServerErrorException e) {
            String detail = extractGitLabError(e.getResponseBodyAsString());
            log.error("Failed to merge MR !{}: {} - {}", mrIid, e.getStatusCode(), detail);
            throw new GitPlatformException(e.getStatusCode().value(), detail != null ? detail : "Git平台合并失败: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to merge MR !{}: {}", mrIid, e.getMessage());
            throw new GitPlatformException(500, "Git平台合并失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean closeMR(String projectPath, Long mrIid) {
        String encodedPath = projectPath.replace("/", "%2F");
        String url = apiBaseUrl + "/projects/" + encodedPath + "/merge_requests/" + mrIid;
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("PRIVATE-TOKEN", accessToken);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        org.springframework.http.HttpEntity<Map> entity = new org.springframework.http.HttpEntity<>(
                Map.of("state_event", "close"), headers
        );
        try {
            restTemplate.exchange(url, org.springframework.http.HttpMethod.PUT, entity, Map.class);
            return true;
        } catch (org.springframework.web.client.HttpClientErrorException | org.springframework.web.client.HttpServerErrorException e) {
            String detail = extractGitLabError(e.getResponseBodyAsString());
            log.error("Failed to close MR !{}: {} - {}", mrIid, e.getStatusCode(), detail);
            throw new GitPlatformException(e.getStatusCode().value(), detail != null ? detail : "Git平台关闭失败: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to close MR !{}: {}", mrIid, e.getMessage());
            throw new GitPlatformException(500, "Git平台关闭失败: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> fetchReviewers(String projectPath, Long mrIid) {
        List<String> reviewers = new ArrayList<>();
        String encodedPath = projectPath.replace("/", "%2F");
        String url = apiBaseUrl + "/projects/" + encodedPath + "/merge_requests/" + mrIid;
        try {
            org.springframework.http.ResponseEntity<Map> response = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET, createEntity(), Map.class
            );
            Map<String, Object> body = response.getBody();
            if (body == null) return reviewers;
            List<Map<String, Object>> reviewerList = (List<Map<String, Object>>) body.get("reviewers");
            if (reviewerList != null) {
                for (Map<String, Object> item : reviewerList) {
                    String username = (String) item.get("username");
                    if (username != null) {
                        reviewers.add(username);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch reviewers from GitLab: {}", e.getMessage());
        }
        return reviewers;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String fetchApprovalStatus(String projectPath, Long mrIid) {
        String encodedPath = projectPath.replace("/", "%2F");
        String url = apiBaseUrl + "/projects/" + encodedPath + "/merge_requests/" + mrIid + "/approvals";
        try {
            org.springframework.http.ResponseEntity<Map> response = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET, createEntity(), Map.class
            );
            Map<String, Object> body = response.getBody();
            if (body == null) return "pending";

            Boolean approved = (Boolean) body.get("approved");
            List<Map<String, Object>> approvedBy = (List<Map<String, Object>>) body.get("approved_by");

            if (Boolean.TRUE.equals(approved)) {
                return "approved";
            }
            if (approvedBy != null && !approvedBy.isEmpty()) {
                return "reviewing";
            }
            return "pending";
        } catch (Exception e) {
            log.warn("Failed to fetch approval status from GitLab: {}", e.getMessage());
            return "pending";
        }
    }

    @Override
    public boolean reopenMR(String projectPath, Long mrIid) {
        String encodedPath = projectPath.replace("/", "%2F");
        String url = apiBaseUrl + "/projects/" + encodedPath + "/merge_requests/" + mrIid;
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("PRIVATE-TOKEN", accessToken);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        org.springframework.http.HttpEntity<Map> entity = new org.springframework.http.HttpEntity<>(
                Map.of("state_event", "reopen"), headers
        );
        try {
            restTemplate.exchange(url, org.springframework.http.HttpMethod.PUT, entity, Map.class);
            return true;
        } catch (org.springframework.web.client.HttpClientErrorException | org.springframework.web.client.HttpServerErrorException e) {
            String detail = extractGitLabError(e.getResponseBodyAsString());
            log.error("Failed to reopen MR !{}: {} - {}", mrIid, e.getStatusCode(), detail);
            throw new GitPlatformException(e.getStatusCode().value(), detail != null ? detail : "Git平台重新打开失败: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to reopen MR !{}: {}", mrIid, e.getMessage());
            throw new GitPlatformException(500, "Git平台重新打开失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean rerunCI(String projectPath, Long mrIid) {
        String encodedPath = projectPath.replace("/", "%2F");
        try {
            // 先获取 MR 关联的最新 pipeline
            String pipelinesUrl = apiBaseUrl + "/projects/" + encodedPath + "/merge_requests/" + mrIid + "/pipelines";
            org.springframework.http.ResponseEntity<List> response = restTemplate.exchange(
                    pipelinesUrl, org.springframework.http.HttpMethod.GET, createEntity(), List.class
            );
            List<Map<String, Object>> pipelines = response.getBody();
            if (pipelines == null || pipelines.isEmpty()) {
                throw new GitPlatformException(404, "该MR暂无CI Pipeline");
            }
            String pipelineId = String.valueOf(pipelines.get(0).get("id"));
            String retryUrl = apiBaseUrl + "/projects/" + encodedPath + "/pipelines/" + pipelineId + "/retry";
            restTemplate.exchange(retryUrl, org.springframework.http.HttpMethod.POST, createEntity(), Map.class);
            return true;
        } catch (GitPlatformException e) {
            throw e;
        } catch (org.springframework.web.client.HttpClientErrorException | org.springframework.web.client.HttpServerErrorException e) {
            String detail = extractGitLabError(e.getResponseBodyAsString());
            log.error("Failed to rerun CI for MR !{}: {} - {}", mrIid, e.getStatusCode(), detail);
            throw new GitPlatformException(e.getStatusCode().value(), detail != null ? detail : "Git平台重跑CI失败: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to rerun CI for MR !{}: {}", mrIid, e.getMessage());
            throw new GitPlatformException(500, "Git平台重跑CI失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean assignReviewer(String projectPath, Long mrIid, List<String> reviewers) {
        String encodedPath = projectPath.replace("/", "%2F");
        String url = apiBaseUrl + "/projects/" + encodedPath + "/merge_requests/" + mrIid;
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("PRIVATE-TOKEN", accessToken);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        // 先查询用户ID
        List<Integer> reviewerIds = new ArrayList<>();
        for (String username : reviewers) {
            try {
                String userUrl = apiBaseUrl + "/users?username=" + username;
                org.springframework.http.ResponseEntity<List> userResp = restTemplate.exchange(
                        userUrl, org.springframework.http.HttpMethod.GET, createEntity(), List.class
                );
                List<Map<String, Object>> users = userResp.getBody();
                if (users != null && !users.isEmpty()) {
                    Object id = users.get(0).get("id");
                    reviewerIds.add(((Number) id).intValue());
                }
            } catch (Exception e) {
                log.warn("Failed to resolve user id for {}: {}", username, e.getMessage());
            }
        }
        if (reviewerIds.isEmpty()) {
            throw new GitPlatformException(400, "无法解析指定的Reviewer用户");
        }
        org.springframework.http.HttpEntity<Map> entity = new org.springframework.http.HttpEntity<>(
                Map.of("reviewer_ids", reviewerIds), headers
        );
        try {
            restTemplate.exchange(url, org.springframework.http.HttpMethod.PUT, entity, Map.class);
            return true;
        } catch (org.springframework.web.client.HttpClientErrorException | org.springframework.web.client.HttpServerErrorException e) {
            String detail = extractGitLabError(e.getResponseBodyAsString());
            log.error("Failed to assign reviewers to MR !{}: {} - {}", mrIid, e.getStatusCode(), detail);
            throw new GitPlatformException(e.getStatusCode().value(), detail != null ? detail : "Git平台指派Reviewer失败: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to assign reviewers to MR !{}: {}", mrIid, e.getMessage());
            throw new GitPlatformException(500, "Git平台指派Reviewer失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean remindReviewers(String projectPath, Long mrIid, List<String> reviewers) {
        String encodedPath = projectPath.replace("/", "%2F");
        String url = apiBaseUrl + "/projects/" + encodedPath + "/merge_requests/" + mrIid + "/notes";
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("PRIVATE-TOKEN", accessToken);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        String mentions = reviewers.stream().map(r -> "@" + r).collect(Collectors.joining(" "));
        String body = mentions + " 请尽快评审此MR，谢谢！";
        org.springframework.http.HttpEntity<Map> entity = new org.springframework.http.HttpEntity<>(
                Map.of("body", body), headers
        );
        try {
            restTemplate.exchange(url, org.springframework.http.HttpMethod.POST, entity, Map.class);
            return true;
        } catch (org.springframework.web.client.HttpClientErrorException | org.springframework.web.client.HttpServerErrorException e) {
            String detail = extractGitLabError(e.getResponseBodyAsString());
            log.error("Failed to remind reviewers for MR !{}: {} - {}", mrIid, e.getStatusCode(), detail);
            throw new GitPlatformException(e.getStatusCode().value(), detail != null ? detail : "Git平台提醒Reviewer失败: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to remind reviewers for MR !{}: {}", mrIid, e.getMessage());
            throw new GitPlatformException(500, "Git平台提醒Reviewer失败: " + e.getMessage(), e);
        }
    }

    private org.springframework.http.HttpEntity<Void> createEntity() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("PRIVATE-TOKEN", accessToken);
        return new org.springframework.http.HttpEntity<>(headers);
    }

    @SuppressWarnings("unchecked")
    private MrDTO convertToDto(Map<String, Object> mr) {
        MrDTO dto = new MrDTO();
        dto.setPlatformMrId(Long.valueOf(String.valueOf(mr.get("iid"))));
        dto.setTitle((String) mr.get("title"));
        dto.setDescription((String) mr.get("description"));
        Map<String, Object> author = (Map<String, Object>) mr.get("author");
        if (author != null) {
            dto.setAuthorName((String) author.get("username"));
            dto.setAuthorAvatar((String) author.get("avatar_url"));
        }
        dto.setSourceBranch((String) mr.get("source_branch"));
        dto.setTargetBranch((String) mr.get("target_branch"));
        dto.setPlatformStatus((String) mr.get("state"));
        Object conflict = mr.get("has_conflicts");
        dto.setHasConflict(conflict != null && Boolean.parseBoolean(String.valueOf(conflict)));
        Object mergeableObj = mr.get("merge_status");
        dto.setMergeable("can_be_merged".equals(String.valueOf(mergeableObj)));
        dto.setChangesCount((Integer) mr.get("changes_count"));
        dto.setAdditions(0);
        dto.setDeletions(0);
        dto.setCommentsCount((Integer) mr.get("user_notes_count"));
        dto.setWebUrl((String) mr.get("web_url"));
        dto.setCreatedAt(parseDateTime((String) mr.get("created_at")));
        dto.setUpdatedAt(parseDateTime((String) mr.get("updated_at")));
        dto.setMergedAt(parseDateTime((String) mr.get("merged_at")));
        dto.setClosedAt(parseDateTime((String) mr.get("closed_at")));

        List<Map<String, Object>> reviewers = (List<Map<String, Object>>) mr.get("reviewers");
        if (reviewers != null) {
            dto.setReviewers(reviewers.stream()
                    .map(r -> (String) r.get("username"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
        }

        // 解析 head_pipeline 状态作为初始 ciStatus
        Map<String, Object> headPipeline = (Map<String, Object>) mr.get("head_pipeline");
        if (headPipeline != null) {
            dto.setCiStatus(mapCiStatus((String) headPipeline.get("status")));
        }

        return dto;
    }

    private String mapCiStatus(String status) {
        return switch (status != null ? status.toLowerCase() : "") {
            case "success", "passed" -> "success";
            case "failed" -> "failed";
            case "running" -> "running";
            case "pending", "created" -> "pending";
            case "canceled", "cancelled", "skipped" -> "canceled";
            default -> "unknown";
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<CommentDTO> fetchComments(String projectPath, Long mrIid) {
        List<CommentDTO> results = new ArrayList<>();
        try {
            String encodedPath = projectPath.replace("/", "%2F");
            String url = apiBaseUrl + "/projects/" + encodedPath + "/merge_requests/" + mrIid + "/notes?per_page=100";
            ResponseEntity<List> response = restTemplate.exchange(url,
                    org.springframework.http.HttpMethod.GET, createEntity(), List.class);
            List<Map<String, Object>> notes = response.getBody();
            if (notes != null) {
                for (Map<String, Object> n : notes) {
                    CommentDTO dto = new CommentDTO();
                    dto.setPlatformCommentId(String.valueOf(n.get("id")));
                    Map<String, Object> author = (Map<String, Object>) n.get("author");
                    dto.setAuthorName(author != null ? (String) author.get("username") : "unknown");
                    dto.setAuthorAvatar(author != null ? (String) author.get("avatar_url") : null);
                    dto.setBody((String) n.get("body"));
                    dto.setIsSystem(Boolean.TRUE.equals(n.get("system")));
                    dto.setCreatedAt(parseDateTime((String) n.get("created_at")));
                    results.add(dto);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch comments from GitLab: {}", e.getMessage());
        }
        return results;
    }

    private String extractGitLabError(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(json, Map.class);
            Object msg = map.get("message");
            if (msg != null) return msg.toString();
        } catch (Exception ignored) {}
        return null;
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null) return null;
        try {
            return LocalDateTime.parse(value.replace("Z", ""), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }
}
