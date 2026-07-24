package com.mrboard.system.config;

import com.mrboard.common.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Value("${jwt.access-secret}")
    private String accessSecret;

    @Value("${jwt.refresh-secret}")
    private String refreshSecret;

    @Bean
    public JwtUtil jwtUtil() {
        return new JwtUtil(accessSecret, refreshSecret);
    }
}
