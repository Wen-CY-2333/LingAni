package com.lingani.security;

import com.lingani.config.UserJwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * 用户登录令牌（USER JWT）的签发与校验——长期身份凭据。
 * <p>
 * 与 {@link AgentJwtService} 结构对称：同样使用 HS256 签名，同样提供
 * {@code issue / verify / getTtlSeconds} 三个入口，区别仅在于密钥、有效期与声明用途。
 */
@Component
public class UserJwtService {

    private final UserJwtProperties properties;
    private final SecretKey key;

    public UserJwtService(UserJwtProperties properties) {
        this.properties = properties;
        String secret = properties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("未配置 USER_JWT_SECRET，无法签发或校验用户令牌");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("USER_JWT_SECRET 长度至少需要 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String issue(Long userId, String username, int tokenVersion) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(properties.getTtlSeconds());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuer(properties.getIssuer())
                .claim("username", username)
                .claim("tv", tokenVersion)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims verify(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getTtlSeconds() {
        return properties.getTtlSeconds();
    }
}
