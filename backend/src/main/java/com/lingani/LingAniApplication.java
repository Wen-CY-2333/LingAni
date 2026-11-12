package com.lingani;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication
@EnableAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class) // 禁用 Spring Security 的默认用户认证配置，避免启动时创建默认用户
@ConfigurationPropertiesScan 
@MapperScan("com.lingani.mapper")
public class LingAniApplication {

    public static void main(String[] args) {
        SpringApplication.run(LingAniApplication.class, args);
    }
}
