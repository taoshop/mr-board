package com.mrboard.system.sync;

import com.mrboard.system.sync.dto.MrDTO;
import com.mrboard.system.sync.dto.CiDTO;

import java.util.List;

public interface GitSyncClient {

    boolean testConnection();

    List<MrDTO> fetchMRs(String projectPath, String state, String updatedAfter);

    List<CiDTO> fetchCI(String projectPath, Long mrIid);

    boolean mergeMR(String projectPath, Long mrIid);

    boolean closeMR(String projectPath, Long mrIid);
}
