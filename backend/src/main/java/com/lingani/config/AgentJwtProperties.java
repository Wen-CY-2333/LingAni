package com.lingani.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 委托令牌（AGENT JWT）配置——短期委托凭据。
 * <p>
 * 由后端代表已登录用户签发，仅在单次 AI 对话链路内流转（前端 → Agent → 后端个人数据接口），
 * 默认 5 分钟失效，且限定 audience 与 scope，不能当作登录凭证复用。
 * 密钥通过环境变量 AGENT_JWT_SECRET 注入，必须与 Agent 服务侧完全一致。
 */
@Data
@ConfigurationProperties(prefix = "lingani.jwt.agent")
public class AgentJwtProperties {

    private String secret = "";

    private String issuer = "lingani-backend";

    private String audience = "lingani-agent";

    /**
     * 有效期（秒），默认 5 分钟，显著短于用户令牌。
     */
    private long ttlSeconds = 300;
}
