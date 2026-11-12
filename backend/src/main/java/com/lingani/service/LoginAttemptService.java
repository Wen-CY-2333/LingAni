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
 * 登录失败次数限制，防止暴力尝试。单实例内存实现，重启后清零。
 */
@Component
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public void checkAllowed(String key) {
        Deque<Instant> records = failures.get(key);
        if (records == null) {
            return;
        }
        prune(records);
        if (records.size() >= MAX_FAILURES) {
            throw BizException.tooMany("登录尝试过于频繁，请 10 分钟后再试");
        }
    }

    public void recordFailure(String key) {
        Deque<Instant> records = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        prune(records);
        records.addLast(Instant.now());
    }

    public void reset(String key) {
        failures.remove(key);
    }

    private void prune(Deque<Instant> records) {
        Instant threshold = Instant.now().minus(WINDOW);
        while (!records.isEmpty() && records.peekFirst().isBefore(threshold)) {
            records.pollFirst();
        }
    }
}
