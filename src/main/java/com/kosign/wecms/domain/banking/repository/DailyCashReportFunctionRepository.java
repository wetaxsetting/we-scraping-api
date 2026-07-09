package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.AcctMstrId;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Daily Cash report — the <b>single funnel</b> for the four legacy
 * set-returning DB functions that back legacy WSVC {@code rept_0009_001_r001}:
 *
 * <ul>
 *   <li>{@link #rec1} — {@code rept_0900_001_rec1}: per-account detail</li>
 *   <li>{@link #rec2} — {@code rept_0900_001_rec2}: per-currency summary
 *       (bank-account buckets; no cash)</li>
 *   <li>{@link #rec3} — {@code rept_0900_001_rec3}: per-currency cash flow
 *       (opening / closing balance + same-day movements, from {@code CASH_REPY_MSTR})</li>
 *   <li>{@link #rec4} — {@code rept_0900_001_rec4}: per-currency total cash</li>
 * </ul>
 *
 * <p><b>Architecture decision:</b> the legacy DB functions are the source of
 * truth (same stance as Cash Flow and Daily Financial HS — see
 * {@link DailyFinancialHsReportRepository}). This replaces the prior Java
 * live-recompute ({@code DailyCashRecomputeRepository}) + Java account-type
 * bucketing ({@code DailyCashReportService.CurrencyAccumulator}); the bucketing
 * and cash math now live in the functions verbatim, eliminating refactor drift.
 *
 * <p><b>Currency filter is applied OUTSIDE the functions</b> (the functions
 * take no currency argument — legacy applies the filter on the WSVC action).
 * Each function returns {@code ccy_cd}, so the wrapping query filters the
 * function output: {@code ccyExact} pins one currency (exact match);
 * {@code ccyExclude} drops the tenant-local currency (FCY); both {@code null}
 * = ALL. {@code TRIM} guards the {@code bpchar} {@code ccy_cd} comparison.
 *
 * <p>{@code p_req_date2} mirrors {@code p_req_date} (the functions do not read
 * the second date today — the legacy signature is preserved for parity).
 *
 * <p>Columns are aliased to camelCase so the interface projections bind (same
 * convention as {@link DailyFinancialHsReportRepository} /
 * {@link DailyCashAccountProjection}). This repository is the only executable
 * call site for the four {@code rept_0900_001_rec*} functions.
 */
public interface DailyCashReportFunctionRepository extends Repository<AcctMstr, AcctMstrId> {

    /** Legacy {@code rept_0900_001_rec1} — account detail. */
    @Query(value = """
            SELECT f.biz_key      AS "bizKey",
                   f.ref_date     AS "refDate",
                   TRIM(f.ccy_cd) AS "ccyCd",
                   TRIM(f.acct_type_cd) AS "acctTypeCd",
                   f.bank_cd      AS "bankCd",
                   f.bank_nm      AS "bankNm",
                   f.acct_no      AS "acctNo",
                   f.bal          AS "bal",
                   f.loan_nm      AS "loanNm",
                   f.expi_dt      AS "expiDt",
                   f.agmt_amt     AS "agmtAmt",
                   f.snd_cnt      AS "sndCnt",
                   f.snd_amt      AS "sndAmt",
                   f.rcv_cnt      AS "rcvCnt",
                   f.rcv_amt      AS "rcvAmt"
              FROM rept_0900_001_rec1(:bizKey, :reqDate, :reqDate2) f
             WHERE (CAST(:ccyExact AS varchar)   IS NULL OR TRIM(f.ccy_cd) = :ccyExact)
               AND (CAST(:ccyExclude AS varchar) IS NULL OR TRIM(f.ccy_cd) <> :ccyExclude)
             ORDER BY f.acct_type_cd, f.bank_cd, f.acct_no
            """, nativeQuery = true)
    List<DailyCashRec1Row> rec1(
            @Param("bizKey") String bizKey,
            @Param("reqDate") String reqDate,
            @Param("reqDate2") String reqDate2,
            @Param("ccyExact") String ccyExact,
            @Param("ccyExclude") String ccyExclude);

    /** Legacy {@code rept_0900_001_rec2} — per-currency summary (account buckets). */
    @Query(value = """
            SELECT TRIM(f.ccy_cd) AS "ccyCd",
                   f.totl_norm_amt AS "totlNormAmt",
                   f.totl_sav_amt  AS "totlSavAmt",
                   f.totl_acct_amt AS "totlAcctAmt",
                   f.totl_loan_amt AS "totlLoanAmt"
              FROM rept_0900_001_rec2(:bizKey, :reqDate, :reqDate2) f
             WHERE (CAST(:ccyExact AS varchar)   IS NULL OR TRIM(f.ccy_cd) = :ccyExact)
               AND (CAST(:ccyExclude AS varchar) IS NULL OR TRIM(f.ccy_cd) <> :ccyExclude)
             ORDER BY f.ccy_cd
            """, nativeQuery = true)
    List<DailyCashRec2Row> rec2(
            @Param("bizKey") String bizKey,
            @Param("reqDate") String reqDate,
            @Param("reqDate2") String reqDate2,
            @Param("ccyExact") String ccyExact,
            @Param("ccyExclude") String ccyExclude);

    /** Legacy {@code rept_0900_001_rec3} — per-currency cash flow / opening balance. */
    @Query(value = """
            SELECT TRIM(f.ccy_cd) AS "ccyCd",
                   f.bef_date_bal AS "befDateBal",
                   f.bal          AS "bal",
                   f.snd_cnt      AS "sndCnt",
                   f.snd_amt      AS "sndAmt",
                   f.rcv_cnt      AS "rcvCnt",
                   f.rcv_amt      AS "rcvAmt"
              FROM rept_0900_001_rec3(:bizKey, :reqDate, :reqDate2) f
             WHERE (CAST(:ccyExact AS varchar)   IS NULL OR TRIM(f.ccy_cd) = :ccyExact)
               AND (CAST(:ccyExclude AS varchar) IS NULL OR TRIM(f.ccy_cd) <> :ccyExclude)
             ORDER BY f.ccy_cd
            """, nativeQuery = true)
    List<DailyCashRec3Row> rec3(
            @Param("bizKey") String bizKey,
            @Param("reqDate") String reqDate,
            @Param("reqDate2") String reqDate2,
            @Param("ccyExact") String ccyExact,
            @Param("ccyExclude") String ccyExclude);

    /** Legacy {@code rept_0900_001_rec4} — per-currency total cash. */
    @Query(value = """
            SELECT TRIM(f.ccy_cd) AS "ccyCd",
                   f.totl_cash    AS "totlCash"
              FROM rept_0900_001_rec4(:bizKey, :reqDate, :reqDate2) f
             WHERE (CAST(:ccyExact AS varchar)   IS NULL OR TRIM(f.ccy_cd) = :ccyExact)
               AND (CAST(:ccyExclude AS varchar) IS NULL OR TRIM(f.ccy_cd) <> :ccyExclude)
             ORDER BY f.ccy_cd
            """, nativeQuery = true)
    List<DailyCashRec4Row> rec4(
            @Param("bizKey") String bizKey,
            @Param("reqDate") String reqDate,
            @Param("reqDate2") String reqDate2,
            @Param("ccyExact") String ccyExact,
            @Param("ccyExclude") String ccyExclude);
}
