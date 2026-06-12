package com.kosign.wecms.domain.payment.internaltransfer.repository;

import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetlId;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link ScdulPymnSumrDetl}. Every query
 * is tenant-scoped on {@code BIZ_KEY}.
 */
public interface ScdulPymnSumrDetlRepository
        extends JpaRepository<ScdulPymnSumrDetl, ScdulPymnSumrDetlId> {

    /**
     * Numeric ascending order on {@code SCDUL_PYMN_DETL_NO} for the per-line
     * grid. The column is {@code VARCHAR(20)} holding integer text; since MR-2
     * stores JEX-style <em>unpadded</em> per-tenant numbers (e.g. {@code 9999},
     * {@code 10000}) instead of the old zero-padded {@code 000001}, a plain
     * string sort would mis-order across digit-count boundaries
     * ({@code "10000" < "9999"}). Ordering by length first, then value, yields
     * true numeric order for non-negative integer strings without a SQL
     * {@code CAST} (portable + avoids {@code JpaSort.unsafe} cast quirks). If a
     * future need requires an explicit cast, swap this for
     * {@code JpaSort.unsafe(ASC, "cast(scdulPymnDetlNo as long)")}.
     */
    Sort DETL_NO_NUMERIC_ASC =
            JpaSort.unsafe(Sort.Direction.ASC, "length(d.scdulPymnDetlNo)", "d.scdulPymnDetlNo");

    /** Detail-line lookup by composite PK + tenant. Used by the
     *  Reporting Account update endpoint. */
    Optional<ScdulPymnSumrDetl> findByBizKeyAndScdulPymnNoAndScdulPymnDetlNo(
            String bizKey, String scdulPymnNo, String scdulPymnDetlNo);

    /** Lines for one batch, sorted by the caller-supplied {@code Sort}.
     *  Used by the detail endpoint to render the per-line table.
     *
     *  <p>Declared as an explicit {@code @Query} (not a derived finder) so the
     *  {@link #DETL_NO_NUMERIC_ASC} {@link JpaSort#unsafe} {@code length(...)}
     *  ordering is appended to the ORDER BY verbatim. A derived (PartTree)
     *  finder validates every {@code Sort} token as a property path and would
     *  reject {@code length(scdulPymnDetlNo)} with a
     *  {@code PropertyReferenceException}. The unqualified attribute names in
     *  the unsafe order resolve against this query's single root. */
    @Query("SELECT d FROM ScdulPymnSumrDetl d" +
            "  WHERE d.bizKey = :bizKey" +
            "    AND d.scdulPymnNo = :scdulPymnNo")
    List<ScdulPymnSumrDetl> findByBizKeyAndScdulPymnNo(
            @Param("bizKey") String bizKey,
            @Param("scdulPymnNo") String scdulPymnNo,
            Sort sort);

    /** Bulk delete by batch — used when deleting a draft batch. The
     *  parent SCDUL_PYMN_SUMR row must be deleted in the same
     *  transaction (see InternalTransferService#delete). */
    long deleteByBizKeyAndScdulPymnNo(String bizKey, String scdulPymnNo);

    /**
     * Smart Organizer candidate scan — domestic scheduled payment lines
     * for the tenant whose `LINK_MATCH_YN != 'Y'`. The service layer
     * applies date / amount / keyword filters in memory before
     * returning to the frontend — the row count is bounded by the
     * tenant's payment-detail volume.
     */
    @Query("SELECT d FROM ScdulPymnSumrDetl d WHERE d.bizKey = :bizKey" +
            "   AND (d.linkMatchYn IS NULL OR d.linkMatchYn <> 'Y')")
    List<ScdulPymnSumrDetl> findUnmatchedForTenant(@Param("bizKey") String bizKey);

    /**
     * Smart Organizer — set the payment-side match link. Mirrors legacy
     * {@code comm_0196_001_c001_act.jsp} which patches
     * {@code SCDUL_PYMN_SUMR_DETL.LINK_MATCH_YN = 'Y'} plus the
     * bank-transaction back-references.
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE SCDUL_PYMN_SUMR_DETL" +
            "    SET LINK_MATCH_YN = 'Y'," +
            "        ACCT_SEQ_NO = :acctSeqNo," +
            "        BANK_TRNS_SHORT_ID = :bankTrnsShortId," +
            "        BANK_TRNS_FULL_ID = :bankTrnsFullId," +
            "        MOD_ID = :userId," +
            "        MOD_DTM = :nowDtm" +
            "  WHERE BIZ_KEY = :bizKey" +
            "    AND SCDUL_PYMN_NO = :scdulPymnNo" +
            "    AND SCDUL_PYMN_DETL_NO = :scdulPymnDetlNo",
            nativeQuery = true)
    int markDetailMatched(
            @Param("bizKey") String bizKey,
            @Param("scdulPymnNo") String scdulPymnNo,
            @Param("scdulPymnDetlNo") String scdulPymnDetlNo,
            @Param("acctSeqNo") String acctSeqNo,
            @Param("bankTrnsShortId") String bankTrnsShortId,
            @Param("bankTrnsFullId") String bankTrnsFullId,
            @Param("userId") String userId,
            @Param("nowDtm") String nowDtm);

    /** Smart Organizer — clear the payment-side match link (undo flow). */
    @Modifying
    @Transactional
    @Query(value = "UPDATE SCDUL_PYMN_SUMR_DETL" +
            "    SET LINK_MATCH_YN = 'N'," +
            "        ACCT_SEQ_NO = NULL," +
            "        BANK_TRNS_SHORT_ID = NULL," +
            "        BANK_TRNS_FULL_ID = NULL," +
            "        MOD_ID = :userId," +
            "        MOD_DTM = :nowDtm" +
            "  WHERE BIZ_KEY = :bizKey" +
            "    AND SCDUL_PYMN_NO = :scdulPymnNo" +
            "    AND SCDUL_PYMN_DETL_NO = :scdulPymnDetlNo",
            nativeQuery = true)
    int clearDetailMatch(
            @Param("bizKey") String bizKey,
            @Param("scdulPymnNo") String scdulPymnNo,
            @Param("scdulPymnDetlNo") String scdulPymnDetlNo,
            @Param("userId") String userId,
            @Param("nowDtm") String nowDtm);

    /**
     * Cash Flow report — scheduled-payment source rows.
     *
     * <p>Joins {@code SCDUL_PYMN_SUMR_DETL} to its header on
     * {@code (biz_key, scdul_pymn_no)} and projects the columns the
     * legacy {@code all_payment_data} scheduled UNION branch reads:
     * detail's {@code tran_amt} + {@code chart_acct_cd}, header's
     * {@code ccy_cd} / {@code out_bank_cd} / {@code wdrl_act_no} /
     * {@code reg_dtm} / {@code appr_seq_no}.
     *
     * <p>Year is derived from the header's {@code reg_dtm}
     * (registration time), NOT the detail's scheduled execution date —
     * matches the legacy function's
     * {@code EXTRACT(YEAR FROM TO_DATE(sps.reg_dtm,...))} predicate.
     *
     * <p>Approval-status filter is applied by
     * {@link com.kosign.wecms.domain.banking.service.cashflow.ApprovalStatusResolver}
     * downstream — this query returns ALL classified rows for the year.
     */
    @Query("""
            SELECT d.chartAcctSeqNo AS chartAcctSeqNo,
                   d.chartAcctCd    AS chartAcctCd,
                   d.tranAmt        AS tranAmt,
                   h.ccyCd          AS ccyCd,
                   h.outBankCd      AS outBankCd,
                   h.wdrlActNo      AS wdrlActNo,
                   h.regDtm         AS regDtm,
                   h.apprSeqNo      AS apprSeqNo
              FROM ScdulPymnSumrDetl d
              JOIN ScdulPymnSumr h
                ON h.bizKey = d.bizKey
               AND h.scdulPymnNo = d.scdulPymnNo
             WHERE d.bizKey = :bizKey
               AND d.chartAcctCd IS NOT NULL
               AND d.chartAcctCd <> ''
               AND SUBSTRING(h.regDtm, 1, 4) = :year
            """)
    List<ScdulPymnCashFlowRow> findCashFlowSourceRows(
            @Param("bizKey") String bizKey,
            @Param("year") String year);
}
