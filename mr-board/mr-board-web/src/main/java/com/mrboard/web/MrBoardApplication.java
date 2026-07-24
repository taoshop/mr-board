package com.mrboard.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication(scanBasePackages = "com.mrboard")
@MapperScan("com.mrboard.system.mapper")
@EnableCaching
public class MrBoardApplication {

    public static void main(String[] args) {
        SpringApplication.run(MrBoardApplication.class, args);
    }
}
