package com.kosign.wecms.domain.banking.synchistory.repository;

import com.kosign.wecms.domain.banking.synchistory.entity.ExtTranCallHis;
import com.kosign.wecms.domain.banking.synchistory.entity.ExtTranCallHisId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ExtTranCallHisRepository extends JpaRepository<ExtTranCallHis, ExtTranCallHisId> {

    /**
     * Allocate the next {@code SEQ_NO} for the per-day audit-log
     * sequence. Legacy {@code wb_get_next_val('ext_tran_call_his',
     * 'seq_no', ref_date)} returns {@code MAX(SEQ_NO) + 1} scoped to
     * {@code (SVC_ID, REF_DATE)}. The PK is
     * {@code (SVC_ID, REF_DATE, SEQ_NO)} so the sequence naturally
     * resets each day. Returns {@code 0} when no rows exist for the
     * given day; caller bumps to {@code 1}.
     */
    @Query("SELECT COALESCE(MAX(h.seqNo), 0) FROM ExtTranCallHis h " +
           "WHERE h.svcId = :svcId AND h.refDate = :refDate")
    BigDecimal findMaxSeqNoForDay(@Param("svcId") String svcId,
                                  @Param("refDate") String refDate);

    /**
     * Sync-history list query. Mirrors legacy
     * {@code IDO.EXT_TRAN_CALL_HIS_R003} — JOINs {@code ACCT_MSTR} on
     * {@code (BIZ_KEY, BANK_CD, ACCT_NO)} to enrich each call row with
     * the matching account's nickname + type + currency for display.
     *
     * <p>Filter parameters mirror the legacy JEX inputs:
     * <ul>
     *   <li>{@code bizKey} / {@code userId} — required tenant + user
     *       scope (legacy hardcodes both).</li>
     *   <li>{@code fromDt} / {@code toDt} — required date window on
     *       {@code REF_DATE} ({@code yyyyMMdd}).</li>
     *   <li>{@code currency} — required scope ({@code "VND"} for the
     *       VND popup, {@code "FCY"} maps to NOT {@code "VND"} at the
     *       service layer; a concrete code like {@code "USD"} passes
     *       through verbatim).</li>
     *   <li>{@code bankCd} — optional bank filter; {@code null} means
     *       "All Banks".</li>
     *   <li>{@code success} — optional result filter; {@code null} =
     *       All, {@code TRUE} = success only ({@code RSLT_CD =
     *       '00000000'}), {@code FALSE} = failed only.</li>
     * </ul>
     *
     * <p>ORDER BY {@code REG_DTM DESC} matches legacy (newest scrape
     * first). Pagination intentionally not added — the legacy popup
     * loads everything in the window; volumes are bounded by the chip
     * date range. Adopt server-side paging when a real user complains.
     */
    @Query("SELECT new com.kosign.wecms.domain.banking.synchistory.repository.SyncHistoryProjection(" +
           "  h.refDate, h.seqNo, h.bankCd, h.acctNo, " +
           "  a.acctNickNm, a.acctTypeCd, a.ccyCd, " +
           "  h.startDate, h.endDate, h.rsltCd, h.errCont, h.regDtm) " +
           "FROM ExtTranCallHis h " +
           "JOIN com.kosign.wecms.domain.banking.entity.AcctMstr a " +
           "  ON a.bizKey = h.bizKey AND a.bankCd = h.bankCd AND a.acctNo = h.acctNo " +
           "WHERE h.svcId = 'REAL_TIME_ONLINE_INQUIRY' " +
           "  AND h.bizKey = :bizKey " +
           "  AND h.userId = :userId " +
           "  AND h.refDate BETWEEN :fromDt AND :toDt " +
           "  AND ( :ccyEq  IS NULL OR a.ccyCd =  :ccyEq  ) " +
           "  AND ( :ccyNot IS NULL OR a.ccyCd <> :ccyNot ) " +
           "  AND ( :bankCd IS NULL OR h.bankCd = :bankCd ) " +
           "  AND ( :successFilter IS NULL " +
           "        OR ( :successFilter = TRUE  AND h.rsltCd = '00000000' ) " +
           "        OR ( :successFilter = FALSE AND ( h.rsltCd IS NULL OR h.rsltCd <> '00000000' ) ) ) " +
           "ORDER BY h.regDtm DESC")
    List<SyncHistoryProjection> findSyncHistory(
            @Param("bizKey") String bizKey,
            @Param("userId") String userId,
            @Param("fromDt") String fromDt,
            @Param("toDt") String toDt,
            @Param("ccyEq") String ccyEq,
            @Param("ccyNot") String ccyNot,
            @Param("bankCd") String bankCd,
            @Param("successFilter") Boolean successFilter);
}
