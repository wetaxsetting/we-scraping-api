package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.AcctTrnsRcvPayInfm;
import com.kosign.wecms.domain.banking.entity.AcctTrnsRcvPayInfmId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /* =================================================================
     *  Transaction-History picker RECENT quick-picks (server-side).
     *
     *  Derived from the classification the user actually applies to bank
     *  transactions — `ACCT_TRNS_RCV_PAY_INFM` carries the picked
     *  `RCV_PAY_USAG_CD` / `RCV_PAY_TRCO_KEY` / `VAS_ACCT_CD` and stamps
     *  `MOD_ID` / `MOD_DTM` (= the classifying user + time) on every pick
     *  (see TransactionService.updatePurpose/Contact/VasAccount). So
     *  "recently used by this user" = the most-recent distinct value the
     *  user set, newest first. No localStorage, no new table, no writes
     *  to the shared USER_*_USE_HIS tables (PO decision 2026-07-24).
     *  `REG_DTM`/`MOD_DTM` are VARCHAR(14) yyyyMMddHHmmss, so lexical DESC
     *  is chronological DESC. Pass `PageRequest.of(0, limit)` for top-N.
     * ================================================================= */

    /** Recent distinct contact keys the user assigned, newest first. */
    @Query("""
            select r.rcvPayTrcoKey
              from AcctTrnsRcvPayInfm r
             where r.bizKey = :bizKey
               and (r.modId = :userId or r.regId = :userId)
               and coalesce(r.rcvPayTrcoKey, '') <> ''
             group by r.rcvPayTrcoKey
             order by max(coalesce(r.modDtm, r.regDtm)) desc
            """)
    List<String> findRecentContactKeys(
            @Param("bizKey") String bizKey,
            @Param("userId") String userId,
            Pageable pageable);

    /** Recent distinct VAS-account codes the user assigned, newest first. */
    @Query("""
            select r.vasAcctCd
              from AcctTrnsRcvPayInfm r
             where r.bizKey = :bizKey
               and (r.modId = :userId or r.regId = :userId)
               and coalesce(r.vasAcctCd, '') <> ''
             group by r.vasAcctCd
             order by max(coalesce(r.modDtm, r.regDtm)) desc
            """)
    List<String> findRecentVasCodes(
            @Param("bizKey") String bizKey,
            @Param("userId") String userId,
            Pageable pageable);

    /** Recent distinct purpose codes the user assigned within one
     *  {@code USAG_GRP_CD} bucket (4 = Banking Deposit / 6 = Banking
     *  Withdrawal), newest first. Joined to {@code BUSI_USAG_SET_INFM}
     *  so the RECENT list on a deposit row carries only grp-4 codes and
     *  a withdrawal row only grp-6 — matching the direction-scoped picker
     *  options (PROD parity). */
    @Query("""
            select r.rcvPayUsagCd
              from AcctTrnsRcvPayInfm r, BusiUsagSetInfm u
             where r.bizKey = :bizKey
               and u.bizKey = r.bizKey
               and u.useUsagCd = r.rcvPayUsagCd
               and u.usagGrpCd = :group
               and (r.modId = :userId or r.regId = :userId)
               and coalesce(r.rcvPayUsagCd, '') <> ''
             group by r.rcvPayUsagCd
             order by max(coalesce(r.modDtm, r.regDtm)) desc
            """)
    List<String> findRecentPurposeCodesByGroup(
            @Param("bizKey") String bizKey,
            @Param("userId") String userId,
            @Param("group") String group,
            Pageable pageable);
}
