package com.mrboard.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = com.mrboard.web.MrBoardApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Test
    void contextLoads() {
    }
}
