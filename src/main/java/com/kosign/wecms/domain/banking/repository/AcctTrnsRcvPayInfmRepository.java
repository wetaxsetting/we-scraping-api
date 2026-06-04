package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.AcctTrnsRcvPayInfm;
import com.kosign.wecms.domain.banking.entity.AcctTrnsRcvPayInfmId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Per §13.6 risk R3 + the user's Sprint 1 decision: implement the
 * Transaction History "confirmed" flag with derived methods first.
 * existsByBizKeyAndAcctSeqNoAndCnfmYn(...) returns true when at least
 * one ACCT_TRNS_RCV_PAY_INFM row for the given transaction has
 * {@code CNFM_YN = 'Y'}.
 *
 * Promote to a native JOIN against BUSA_DOC_RCV_PAY_HIS only if the
 * derived shape can no longer express the contract.
 */
public interface AcctTrnsRcvPayInfmRepository extends JpaRepository<AcctTrnsRcvPayInfm, AcctTrnsRcvPayInfmId> {

    boolean existsByBizKeyAndAcctSeqNoAndCnfmYn(String bizKey, String acctSeqNo, String cnfmYn);

    List<AcctTrnsRcvPayInfm> findByBizKeyAndAcctSeqNo(String bizKey, String acctSeqNo);

    List<AcctTrnsRcvPayInfm> findByBizKeyAndAcctSeqNoIn(String bizKey, List<String> acctSeqNos);

    /**
     * Internal-transfer classification for the Daily Financial report.
     * Returns the receipt/payment rows whose {@code RCV_PAY_USAG_CD} is
     * one of the legacy internal-transfer usage codes
     * ({@code 4000000008 / 6000000062 / 4000000104 / 6000000061} —
     * verbatim from {@code rept_0015_001_r001_act.jsp:264-267}), scoped
     * to the supplied transaction {@code ACCT_SEQ_NO}s (the day's
     * transactions). The service collects the matched {@code acctSeqNo}
     * set and splits each transaction into the SND/RCV vs. INTL_SND/
     * INTL_RCV buckets.
     *
     * <p>{@code acctSeqNos} must be non-empty — the service skips the
     * call when the day has no transactions.
     */
    List<AcctTrnsRcvPayInfm> findByBizKeyAndAcctSeqNoInAndRcvPayUsagCdIn(
            String bizKey, List<String> acctSeqNos, List<String> rcvPayUsagCds);

    /**
     * Settings &gt; Contacts in-use guard (OQ-2 resolved 2026-05-12 /
     * legacy {@code WBE1190}). Returns {@code true} when at least one
     * payment / receipt row in the tenant references the given contact
     * &mdash; the contacts service blocks the trash transition
     * ({@code TRCO_STTS = 'C'}) in that case.
     *
     * <p>V21 renamed the legacy {@code TRCO_KEY} column on this table
     * to {@code RCV_PAY_TRCO_KEY}; the derived method therefore
     * matches against {@code rcvPayTrcoKey} (NOT {@code trcoKey}).
     * The {@code BIZ_KEY} predicate enforces tenant isolation so this
     * cannot leak the existence of cross-tenant references.
     *
     * <p>Used by {@code ContactsService.softDelete(...)},
     * {@code ContactsService.bulkSoftDelete(...)}, and
     * {@code ContactsService.update(...)} when {@code trco_stts}
     * transitions to {@code 'C'}.
     */
    boolean existsByBizKeyAndRcvPayTrcoKey(String bizKey, String rcvPayTrcoKey);
}
