package com.kosign.wecms.common.tx;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * Runs an action inside a brand-new transaction ({@code REQUIRES_NEW}) so a
 * caller can implement boundary-level one-shot retry: a failed attempt's
 * transaction — and its Hibernate session — is fully rolled back and discarded
 * before the next attempt runs in a fresh session.
 *
 * <p>Use case: {@code MAX+1} key allocation that has no DB row lock (e.g.
 * {@code scdul_pymn_sumr.scdul_pymn_no} via {@code wb_get_next_val}). Two
 * concurrent creates can read the same {@code MAX} and collide on the PK at
 * flush; re-trying in the same poisoned session is unsafe, so the retry must
 * cross a transaction boundary.
 *
 * <p>The supplied action is <strong>not</strong> separately {@code @Transactional}
 * — it executes within the transaction this helper opens. This must be a
 * distinct Spring bean so the call crosses a proxy boundary (a self-invocation
 * inside the same bean would not start a new transaction).
 */
@Component
public class TransactionalRetry {

    /**
     * Execute {@code action} in a new transaction and return its result. Any
     * exception propagates to the caller after that transaction rolls back, so
     * the caller can decide whether to retry.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T runOnce(Supplier<T> action) {
        return action.get();
    }
}
