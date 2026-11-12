package com.lingani.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lingani.jwt")
public class JwtProperties {

    /**
     * HS256 密钥，长度至少 32 字节，通过环境变量 JWT_SECRET 注入。
     */
    private String secret = "lingani-local-dev-secret-change-me-0123456789abcdef";

    private long expireSeconds = 604800;

    private String issuer = "lingani";
}
