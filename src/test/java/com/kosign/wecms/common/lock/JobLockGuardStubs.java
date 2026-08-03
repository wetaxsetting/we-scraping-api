package com.kosign.wecms.common.lock;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * {@link JobLockGuard} stubs for unit tests, so a test can choose the contention
 * scenario without a database.
 *
 * <p>Unit tests must not exercise the real guard — it needs a {@code LockProvider}
 * over a live {@code shedlock} table. What the tests actually care about is the
 * <em>branch</em>: did the body run, or was it skipped because a scheduled twin
 * held the lock? These two stubs make that explicit at the call site.
 */
public final class JobLockGuardStubs {

    private JobLockGuardStubs() {
    }

    /**
     * Guard that always acquires and runs the body — the no-contention case, i.e.
     * the behaviour every pre-existing test implicitly assumed before locking was
     * introduced.
     */
    public static JobLockGuard alwaysFree() {
        // lenient(): each stub covers both the single-lock and multi-lock entry points, but any
        // given call site uses only one of them. Under MockitoExtension's default strict stubs
        // the unused one would fail the test with UnnecessaryStubbingException, which says
        // nothing about the behaviour under test.
        JobLockGuard guard = mock(JobLockGuard.class);
        lenient().when(guard.runIfFree(any(), any())).thenAnswer(inv -> {
            inv.getArgument(1, Runnable.class).run();
            return true;
        });
        lenient().when(guard.runIfAllFree(anyList(), any())).thenAnswer(inv -> {
            inv.getArgument(1, Runnable.class).run();
            return true;
        });
        return guard;
    }

    /**
     * Guard that never acquires — simulates the scheduled twin (or another node)
     * already running the same body. The guarded body must NOT be invoked.
     */
    public static JobLockGuard alwaysHeld() {
        JobLockGuard guard = mock(JobLockGuard.class);
        lenient().when(guard.runIfFree(any(), any())).thenReturn(false);
        lenient().when(guard.runIfAllFree(anyList(), any())).thenReturn(false);
        return guard;
    }

    /** Convenience for asserting the exact lock identity a call site requested. */
    public static JobLockGuard.LockKey singleKey(List<JobLockGuard.LockKey> keys) {
        if (keys.size() != 1) {
            throw new IllegalArgumentException("expected exactly one lock key, got " + keys);
        }
        return keys.get(0);
    }
}
