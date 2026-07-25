package com.mrboard.system.config;

import com.mrboard.common.utils.AesUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AesConfig {

    @Value("${AES_KEY:MrBoardAesKey2026SecretKey123456}")
    private String aesKey;

    @Bean
    public AesUtil aesUtil() {
        return new AesUtil(aesKey);
    }
}
