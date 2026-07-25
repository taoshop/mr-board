package com.mrboard.system;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E2E 集成测试基类
 * 提供 MockMvc、ObjectMapper 及认证辅助方法
 */
@SpringBootTest(classes = com.mrboard.web.MrBoardApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    protected static final String ADMIN_USERNAME = "admin";
    protected static final String ADMIN_PASSWORD = "Admin@123";

    protected String adminToken;

    @BeforeEach
    void authenticate() throws Exception {
        redisTemplate.delete("login:fail:" + ADMIN_USERNAME);
        this.adminToken = obtainAccessToken(ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    /**
     * 登录并获取 Access Token
     */
    protected String obtainAccessToken(String username, String password) throws Exception {
        String body = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse();
        Map<String, Object> result = objectMapper.readValue(response.getContentAsString(), new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        return (String) data.get("accessToken");
    }

    /**
     * 为请求添加 Bearer Token 认证头
     */
    protected RequestPostProcessor bearerToken() {
        return request -> {
            request.addHeader("Authorization", "Bearer " + adminToken);
            return request;
        };
    }

    /**
     * 将对象序列化为 JSON 字符串
     */
    protected String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
}
