package com.mrboard.system.sync;

import com.mrboard.system.exception.GitPlatformException;
import com.mrboard.system.sync.dto.CiDTO;
import com.mrboard.system.sync.dto.ChangeDTO;
import com.mrboard.system.sync.dto.CommentDTO;
import com.mrboard.system.sync.dto.MrDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class GitHubClient implements GitSyncClient {

    private final RestTemplate restTemplate;
    private final String apiBaseUrl;
    private final String accessToken;

    public GitHubClient(String apiBaseUrl, String accessToken, String proxyHost, Integer proxyPort) {
        this.apiBaseUrl = apiBaseUrl != null ? apiBaseUrl : "https://api.github.com";
        this.accessToken = accessToken;

        if (proxyHost != null && proxyPort != null) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
            this.restTemplate = new RestTemplate(factory);
            log.info("GitHubClient using proxy {}:{}", proxyHost, proxyPort);
        } else {
            this.restTemplate = new RestTemplate();
        }
    }

    @Override
    public boolean testConnection() {
        try {
            String url = apiBaseUrl + "/user";
            restTemplate.exchange(url, HttpMethod.GET, createEntity(), Map.class);
            return true;
        } catch (Exception e) {
            log.warn("GitHub connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<MrDTO> fetchMRs(String repoFullName, String state, String updatedAfter) {
        List<MrDTO> result = new ArrayList<>();
        int page = 1;
        while (true) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(
                    apiBaseUrl + "/repos/" + repoFullName + "/pulls"
            )
                    .queryParam("per_page", 100)
                    .queryParam("page", page);
            if (state != null) {
                builder.queryParam("state", state);
            }

            try {
                ResponseEntity<List> response = restTemplate.exchange(
                        builder.toUriString(), HttpMethod.GET, createEntity(), List.class
                );
                List<Map<String, Object>> prs = response.getBody();
                if (prs == null || prs.isEmpty()) break;
                for (Map<String, Object> pr : prs) {
                    result.add(convertToDto(pr));
                }
                if (prs.size() < 100) break;
                page++;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {}
            } catch (Exception e) {
                log.error("Failed to fetch PRs from GitHub: {}", e.getMessage());
                break;
            }
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<CiDTO> fetchCI(String repoFullName, Long prNumber) {
        List<CiDTO> result = new ArrayList<>();
        try {
            String prUrl = apiBaseUrl + "/repos/" + repoFullName + "/pulls/" + prNumber;
            ResponseEntity<Map> prResponse = restTemplate.exchange(prUrl, HttpMethod.GET, createEntity(), Map.class);
            Map<String, Object> prBody = prResponse.getBody();
            if (prBody == null) return result;
            Map<String, Object> head = (Map<String, Object>) prBody.get("head");
            if (head == null) return result;
            String sha = (String) head.get("sha");
            if (sha == null) return result;

            String url = apiBaseUrl + "/repos/" + repoFullName + "/commits/" + sha + "/check-runs";
            HttpEntity<Void> entity = createEntity("application/vnd.github+json");
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) return result;
            List<Map<String, Object>> checkRuns = (List<Map<String, Object>>) body.get("check_runs");
            if (checkRuns == null) return result;
            for (Map<String, Object> run : checkRuns) {
                CiDTO dto = new CiDTO();
                dto.setPlatformJobId(String.valueOf(run.get("id")));
                dto.setName((String) run.get("name"));
                dto.setStatus(mapCiStatus((String) run.get("status"), (String) run.get("conclusion")));
                dto.setLogUrl((String) run.get("html_url"));
                result.add(dto);
            }
        } catch (Exception e) {
            log.error("Failed to fetch CI from GitHub: {}", e.getMessage());
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ChangeDTO> fetchChanges(String repoFullName, Long prNumber) {
        List<ChangeDTO> result = new ArrayList<>();
        int page = 1;
        while (true) {
            String url = apiBaseUrl + "/repos/" + repoFullName + "/pulls/" + prNumber + "/files?per_page=100&page=" + page;
            try {
                ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, createEntity(), List.class);
                List<Map<String, Object>> files = response.getBody();
                if (files == null || files.isEmpty()) break;
                for (Map<String, Object> f : files) {
                    ChangeDTO dto = new ChangeDTO();
                    dto.setNewPath((String) f.get("filename"));
                    dto.setOldPath((String) f.getOrDefault("previous_filename", dto.getNewPath()));
                    dto.setStatus((String) f.get("status"));
                    dto.setAdditions((Integer) f.get("additions"));
                    dto.setDeletions((Integer) f.get("deletions"));
                    dto.setDiff((String) f.get("patch"));
                    dto.setNewFile("added".equals(dto.getStatus()));
                    dto.setDeletedFile("removed".equals(dto.getStatus()));
                    dto.setRenamedFile("renamed".equals(dto.getStatus()));
                    result.add(dto);
                }
                if (files.size() < 100) break;
                page++;
            } catch (Exception e) {
                log.error("Failed to fetch changes from GitHub: {}", e.getMessage());
                break;
            }
        }
        return result;
    }

    @Override
    public boolean mergeMR(String repoFullName, Long prNumber) {
        String url = apiBaseUrl + "/repos/" + repoFullName + "/pulls/" + prNumber + "/merge";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "token " + accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map> entity = new HttpEntity<>(Map.of("merge_method", "merge"), headers);
            restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
            return true;
        } catch (org.springframework.web.client.HttpClientErrorException | org.springframework.web.client.HttpServerErrorException e) {
            String detail = extractErrorMessage(e.getResponseBodyAsString());
            log.error("Failed to merge PR #{}: {} - {}", prNumber, e.getStatusCode(), detail);
            throw new GitPlatformException(e.getStatusCode().value(), detail != null ? detail : "Git平台合并失败: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to merge PR #{}: {}", prNumber, e.getMessage());
            throw new GitPlatformException(500, "Git平台合并失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean closeMR(String repoFullName, Long prNumber) {
        String url = apiBaseUrl + "/repos/" + repoFullName + "/pulls/" + prNumber;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "token " + accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map> entity = new HttpEntity<>(Map.of("state", "closed"), headers);
            restTemplate.exchange(url, HttpMethod.PATCH, entity, Map.class);
            return true;
        } catch (org.springframework.web.client.HttpClientErrorException | org.springframework.web.client.HttpServerErrorException e) {
            String detail = extractErrorMessage(e.getResponseBodyAsString());
            log.error("Failed to close PR #{}: {} - {}", prNumber, e.getStatusCode(), detail);
            throw new GitPlatformException(e.getStatusCode().value(), detail != null ? detail : "Git平台关闭失败: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to close PR #{}: {}", prNumber, e.getMessage());
            throw new GitPlatformException(500, "Git平台关闭失败: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> fetchReviewers(String projectPath, Long mrIid) {
        List<String> reviewers = new ArrayList<>();
        String url = apiBaseUrl + "/repos/" + projectPath + "/pulls/" + mrIid;
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, createEntity(), Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) return reviewers;
            List<Map<String, Object>> requestedReviewers = (List<Map<String, Object>>) body.get("requested_reviewers");
            if (requestedReviewers != null) {
                for (Map<String, Object> item : requestedReviewers) {
                    String login = (String) item.get("login");
                    if (login != null) {
                        reviewers.add(login);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch requested reviewers from GitHub: {}", e.getMessage());
        }
        return reviewers;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String fetchApprovalStatus(String projectPath, Long mrIid) {
        String url = apiBaseUrl + "/repos/" + projectPath + "/pulls/" + mrIid + "/reviews";
        try {
            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, createEntity(), List.class);
            List<Map<String, Object>> reviews = response.getBody();
            if (reviews == null || reviews.isEmpty()) {
                return "pending";
            }

            boolean hasApproved = false;
            boolean hasChangesRequested = false;

            for (Map<String, Object> review : reviews) {
                String state = (String) review.get("state");
                if ("APPROVED".equalsIgnoreCase(state)) {
                    hasApproved = true;
                } else if ("CHANGES_REQUESTED".equalsIgnoreCase(state)) {
                    hasChangesRequested = true;
                }
            }

            if (hasApproved && !hasChangesRequested) {
                return "approved";
            }
            if (hasChangesRequested) {
                return "reviewing";
            }
            return "reviewing"; // 有 review 记录但未 approved / changes_requested（如 COMMENTED）
        } catch (Exception e) {
            log.warn("Failed to fetch approval status from GitHub: {}", e.getMessage());
            return "pending";
        }
    }

    private HttpEntity<Void> createEntity() {
        return createEntity(null);
    }

    private HttpEntity<Void> createEntity(String acceptHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + accessToken);
        if (acceptHeader != null) {
            headers.set("Accept", acceptHeader);
        }
        return new HttpEntity<>(headers);
    }

    @SuppressWarnings("unchecked")
    private MrDTO convertToDto(Map<String, Object> pr) {
        MrDTO dto = new MrDTO();
        dto.setPlatformMrId(Long.valueOf(String.valueOf(pr.get("number"))));
        dto.setTitle((String) pr.get("title"));
        dto.setDescription((String) pr.get("body"));
        Map<String, Object> user = (Map<String, Object>) pr.get("user");
        if (user != null) {
            dto.setAuthorName((String) user.get("login"));
            dto.setAuthorAvatar((String) user.get("avatar_url"));
        }
        Map<String, Object> head = (Map<String, Object>) pr.get("head");
        if (head != null) {
            dto.setSourceBranch((String) head.get("ref"));
        }
        Map<String, Object> base = (Map<String, Object>) pr.get("base");
        if (base != null) {
            dto.setTargetBranch((String) base.get("ref"));
        }
        dto.setPlatformStatus(mapGitHubState(pr));
        dto.setHasConflict(false);
        dto.setMergeable(!"closed".equals(pr.get("state")));
        dto.setChangesCount(0);
        dto.setAdditions(0);
        dto.setDeletions(0);
        dto.setCommentsCount((Integer) pr.getOrDefault("comments", 0));
        dto.setWebUrl((String) pr.get("html_url"));
        dto.setCreatedAt(parseDateTime((String) pr.get("created_at")));
        dto.setUpdatedAt(parseDateTime((String) pr.get("updated_at")));
        dto.setMergedAt(parseDateTime((String) pr.get("merged_at")));
        dto.setClosedAt(parseDateTime((String) pr.get("closed_at")));

        List<Map<String, Object>> requestedReviewers = (List<Map<String, Object>>) pr.get("requested_reviewers");
        if (requestedReviewers != null) {
            dto.setReviewers(requestedReviewers.stream()
                    .map(r -> (String) r.get("login"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    /** 区分 GitHub PR 的 merged 和 closed 状态 */
    private String mapGitHubState(Map<String, Object> pr) {
        String state = (String) pr.get("state");
        if ("closed".equals(state) && pr.get("merged_at") != null) {
            return "merged";
        }
        return state;
    }

    private String mapCiStatus(String status, String conclusion) {
        if ("completed".equalsIgnoreCase(status)) {
            return switch (conclusion != null ? conclusion.toLowerCase() : "") {
                case "success" -> "success";
                case "failure", "failed" -> "failed";
                case "cancelled", "skipped" -> "canceled";
                default -> "unknown";
            };
        }
        return "running".equalsIgnoreCase(status) ? "running" : "pending";
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<CommentDTO> fetchComments(String projectPath, Long mrIid) {
        List<CommentDTO> results = new ArrayList<>();
        try {
            String url = apiBaseUrl + "/repos/" + projectPath + "/issues/" + mrIid + "/comments?per_page=100";
            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, createEntity(), List.class);
            List<Map<String, Object>> comments = response.getBody();
            if (comments != null) {
                for (Map<String, Object> c : comments) {
                    CommentDTO dto = new CommentDTO();
                    dto.setPlatformCommentId(String.valueOf(c.get("id")));
                    Map<String, Object> user = (Map<String, Object>) c.get("user");
                    dto.setAuthorName(user != null ? (String) user.get("login") : "unknown");
                    dto.setAuthorAvatar(user != null ? (String) user.get("avatar_url") : null);
                    dto.setBody((String) c.get("body"));
                    dto.setIsSystem(false);
                    dto.setCreatedAt(parseDateTime((String) c.get("created_at")));
                    results.add(dto);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch comments from GitHub: {}", e.getMessage());
        }
        return results;
    }

    private String extractErrorMessage(String json) {
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
