package com.mrboard.system.config;

import com.mrboard.common.utils.AesUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AesConfig {

    @Value("${AES_KEY:mr-board-aes-key-32-chars-long}")
    private String aesKey;

    @Bean
    public AesUtil aesUtil() {
        return new AesUtil(aesKey);
    }
}
