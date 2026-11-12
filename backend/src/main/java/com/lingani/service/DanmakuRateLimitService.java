package com.lingani.service;

import com.lingani.common.BizException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 弹幕发送频率限制：同一用户 3 秒内只能发送一条，且每分钟最多 20 条。
 */
@Component
public class DanmakuRateLimitService {

    private static final Duration MIN_INTERVAL = Duration.ofSeconds(3);
    private static final int MAX_PER_MINUTE = 20;

    private final Map<Long, Deque<Instant>> records = new ConcurrentHashMap<>();

    public void checkAllowed(Long userId) {
        Deque<Instant> userRecords = records.get(userId);
        if (userRecords == null) {
            return;
        }
        prune(userRecords);
        if (!userRecords.isEmpty()) {
            Instant last = userRecords.peekLast();
            if (last != null && last.plus(MIN_INTERVAL).isAfter(Instant.now())) {
                throw BizException.tooMany("发送太快啦，请稍后再发");
            }
        }
        if (userRecords.size() >= MAX_PER_MINUTE) {
            throw BizException.tooMany("弹幕发送过于频繁，请稍后再试");
        }
    }

    public void record(Long userId) {
        Deque<Instant> userRecords = records.computeIfAbsent(userId, key -> new ArrayDeque<>());
        prune(userRecords);
        userRecords.addLast(Instant.now());
    }

    private void prune(Deque<Instant> userRecords) {
        Instant threshold = Instant.now().minus(Duration.ofMinutes(1));
        while (!userRecords.isEmpty() && userRecords.peekFirst().isBefore(threshold)) {
            userRecords.pollFirst();
        }
    }
}
