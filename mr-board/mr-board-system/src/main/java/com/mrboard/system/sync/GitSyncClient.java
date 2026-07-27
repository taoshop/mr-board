package com.mrboard.system.sync;

import com.mrboard.system.sync.dto.ChangeDTO;
import com.mrboard.system.sync.dto.CiDTO;
import com.mrboard.system.sync.dto.CommentDTO;
import com.mrboard.system.sync.dto.MrDTO;

import java.util.Collections;
import java.util.List;

public interface GitSyncClient {

    boolean testConnection();

    List<MrDTO> fetchMRs(String projectPath, String state, String updatedAfter);

    List<CiDTO> fetchCI(String projectPath, Long mrIid);

    List<ChangeDTO> fetchChanges(String projectPath, Long mrIid);

    boolean mergeMR(String projectPath, Long mrIid);

    boolean closeMR(String projectPath, Long mrIid);

    default List<CommentDTO> fetchComments(String projectPath, Long mrIid) {
        return Collections.emptyList();
    }

    default List<String> fetchReviewers(String projectPath, Long mrIid) {
        return Collections.emptyList();
    }

    default String fetchApprovalStatus(String projectPath, Long mrIid) {
        return "pending";
    }

    default boolean rerunCI(String projectPath, Long mrIid) {
        return false;
    }

    default boolean assignReviewer(String projectPath, Long mrIid, List<String> reviewers) {
        return false;
    }

    default boolean remindReviewers(String projectPath, Long mrIid, List<String> reviewers) {
        return false;
    }

    default boolean reopenMR(String projectPath, Long mrIid) {
        return false;
    }
}
