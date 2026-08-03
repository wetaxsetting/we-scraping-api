package com.kosign.wecms.common.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Programmatic ShedLock entry point for job bodies reached from somewhere other
 * than a {@code @Scheduled} method — user-triggered "refresh now" endpoints,
 * on-demand catch-up paths, and one job delegating into another job's body.
 *
 * <p><b>The problem this solves.</b> {@code @SchedulerLock} is applied by the
 * scheduler proxy, so it guards <em>only</em> the scheduled invocation. A manual
 * or cross-job call into the same body runs completely unlocked, which
 * scheduler-audit finding F-3 recorded for the exchange-rate on-demand path and
 * the interactive HAENGSUNG import, and finding F-8 recorded for the
 * recovery→result-check delegation. Wrapping those call sites here — with the
 * {@link JobLockNames} constant its scheduled twin already uses — restores one
 * lock identity per business body.
 *
 * <p><b>Transaction placement matters.</b> Always call this <em>outside</em> the
 * business transaction, never from inside it. ShedLock's
 * {@code JdbcTemplateLockProvider} performs its lock row INSERT/UPDATE in its
 * own transaction, but a caller that has already opened a read-only transaction
 * (e.g. {@code ExchangeRateService.list}) must not have lock acquisition
 * entangled with it. The guarded bodies below are therefore invoked from
 * non-transactional wrapper methods that then enter {@code @Transactional} work.
 *
 * <p><b>Not acquired ≠ failed.</b> {@link #runIfFree} returns {@code false} when
 * another holder has the lock; it does not throw and does not wait. Each caller
 * decides what "someone else is already doing this" means for its own contract —
 * a background catch-up path logs and moves on, whereas a user-facing endpoint
 * surfaces a business error so the user can retry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobLockGuard {

    private final LockingTaskExecutor lockingTaskExecutor;

    /**
     * One lock name, held for the duration of {@code body}.
     *
     * @param key  lock identity + bounds; the name must be the same
     *             {@link JobLockNames} constant the scheduled twin uses
     * @param body the business body to run while holding the lock
     * @return {@code true} if the lock was acquired and {@code body} ran;
     *         {@code false} if another holder had it (body NOT run)
     */
    public boolean runIfFree(LockKey key, Runnable body) {
        LockConfiguration config = new LockConfiguration(
                ClockProvider.now(), key.name(), key.atMostFor(), key.atLeastFor());
        try {
            // The Runnable overload of executeWithLock returns void, so it cannot tell
            // "ran" from "lock was held". The TaskWithResult overload does — hence the
            // constant TRUE payload; wasExecuted() is the answer we actually want.
            LockingTaskExecutor.TaskResult<Boolean> result = lockingTaskExecutor.executeWithLock(
                    (LockingTaskExecutor.TaskWithResult<Boolean>) () -> {
                        body.run();
                        return Boolean.TRUE;
                    },
                    config);

            if (!result.wasExecuted()) {
                log.info("job-lock '{}' already held — skipping this invocation", key.name());
                return false;
            }
            return true;
        } catch (RuntimeException | Error e) {
            // Propagate verbatim. Callers depend on this: the HAENGSUNG Refresh endpoint
            // lets BusinessException reach GlobalExceptionHandler, and the batch bodies
            // rely on their own per-row/per-tenant handling being the outermost one.
            // Wrapping here would convert a documented business outcome into WBE9999.
            throw e;
        } catch (Throwable t) {
            // Unreachable for a Runnable body (it cannot throw checked exceptions); present
            // only because the ShedLock TaskWithResult signature declares Throwable.
            throw new IllegalStateException("job-lock '" + key.name() + "' body failed", t);
        }
    }

    /**
     * Acquire several locks, then run {@code body} while holding all of them.
     *
     * <p>Used by the exchange-rate on-demand catch-up, which executes the work of
     * all three scheduled FX jobs inside a single transaction and therefore has
     * to exclude all three of their locks at once.
     *
     * <p><b>Deadlock safety.</b> Locks are acquired in the caller-supplied list
     * order and that order is fixed at the call site. Only multi-lock callers
     * ever hold more than one of these names, and there is exactly one such
     * caller per lock family, so no two holders can request the same pair in
     * opposite orders. Keep it that way: if a second multi-lock caller is added
     * over the same family, it must use the identical ordering.
     *
     * @return {@code true} only if every lock was acquired and {@code body} ran
     */
    public boolean runIfAllFree(List<LockKey> keys, Runnable body) {
        if (keys.isEmpty()) {
            body.run();
            return true;
        }
        // Track the nested outcome separately: the outer runIfFree reports only
        // whether IT acquired, not whether the inner levels did.
        boolean[] innerRan = {false};
        boolean outerAcquired = runIfFree(
                keys.get(0),
                () -> innerRan[0] = runIfAllFree(keys.subList(1, keys.size()), body));
        return outerAcquired && innerRan[0];
    }

    /**
     * A lock identity plus its bounds.
     *
     * @param name       a {@link JobLockNames} constant — never an inline string
     * @param atMostFor  crash-recovery ceiling; must exceed the worst-case body
     *                   duration under expected load, or a still-running body
     *                   loses its lock
     * @param atLeastFor floor that stops a very fast body from being re-run by
     *                   another node inside the same window
     */
    public record LockKey(String name, Duration atMostFor, Duration atLeastFor) {

        public static LockKey of(String name, Duration atMostFor, Duration atLeastFor) {
            return new LockKey(name, atMostFor, atLeastFor);
        }
    }
}
