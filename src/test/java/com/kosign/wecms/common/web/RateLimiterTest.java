package com.kosign.wecms.common.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-10 — in-memory rate limiter behaviour.
 */
class RateLimiterTest {

    @Test
    void allowsUpToLimitThenBlocks() {
        RateLimiter rl = new RateLimiter();
        assertThat(rl.tryAcquire("k", 3, 60)).isTrue();   // 1
        assertThat(rl.tryAcquire("k", 3, 60)).isTrue();   // 2
        assertThat(rl.tryAcquire("k", 3, 60)).isTrue();   // 3
        assertThat(rl.tryAcquire("k", 3, 60)).isFalse();  // 4 → blocked
        assertThat(rl.tryAcquire("k", 3, 60)).isFalse();  // still blocked
    }

    @Test
    void differentKeysAreIndependent() {
        RateLimiter rl = new RateLimiter();
        assertThat(rl.tryAcquire("a", 1, 60)).isTrue();
        assertThat(rl.tryAcquire("a", 1, 60)).isFalse();  // "a" exhausted
        assertThat(rl.tryAcquire("b", 1, 60)).isTrue();   // "b" independent
    }

    @Test
    void windowResetAllowsAgain() throws InterruptedException {
        RateLimiter rl = new RateLimiter();
        assertThat(rl.tryAcquire("k", 1, 1)).isTrue();    // 1 per 1-second window
        assertThat(rl.tryAcquire("k", 1, 1)).isFalse();   // blocked within window
        Thread.sleep(1100);                                // window rolls over
        assertThat(rl.tryAcquire("k", 1, 1)).isTrue();    // allowed again
    }
}
