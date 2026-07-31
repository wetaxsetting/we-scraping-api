package com.kosign.wecms.domain.payment.dispatch.callback.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the transaction boundary the 00302 fan-out depends on.
 *
 * <h2>What breaks if this is not asserted</h2>
 * {@code BulkReceptionResultService.scheduleExecutionFanOut} registers the fan-out on
 * {@code afterCommit} when a transaction synchronization is active, and falls back to running it
 * immediately when there is not one. Both paths are correct — with no transaction, the repository writes
 * have already auto-committed, so there is no barrier left to lose — but they are not <b>equally</b>
 * correct: only the {@code afterCommit} path guarantees that the reception advance and the gateway calls
 * are ordered by one explicit commit.
 *
 * <p>The fallback therefore doubles as a silent failure mode. Drop {@code @Transactional} from
 * {@code handle} in some future refactor and nothing fails: every callback quietly takes the
 * immediate path, each write commits on its own, and the only signal is a WARN nobody is grepping for.
 * These tests make that change fail the build instead.
 *
 * <p>Reflection on the annotations rather than a Spring context: the property under test is "is this
 * method proxied for transactions at all", which is exactly what the annotation declares and what a
 * refactor would remove. A context test would additionally need a database.
 */
class BulkReceptionResultTransactionBoundaryTest {

    @Test
    @DisplayName("handle() is @Transactional — so the proxied path always has a synchronization to register on")
    void handleIsTransactional() throws Exception {
        Method handle = BulkReceptionResultService.class.getMethod(
                "handle", com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope.class);

        Transactional tx = handle.getAnnotation(Transactional.class);
        assertThat(tx)
                .as("BulkReceptionResultService.handle must stay @Transactional. Without it every callback "
                        + "silently takes scheduleExecutionFanOut's no-transaction fallback: the writes "
                        + "auto-commit individually and the reception/00302 ordering is no longer anchored "
                        + "to one commit. Nothing else fails, which is why this is asserted here.")
                .isNotNull();

        assertThat(tx.propagation())
                .as("REQUIRED (the default) is what makes a synchronization active for the afterCommit "
                        + "registration. NOT_SUPPORTED / NEVER would suspend or forbid the transaction and "
                        + "silently re-enable the fallback path.")
                .isEqualTo(Propagation.REQUIRED);

        assertThat(tx.readOnly())
                .as("handle() advances PROC_STEP and writes audit rows — a read-only transaction would fail "
                        + "at flush time, or worse, silently discard the writes on some providers")
                .isFalse();
    }

    @Test
    @DisplayName("issueOne() is REQUIRES_NEW — each sibling commits independently of the callback and of its peers")
    void issueOneIsRequiresNew() throws Exception {
        Method issueOne = BulkExecutionInlineTxService.class.getMethod("issueOne",
                com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMstId.class,
                String.class, String.class, String.class, java.util.function.Consumer.class);

        Transactional tx = issueOne.getAnnotation(Transactional.class);
        assertThat(tx).as("issueOne must carry its own transaction").isNotNull();
        assertThat(tx.propagation())
                .as("REQUIRES_NEW is the whole point: legacy BATCH2 committed per row "
                        + "(beginTransaction:238, commit:260-263 / :317-320), so one sibling's failure could "
                        + "never undo another's — including a sibling whose 00302 already reached the bank. "
                        + "REQUIRED would re-join the caller and restore the defect.")
                .isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    @DisplayName("issueOne lives on a separate bean — a self-invocation would bypass the proxy entirely")
    void issueOneIsOnADifferentBeanThanTheCaller() {
        assertThat(BulkExecutionInlineTxService.class)
                .as("If issueOne were moved onto BulkReceptionResultService, the call from fanOutExecution "
                        + "would be a self-invocation: no proxy, no REQUIRES_NEW, silently back inside the "
                        + "caller's transaction. Keeping it on its own bean is load-bearing, not stylistic.")
                .isNotEqualTo(BulkReceptionResultService.class);
    }
}
