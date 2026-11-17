package com.lingani.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 用户登录令牌（USER JWT）配置——长期身份凭据。
 * <p>
 * 登录成功后签发，用于访问全部受保护业务接口，默认 7 天有效。
 * 与 Agent 侧短期委托令牌（AGENT JWT）使用完全独立的密钥，二者互不通用。
 */
@Data
@ConfigurationProperties(prefix = "lingani.jwt.user")
public class UserJwtProperties {

    /**
     * HS256 密钥，长度至少 32 字节，通过环境变量 USER_JWT_SECRET 注入。
     */
    private String secret = "lingani-local-dev-secret-change-me-0123456789abcdef";

    /**
     * 有效期（秒），默认 7 天。
     */
    private long ttlSeconds = 604800;

    private String issuer = "lingani";
}
