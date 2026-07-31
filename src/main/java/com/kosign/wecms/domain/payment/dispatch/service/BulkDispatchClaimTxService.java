package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * H-4 · Owns the <b>pre-egress duplicate decision</b> for one logical bulk-payment file.
 *
 * <p>The single responsibility of this class is to make the claim a <b>short transaction that has
 * committed before any irreversible external call</b>. That ordering is the whole point, and it is
 * the ordering legacy uses: {@code FILE_DEPOSIT_TRANSFER_BATCH1} commits at {@code :696-697} and only
 * then uploads at {@code :700} and calls 00301 at {@code :724}.
 *
 * <h2>Why a separate bean rather than an annotation on {@code BulkReceptionService}</h2>
 * {@link BulkReceptionService#receive} carries no {@code @Transactional} and is entered from two
 * places that have no ambient transaction — {@code DispatchAutoReceiveRegistrar}'s
 * {@code afterCommit()} hook (the payment transaction has already completed) and
 * {@code StrandedRecoveryService}'s sweep. Spring's proxy only applies to <b>external</b> calls, so a
 * {@code @Transactional} method on {@code BulkReceptionService} invoked from within the same class
 * would be silently ignored and the claim would run non-transactionally. Calling across a bean
 * boundary guarantees the proxy — and therefore the commit — actually happens.
 *
 * <h2>Why {@code REQUIRES_NEW}</h2>
 * Today no caller holds a transaction, so {@code REQUIRED} would behave identically. {@code
 * REQUIRES_NEW} makes the guarantee independent of that: if a future caller ever wraps
 * {@code receive()} in a transaction, the claim must still commit on its own <i>before</i> the upload,
 * not at the end of an outer transaction that also spans the egress. It also means a later rollback of
 * the caller's work cannot un-claim a file whose bytes have already left.
 *
 * <h2>What this deliberately does NOT do</h2>
 * It does not hold a lock across the SFTP transfer or the EFS call. The claim UPDATE takes a row lock,
 * commits, and releases it — the file build, upload and 00301 all run afterwards with no lock on the
 * pay-file row. Holding that lock across a multi-second SFTP transfer would serialise unrelated
 * tenants' recovery sweeps behind one slow upload.
 *
 * @see TbPayFileMstRepository#claimForReception
 * @see com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository#claimForDispatch
 *      the equivalent guard on the real-time API rail
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkDispatchClaimTxService {

    private final TbPayFileMstRepository payFileMstRepository;

    /**
     * Attempt to claim the bulk pay-file identified by {@code (bizKey, tranDt, fileSeqNo)} for 00301
     * reception. Commits before returning.
     *
     * <p>The logical file identity is the pay-file's natural key, which is also what every driver
     * already passes around — so normal dispatch and stranded recovery contend on exactly the same
     * key and cannot both win.
     *
     * @param procDttm 14-char {@code yyyyMMddHHmmss} stamp written with the transition
     * @return {@code true} when this call won the claim and owns the dispatch; {@code false} when
     *         another driver already owns it or the file is no longer WAIT — in which case the caller
     *         must return without performing any external call
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimForReception(String bizKey, String tranDt, String fileSeqNo, String procDttm) {
        int claimed = payFileMstRepository.claimForReception(bizKey, tranDt, fileSeqNo, procDttm);
        if (claimed == 0) {
            // Expected under a race or a duplicate hand-off — not an error. Logged at info so a
            // genuine double-drive is visible in ops logs without looking like a failure.
            log.info("bulk reception claim lost (already claimed or no longer WAIT) "
                    + "bizKey={} tranDt={} fileSeqNo={}", bizKey, tranDt, fileSeqNo);
            return false;
        }
        log.debug("bulk reception claim won bizKey={} tranDt={} fileSeqNo={}", bizKey, tranDt, fileSeqNo);
        return true;
    }

    /**
     * H-3 / R-2 · Record a terminal failure on a pay-file that was claimed and then stranded, in its
     * own committed transaction.
     *
     * <p><b>Why this cannot be a plain {@code save(pay)} in the caller.</b> The claim commits
     * {@code '9'→'6'} here; each split file then runs in its own {@code REQUIRES_NEW} transaction. When
     * a split file's transaction rolls back, the {@code failBoth(...)} write it attempted rolls back
     * with it — while the claim, committed separately, does not. Without a write from outside that
     * transaction the row stays at {@code '6'} permanently, and every recovery and monitoring query
     * historically selected only {@code '9'}, so nothing would ever see it.
     *
     * <p>{@code REQUIRES_NEW} for the same reason as {@link #claimForReception}: this must commit on its
     * own, independent of whatever the caller's transaction is doing (and the caller reaches this from a
     * {@code catch} block, where the ambient transaction may already be marked rollback-only).
     *
     * <p>The underlying UPDATE is <b>guarded on {@code '6'}</b>, so this can only move a row out of the
     * stranded state and can never overwrite a FAIL a split file already recorded, or a status a
     * callback has since advanced.
     *
     * @param rsMsg bounded, non-sensitive summary — exception type plus operational context only.
     *              Never an API key, encrypted payload, account number or raw gateway body; the column
     *              is also length-constrained, so the caller truncates.
     * @return {@code true} when this call recorded the failure; {@code false} when the row was not (or
     *         no longer) stranded, which is a normal outcome and not an error
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean failStrandedPayFile(String bizKey, String tranDt, String fileSeqNo,
                                       String rsMsg, String procDttm) {
        int failed = payFileMstRepository.failClaimedPayFile(bizKey, tranDt, fileSeqNo, rsMsg, procDttm);
        if (failed == 0) {
            // Already terminal, already advanced, or never claimed — all benign.
            log.info("bulk pay-file not stranded at claim state, terminal write skipped "
                    + "bizKey={} tranDt={} fileSeqNo={}", bizKey, tranDt, fileSeqNo);
            return false;
        }
        log.warn("bulk pay-file marked FAIL after a stranded claim bizKey={} tranDt={} fileSeqNo={} rsMsg={}",
                bizKey, tranDt, fileSeqNo, rsMsg);
        return true;
    }
}
