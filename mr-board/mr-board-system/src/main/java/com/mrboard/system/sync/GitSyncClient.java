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
}
