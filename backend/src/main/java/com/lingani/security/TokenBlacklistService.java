package com.lingani.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 无状态 JWT 的退出登录处理：把当前 Token 的 jti 加入本地黑名单，直到 Token 自然过期。
 * 单实例部署足够；多实例部署时可替换为 Redis 实现。
 */
@Component
public class TokenBlacklistService {

    private final Map<String, Instant> revokedTokens = new ConcurrentHashMap<>();

    public void revoke(String jti, Instant expireAt) {
        if (jti == null || expireAt == null) {
            return;
        }
        cleanExpired();
        revokedTokens.put(jti, expireAt);
    }

    public boolean isRevoked(String jti) {
        if (jti == null) {
            return false;
        }
        Instant expireAt = revokedTokens.get(jti);
        if (expireAt == null) {
            return false;
        }
        if (expireAt.isBefore(Instant.now())) {
            revokedTokens.remove(jti);
            return false;
        }
        return true;
    }

    private void cleanExpired() {
        Instant now = Instant.now();
        revokedTokens.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
