package com.mrboard.system.controller;

import com.mrboard.system.BaseIntegrationTest;
import com.mrboard.system.entity.CiJob;
import com.mrboard.system.entity.Mrs;
import com.mrboard.system.entity.Project;
import com.mrboard.system.mapper.CiJobMapper;
import com.mrboard.system.mapper.MrsMapper;
import com.mrboard.system.mapper.ProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 看板与 MR 管理 E2E 测试
 * 覆盖：看板列定义、看板数据、项目列表、MR详情、MR状态更新、CI记录
 */
@Transactional
class BoardAndMrsControllerTest extends BaseIntegrationTest {

    @Autowired
    private MrsMapper mrsMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private CiJobMapper ciJobMapper;

    private Long projectId;
    private Long mrId;

    @BeforeEach
    void seedData() {
        Project project = new Project();
        project.setGitSourceId(1L);
        project.setPlatformProjectId("test-proj");
        project.setProjectPath("group/test-proj");
        project.setName("test-proj");
        project.setIsActive(1);
        projectMapper.insert(project);
        this.projectId = project.getId();

        Mrs mr = new Mrs();
        mr.setProjectId(projectId);
        mr.setPlatformMrId(1L);
        mr.setTitle("Test MR");
        mr.setAuthorName("admin");
        mr.setSourceBranch("feature/test");
        mr.setTargetBranch("master");
        mr.setPlatformStatus("opened");
        mr.setBoardStatus("open");
        mr.setCiStatus("success");
        mr.setHasConflict(false);
        mr.setMergeable(true);
        mr.setWebUrl("http://example.com/mr/1");
        mr.setCreatedAt(LocalDateTime.now());
        mr.setUpdatedAt(LocalDateTime.now());
        mrsMapper.insert(mr);
        this.mrId = mr.getId();

        CiJob ci = new CiJob();
        ci.setProjectId(projectId);
        ci.setPlatformMrId(1L);
        ci.setPlatformJobId("job-1");
        ci.setName("build");
        ci.setStage("build");
        ci.setStatus("success");
        ci.setCreatedAt(LocalDateTime.now());
        ciJobMapper.insert(ci);
    }

    @Test
    void getColumns_shouldReturn7Columns() throws Exception {
        mockMvc.perform(get("/api/board/columns").with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(7))
                .andExpect(jsonPath("$.data[0].key").value("pending_review"));
    }

    @Test
    void getBoard_shouldReturnGroupedMrs() throws Exception {
        mockMvc.perform(get("/api/board").with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.pending_review").isArray());
    }

    @Test
    void listProjects_shouldReturnProjects() throws Exception {
        mockMvc.perform(get("/api/projects").with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void getMrDetail_shouldReturnMrWithCiJobs() throws Exception {
        mockMvc.perform(get("/api/mrs/{id}", mrId).with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.mr.title").value("Test MR"))
                .andExpect(jsonPath("$.data.ciJobs").isArray());
    }

    @Test
    void getMrCi_shouldReturnCiJobs() throws Exception {
        mockMvc.perform(get("/api/board/mr/{id}/ci", 1L).with(bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void updateMrStatus_shouldSucceed_andRecordHistory() throws Exception {
        mockMvc.perform(put("/api/mrs/{id}/status", mrId)
                        .with(bearerToken())
                        .contentType("application/json")
                        .content("{\"boardStatus\":\"testing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/mrs/{id}", mrId).with(bearerToken()))
                .andExpect(jsonPath("$.data.mr.boardStatus").value("testing"));
    }

    @Test
    void updateMrStatus_shouldReturn409_whenInvalidTransition() throws Exception {
        Mrs mr = mrsMapper.selectById(mrId);
        mr.setHasConflict(true);
        mrsMapper.updateById(mr);

        mockMvc.perform(put("/api/mrs/{id}/status", mrId)
                        .with(bearerToken())
                        .contentType("application/json")
                        .content("{\"boardStatus\":\"merged\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409));
    }
}
