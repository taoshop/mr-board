package com.mrboard.system.sync;

import com.mrboard.common.utils.AesUtil;
import com.mrboard.system.entity.GitSource;
import com.mrboard.system.mapper.GitSourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitClientFactory {

    private final GitSourceMapper gitSourceMapper;
    private final AesUtil aesUtil;

    public GitSyncClient create(Long gitSourceId) {
        GitSource source = gitSourceMapper.selectById(gitSourceId);
        if (source == null) {
            throw new IllegalArgumentException("Git source not found: " + gitSourceId);
        }
        return create(source);
    }

    public GitSyncClient create(GitSource source) {
        try {
            String token = aesUtil.decrypt(source.getAccessToken());
            return create(source.getPlatformType(), source.getApiBaseUrl(), token);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt access token", e);
        }
    }

    public GitSyncClient create(Integer platformType, String apiBaseUrl, String token) {
        if (platformType == null || platformType == 1) {
            return new GitLabClient(apiBaseUrl, token);
        } else if (platformType == 2) {
            return new GitHubClient(apiBaseUrl, token);
        }
        throw new IllegalArgumentException("Unsupported platform type: " + platformType);
    }
}
