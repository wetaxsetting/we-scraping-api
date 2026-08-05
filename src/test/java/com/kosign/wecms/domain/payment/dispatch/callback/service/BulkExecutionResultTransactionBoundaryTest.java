package com.kosign.wecms.domain.payment.dispatch.callback.service;

import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C-4 · Guards the transaction boundary of the 01302 execution-result callback.
 *
 * <h2>What breaks if this is not asserted</h2>
 * {@code BulkExecutionResultService.handle} SFTP-downloads and parses the bank's result file. Legacy
 * {@code FILE_DEPOSIT_TRANSFER_API2_act.jsp} does both <b>before</b> opening its transaction at
 * {@code :291} — download {@code :234}, remote archive move {@code :235}, disconnect {@code :236},
 * empty-file check {@code :249-254}, parse loop {@code :262-290}. The refactor used to hold a
 * {@code @Transactional} across all of it: a database connection pinned to a network round trip, on a
 * callback thread.
 *
 * <p>C-4 split it. Nothing <i>fails</i> if someone re-adds {@code @Transactional} to {@code handle} — the
 * writer would simply join the outer transaction and the download would be back inside it, silently.
 * Likewise nothing fails if {@code applyResult} is moved onto {@code BulkExecutionResultService}: the call
 * would become a self-invocation, Spring's proxy would never see it, and every write would run with no
 * transaction at all — no rollback, so a failure mid-way through the {@code TB_FIRM_FILE_RSLT} loop would
 * leave a half-applied result file, which is the one outcome the whole design forbids.
 *
 * <p>Both regressions are invisible at runtime until they cost real money. These tests make them fail the
 * build instead.
 *
 * <p>Reflection on the annotations rather than a Spring context: the property under test is "where does
 * the transaction begin", which is exactly what the annotations declare and what a refactor would move.
 * Same approach, and same reasoning, as {@link BulkReceptionResultTransactionBoundaryTest}.
 */
class BulkExecutionResultTransactionBoundaryTest {

    @Test
    @DisplayName("handle() is NOT @Transactional — the SFTP download and parse must not hold a connection")
    void handleIsNotTransactional() throws Exception {
        Method handle = BulkExecutionResultService.class.getMethod("handle", EfsCallbackEnvelope.class);

        assertThat(handle.getAnnotation(Transactional.class))
                .as("BulkExecutionResultService.handle must stay NON-transactional (C-4). It performs an "
                        + "SFTP download, a remote archive move and a full file parse; a transaction here "
                        + "pins a DB connection across all of that on a callback thread. Legacy opens its "
                        + "transaction only afterwards, at FILE_DEPOSIT_TRANSFER_API2_act.jsp:291. If a "
                        + "write needs a transaction, put it in BulkExecutionResultTxService.applyResult — "
                        + "do not re-annotate this method.")
                .isNull();

        assertThat(BulkExecutionResultService.class.getAnnotation(Transactional.class))
                .as("A class-level @Transactional would re-apply to handle() and quietly undo C-4")
                .isNull();
    }

    @Test
    @DisplayName("applyResult() is @Transactional(REQUIRED) — every write of this leg is one atomic unit")
    void applyResultIsTransactional() throws Exception {
        Method applyResult = BulkExecutionResultTxService.class.getMethod(
                "applyResult", BulkExecutionResultCommand.class);

        Transactional tx = applyResult.getAnnotation(Transactional.class);
        assertThat(tx)
                .as("applyResult carries the transaction handle() gave up. Without it the "
                        + "TB_FIRM_FILE_RSLT per-line loop, the detail reflection, the 2→3 advance and the "
                        + "pay-file finalize each auto-commit on their own — and a failure part-way leaves "
                        + "a half-applied result file with no rollback. Legacy holds all of it in one "
                        + "transaction (API2_act.jsp:291 onward, with :322-329 rolling back the whole "
                        + "insert loop on any single failed line).")
                .isNotNull();

        assertThat(tx.propagation())
                .as("REQUIRED. NOT_SUPPORTED / NEVER would suspend or forbid the transaction and restore "
                        + "exactly the partial-apply hazard this method exists to prevent.")
                .isEqualTo(Propagation.REQUIRED);

        assertThat(tx.readOnly())
                .as("applyResult writes TB_FIRM_FILE_RSLT, TB_FIRM_FILE_DATA, TB_PAY_FILE_DATA, "
                        + "TB_FIRM_FILE_MST and TB_PAY_FILE_MST — read-only would fail at flush, or worse "
                        + "discard the writes silently")
                .isFalse();
    }

    @Test
    @DisplayName("applyResult lives on a separate bean — a self-invocation would bypass the proxy entirely")
    void applyResultIsOnADifferentBeanThanTheCaller() {
        assertThat(BulkExecutionResultTxService.class)
                .as("If applyResult were moved onto BulkExecutionResultService, the call from handle() "
                        + "would be a self-invocation: no proxy, no transaction, every write auto-committing "
                        + "individually. Keeping it on its own bean is load-bearing, not stylistic — the same "
                        + "reason DispatchApiTranAuditWriter and BulkExecutionInlineTxService are their own "
                        + "beans.")
                .isNotEqualTo(BulkExecutionResultService.class);
    }
}
