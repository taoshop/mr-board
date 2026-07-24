package com.mrboard.system.sync;

import com.mrboard.system.sync.dto.CiDTO;
import com.mrboard.system.sync.dto.MrDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class GitLabClient implements GitSyncClient {

    private final RestTemplate restTemplate;
    private final String apiBaseUrl;
    private final String accessToken;

    public GitLabClient(String apiBaseUrl, String accessToken) {
        this.restTemplate = new RestTemplate();
        this.apiBaseUrl = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
        this.accessToken = accessToken;
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
    public boolean mergeMR(String projectPath, Long mrIid) {
        String encodedPath = projectPath.replace("/", "%2F");
        String url = apiBaseUrl + "/projects/" + encodedPath + "/merge_requests/" + mrIid + "/merge";
        try {
            restTemplate.exchange(url, org.springframework.http.HttpMethod.PUT, createEntity(), Map.class);
            return true;
        } catch (Exception e) {
            log.error("Failed to merge MR: {}", e.getMessage());
            return false;
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
        } catch (Exception e) {
            log.error("Failed to close MR: {}", e.getMessage());
            return false;
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

    private LocalDateTime parseDateTime(String value) {
        if (value == null) return null;
        try {
            return LocalDateTime.parse(value.replace("Z", ""), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }
}
