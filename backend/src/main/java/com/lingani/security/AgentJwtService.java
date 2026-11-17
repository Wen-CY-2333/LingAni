package com.lingani.security;

import com.lingani.config.AgentJwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Agent 委托令牌（AGENT JWT）的签发与校验——短期委托凭据。
 * <p>
 * 由 Spring Boot 在用户 JWT 校验通过后签发，只在用户发起的单次 Agent 请求链路内流转：
 * 前端 -> Python Agent（Authorization: Bearer）-> Spring Boot 个人数据接口（X-Agent-JWT）。
 * <p>
 * 与 {@link UserJwtService} 结构对称：同样使用 HS256 签名，同样提供
 * {@code issue / verify / getTtlSeconds} 三个入口；区别在于密钥独立、有效期极短，
 * 并额外限定 issuer / audience / scope，因此不能当作登录凭证复用。
 */
@Component
public class AgentJwtService {

    /** Agent 回调后端个人数据接口时携带本令牌的请求头名称。 */
    public static final String HEADER_NAME = "X-Agent-JWT";

    private static final String SCOPE_AGENT = "agent";

    private final AgentJwtProperties properties;
    private final SecretKey key;

    public AgentJwtService(AgentJwtProperties properties) {
        this.properties = properties;
        String secret = properties.getSecret();
        if (secret == null || secret.isBlank()) {
            this.key = null;
            return;
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("AGENT_JWT_SECRET 长度至少需要 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String issue(LoginUser user) {
        SecretKey signingKey = requireKey();
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(properties.getTtlSeconds());
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuer(properties.getIssuer())
                .audience().add(properties.getAudience()).and()
                .claim("username", user.getUsername())
                .claim("nickname", user.getNickname())
                .claim("scope", SCOPE_AGENT)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims verify(String token) {
        SecretKey signingKey = requireKey();
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.getIssuer())
                .requireAudience(properties.getAudience())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!SCOPE_AGENT.equals(claims.get("scope", String.class))) {
            throw new JwtException("Agent 委托令牌 scope 不匹配");
        }
        return claims;
    }

    public long getTtlSeconds() {
        return properties.getTtlSeconds();
    }

    private SecretKey requireKey() {
        if (key == null) {
            throw new IllegalStateException("未配置 AGENT_JWT_SECRET，无法签发或校验 Agent 委托令牌");
        }
        return key;
    }
}
