package com.kosign.wecms.common.lock;

import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Behavioural tests for {@link JobLockGuard} against a real
 * {@link DefaultLockingTaskExecutor} over an in-memory {@link LockProvider}.
 *
 * <p>The provider is a genuine mutual-exclusion implementation rather than a mock,
 * so the nesting semantics of {@link JobLockGuard#runIfAllFree} are actually
 * exercised — that method is what keeps the exchange-rate on-demand catch-up from
 * overlapping any of the three scheduled FX jobs, and a mock would not show whether
 * partial acquisition leaks a held lock.
 */
class JobLockGuardTest {

    /** Minimal in-memory LockProvider: a name is either held or free. */
    private static final class InMemoryLockProvider implements LockProvider {
        private final Set<String> held = new HashSet<>();

        @Override
        public Optional<SimpleLock> lock(LockConfiguration config) {
            String name = config.getName();
            if (held.contains(name)) {
                return Optional.empty();
            }
            held.add(name);
            return Optional.of(new SimpleLock() {
                @Override
                public void unlock() {
                    held.remove(name);
                }
            });
        }

        boolean isHeld(String name) {
            return held.contains(name);
        }

        void forceHold(String name) {
            held.add(name);
        }

        int heldCount() {
            return held.size();
        }
    }

    private final InMemoryLockProvider provider = new InMemoryLockProvider();
    private final JobLockGuard guard = new JobLockGuard(new DefaultLockingTaskExecutor(provider));

    private static JobLockGuard.LockKey key(String name) {
        return JobLockGuard.LockKey.of(name, Duration.ofMinutes(10), Duration.ZERO);
    }

    // ── single lock ──────────────────────────────────────────────────────────

    @Test
    void runsBodyAndReportsTrue_whenLockIsFree() {
        AtomicInteger runs = new AtomicInteger();

        boolean ran = guard.runIfFree(key("job-a"), runs::incrementAndGet);

        assertThat(ran).isTrue();
        assertThat(runs).hasValue(1);
    }

    @Test
    void skipsBodyAndReportsFalse_whenLockIsHeld() {
        provider.forceHold("job-a");
        AtomicInteger runs = new AtomicInteger();

        boolean ran = guard.runIfFree(key("job-a"), runs::incrementAndGet);

        assertThat(ran).as("a held lock must not run the body").isFalse();
        assertThat(runs).as("body must not execute at all").hasValue(0);
    }

    @Test
    void releasesLockAfterBodyCompletes() {
        guard.runIfFree(key("job-a"), () -> { });
        assertThat(provider.isHeld("job-a")).isFalse();
    }

    @Test
    void releasesLockWhenBodyThrows() {
        assertThatThrownBy(() -> guard.runIfFree(key("job-a"), () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class).hasMessage("boom");

        assertThat(provider.isHeld("job-a"))
                .as("a throwing body must not leak the lock — the next pass has to be able to run")
                .isFalse();
    }

    @Test
    void propagatesRuntimeExceptionVerbatim() {
        // The HAENGSUNG Refresh endpoint depends on this: BusinessException must reach
        // GlobalExceptionHandler unchanged rather than being wrapped into WBE9999.
        class Marker extends RuntimeException {
            Marker() {
                super("marker");
            }
        }
        assertThatThrownBy(() -> guard.runIfFree(key("job-a"), () -> {
            throw new Marker();
        })).isInstanceOf(Marker.class);
    }

    // ── nested / multi-lock ──────────────────────────────────────────────────

    @Test
    void runsBody_whenAllLocksFree() {
        AtomicInteger runs = new AtomicInteger();

        boolean ran = guard.runIfAllFree(
                List.of(key("a"), key("b"), key("c")), runs::incrementAndGet);

        assertThat(ran).isTrue();
        assertThat(runs).hasValue(1);
        assertThat(provider.heldCount()).as("all locks released").isZero();
    }

    @Test
    void skipsBody_whenAnyLockIsHeld() {
        provider.forceHold("b");
        AtomicInteger runs = new AtomicInteger();

        boolean ran = guard.runIfAllFree(
                List.of(key("a"), key("b"), key("c")), runs::incrementAndGet);

        assertThat(ran).as("partial acquisition must report false").isFalse();
        assertThat(runs).as("body must not run when any lock is unavailable").hasValue(0);
    }

    @Test
    void doesNotLeakEarlierLocks_whenALaterLockIsHeld() {
        provider.forceHold("c");

        guard.runIfAllFree(List.of(key("a"), key("b"), key("c")), () -> { });

        assertThat(provider.isHeld("a")).as("'a' must be released after the failed nest").isFalse();
        assertThat(provider.isHeld("b")).as("'b' must be released after the failed nest").isFalse();
        assertThat(provider.isHeld("c")).as("only the pre-existing holder remains").isTrue();
    }

    @Test
    void holdsEveryLockSimultaneouslyWhileTheBodyRuns() {
        // This is the property the FX on-demand path relies on: runOnDemand() writes all
        // three banks inside one transaction, so all three identities must be excluded for
        // its whole duration, not one at a time.
        guard.runIfAllFree(List.of(key("a"), key("b"), key("c")), () -> {
            assertThat(provider.isHeld("a")).isTrue();
            assertThat(provider.isHeld("b")).isTrue();
            assertThat(provider.isHeld("c")).isTrue();
        });
    }

    @Test
    void emptyKeyListRunsBody() {
        AtomicInteger runs = new AtomicInteger();
        assertThat(guard.runIfAllFree(List.of(), runs::incrementAndGet)).isTrue();
        assertThat(runs).hasValue(1);
    }
}
