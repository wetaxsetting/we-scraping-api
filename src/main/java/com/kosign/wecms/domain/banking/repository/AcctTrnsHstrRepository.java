package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstrId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AcctTrnsHstrRepository extends JpaRepository<AcctTrnsHstr, AcctTrnsHstrId> {

    /** Most-recent-first transaction history for one account (date desc, then time desc). */
    List<AcctTrnsHstr> findByBizKeyAndBankCdAndAcctNoAndTrscDtBetweenOrderByTrscDtDescTrscTmDesc(
            String bizKey, String bankCd, String acctNo, String fromDt, String toDt);

    /** Variant without a date range — used when the UI does not supply from/to. */
    List<AcctTrnsHstr> findByBizKeyAndBankCdAndAcctNoOrderByTrscDtDescTrscTmDesc(
            String bizKey, String bankCd, String acctNo);

    /**
     * All transactions for a tenant on a single {@code TRSC_DT}
     * ({@code yyyyMMdd}). Powers the Daily Financial per-account daily
     * debit/credit aggregates (legacy {@code DAILY_FINCL_REPT_DAILY_SUMR_DETL}
     * SND/RCV/INTL columns, live-computed — see
     * {@code docs/features/banking/reports/daily-financial/P0-fix-plan.md}).
     * Grouping by {@code (bank_cd, acct_no)} happens in the service.
     */
    List<AcctTrnsHstr> findByBizKeyAndTrscDt(String bizKey, String trscDt);

    /**
     * As-of end-of-day balance source for Daily Financial — the most
     * recent transaction row on or before {@code asOf} for one account.
     * Its {@code BAL} is the running balance after that transaction, i.e.
     * the account's end-of-day balance as of the selected date (legacy
     * {@code DAILY_FINCL_REPT_DAILY_SUMR_DETL.BAL}, live-computed).
     *
     * <p>Ordered {@code TRSC_DT desc, TRSC_TM desc, ACCT_SEQ_NO desc} so
     * the latest same-day transaction wins. Returns empty when the
     * account has no history on or before {@code asOf}; the service then
     * falls back to {@code ACCT_MSTR.BAL}.
     */
    java.util.Optional<AcctTrnsHstr>
        findFirstByBizKeyAndBankCdAndAcctNoAndTrscDtLessThanEqualOrderByTrscDtDescTrscTmDescAcctSeqNoDesc(
            String bizKey, String bankCd, String acctNo, String asOf);

    /**
     * Idempotency lookup for the Bank Transaction Sync orchestrator —
     * mirrors legacy {@code fnsh_0001_001_c001_act.jsp:853} (Coocon JOB
     * pump checks {@code biz_key = ? AND bank_trns_full_id = ? AND
     * locked_yn = ?} before inserting). A non-empty result means the
     * row already exists for this scrape window and we MUST NOT
     * INSERT a duplicate.
     *
     * <p>Returns the matched row (any one — `BANK_TRNS_FULL_ID` is
     * deterministic over the natural key so duplicates are an
     * invariant violation, not normal). Service uses {@code isPresent}
     * as a boolean and ignores the payload.
     *
     * <p>Backed by the V156 index
     * {@code ix_acct_trns_hstr_biz_full_id}; expected O(log N).
     */
    java.util.Optional<AcctTrnsHstr> findFirstByBizKeyAndBankTrnsFullId(
            String bizKey, String bankTrnsFullId);

    /**
     * Smart Organizer — set the bank-side match link. Updates the four
     * columns V23 added to ACCT_TRNS_HSTR (`SCDUL_PYMN_NO`,
     * `SCDUL_PYMN_DETL_NO`, plus the existing `LINK_MATCH_YN` flag and
     * audit columns) via a native UPDATE because the entity does not
     * yet map those payment-FK columns (Sprint 2+ deferred — adding
     * them to the entity is a separate change).
     *
     * <p>Returns the number of rows updated; the service treats
     * {@code 0} as "row not found / wrong tenant" and surfaces
     * {@code WBE_TRANSACTION_NOT_FOUND}.
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE ACCT_TRNS_HSTR" +
            "    SET LINK_MATCH_YN = 'Y'," +
            "        SCDUL_PYMN_NO = :scdulPymnNo," +
            "        SCDUL_PYMN_DETL_NO = :scdulPymnDetlNo," +
            "        MOD_ID = :userId," +
            "        MOD_DTM = :nowDtm" +
            "  WHERE BIZ_KEY = :bizKey" +
            "    AND ACCT_SEQ_NO = :acctSeqNo",
            nativeQuery = true)
    int markBankRowMatched(
            @Param("bizKey") String bizKey,
            @Param("acctSeqNo") String acctSeqNo,
            @Param("scdulPymnNo") String scdulPymnNo,
            @Param("scdulPymnDetlNo") String scdulPymnDetlNo,
            @Param("userId") String userId,
            @Param("nowDtm") String nowDtm);

    /**
     * Cash Flow report — bank-side source rows.
     *
     * <p>Mirrors the {@code all_payment_data} bank UNION branch of
     * {@code get_cashflow_report()}: rows where {@code chart_acct_cd}
     * is non-empty AND at least one {@code acct_trns_rcv_pay_infm}
     * sibling row has {@code cnfm_yn = 'Y'}. The join is on
     * {@code (biz_key, acct_seq_no)} — NOT the composite PK — so a
     * single bank row may match multiple rcv_pay rows; the EXISTS guard
     * keeps the result count = bank-row count regardless.
     *
     * <p>Year filter applied as a string-prefix on {@code trsc_dt}
     * (yyyyMMdd format), matching the legacy
     * {@code EXTRACT(YEAR FROM TO_DATE(...))} predicate.
     */
    @Query("""
            SELECT h FROM AcctTrnsHstr h
             WHERE h.bizKey = :bizKey
               AND h.chartAcctCd IS NOT NULL
               AND h.chartAcctCd <> ''
               AND SUBSTRING(h.trscDt, 1, 4) = :year
               AND EXISTS (
                   SELECT 1 FROM AcctTrnsRcvPayInfm rp
                    WHERE rp.bizKey = h.bizKey
                      AND rp.acctSeqNo = h.acctSeqNo
                      AND rp.cnfmYn = 'Y'
               )
            """)
    List<AcctTrnsHstr> findCashFlowSourceRows(
            @org.springframework.data.repository.query.Param("bizKey") String bizKey,
            @org.springframework.data.repository.query.Param("year") String year);

    /** Smart Organizer — clear the bank-side match link (undo flow). */
    @Modifying
    @Transactional
    @Query(value = "UPDATE ACCT_TRNS_HSTR" +
            "    SET LINK_MATCH_YN = 'N'," +
            "        SCDUL_PYMN_NO = NULL," +
            "        SCDUL_PYMN_DETL_NO = NULL," +
            "        OVRS_PYMN_NO = NULL," +
            "        OVRS_PYMN_DETL_NO = NULL," +
            "        MOD_ID = :userId," +
            "        MOD_DTM = :nowDtm" +
            "  WHERE BIZ_KEY = :bizKey" +
            "    AND ACCT_SEQ_NO = :acctSeqNo",
            nativeQuery = true)
    int clearBankRowMatch(
            @Param("bizKey") String bizKey,
            @Param("acctSeqNo") String acctSeqNo,
            @Param("userId") String userId,
            @Param("nowDtm") String nowDtm);
}
