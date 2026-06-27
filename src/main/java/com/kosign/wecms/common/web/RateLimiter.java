package com.kosign.wecms.common.web;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * F-10 — minimal in-memory fixed-window rate limiter.
 *
 * <p>Each {@code key} (e.g. {@code "login:<ip>"} or {@code "reveal:<biz>:<user>"})
 * gets a counter that resets every {@code windowSeconds}. The caller decides the
 * limit per call site, so one shared bean serves login + reveal (and any future
 * endpoint) without per-feature config.
 *
 * <p><b>Known limitations (documented, by design):</b> this is <b>in-process</b> —
 * counters are <b>not shared across instances</b> and <b>reset on restart</b>. It is
 * a first line against brute-force / abuse, not a distributed quota. The durable
 * multi-instance upgrade is a shared store (e.g. Redis / a PO-approved table);
 * keep the call-site contract ({@link #tryAcquire}) so only the backing map changes.
 * Counters are NOT a security boundary on their own — they complement the durable
 * 5-failure account lockout (see {@code LoginLockoutService}).
 */
@Component
public class RateLimiter {

    private record Window(long startEpochSec, int count) { }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicInteger writes = new AtomicInteger();
    private static final int PURGE_EVERY = 512;

    /**
     * Record one hit against {@code key} and report whether it is within the limit.
     *
     * @return {@code true} if the request is ALLOWED (count ≤ {@code maxRequests}
     *         within the current window); {@code false} if it exceeds the limit.
     */
    public boolean tryAcquire(String key, int maxRequests, int windowSeconds) {
        long now = Instant.now().getEpochSecond();
        Window w = windows.compute(key, (k, cur) ->
                (cur == null || now - cur.startEpochSec() >= windowSeconds)
                        ? new Window(now, 1)
                        : new Window(cur.startEpochSec(), cur.count() + 1));
        maybePurge(now, windowSeconds);
        return w.count() <= maxRequests;
    }

    /** Drop stale windows occasionally so the map stays bounded (no scheduler). */
    private void maybePurge(long now, int windowSeconds) {
        if (writes.incrementAndGet() % PURGE_EVERY == 0) {
            windows.values().removeIf(w -> now - w.startEpochSec() >= windowSeconds);
        }
    }
}
