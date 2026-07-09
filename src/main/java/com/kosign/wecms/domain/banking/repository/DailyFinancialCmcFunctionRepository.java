package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.AcctMstrId;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Daily Financial CMC — the <b>single funnel</b> for the legacy read-only
 * set-returning function {@code rept_0015_001_r001_rec1(biz_key, req_date,
 * req_date2)} that backs legacy WSVC {@code rept_0015_001_r001}.
 *
 * <p><b>Architecture decision (2026-06-14):</b> the legacy DB function is the
 * source of truth (same stance as Cash Flow / Daily Cash / Daily Financial HS).
 * It replaces the prior Java live-recompute in {@code ReportService.dailyFinancial}.
 * The {@code DAILY_FINCL_REPT_*} summary tables and the {@code WB_INSERT_*}
 * recompute functions are deliberately NOT used — the function is what those
 * tables are materialised from, so reading it directly avoids both write-on-read
 * and intraday summary-table staleness.
 *
 * <p><b>Filters applied OUTSIDE the function</b> (the function takes no currency
 * or active-only argument — legacy applies them in the {@code _act.jsp} IDO
 * wrapper):
 * <ul>
 *   <li>Currency scope — {@code ccyExact} pins one currency; {@code ccyExclude}
 *       drops the tenant-local currency (FCY); both null = ALL.</li>
 *   <li>Active accounts (DF-3) — {@code actvFilter='Y'} keeps only active
 *       accounts (legacy {@code ACTV_ACCT_ONLY_YN='Y'}); null = include
 *       inactive. {@code ACTV_YN} is joined from {@code ACCT_MSTR} since the
 *       function does not return it.</li>
 * </ul>
 *
 * <p>{@code reqDate2} mirrors {@code reqDate} (the function body uses only the
 * first date; the legacy signature is preserved). Columns are aliased to
 * camelCase so {@link DailyFinancialCmcRec1Row} binds — same convention as
 * {@code DailyCashReportFunctionRepository}. This repository is the only
 * executable call site for {@code rept_0015_001_r001_rec1}.
 */
public interface DailyFinancialCmcFunctionRepository extends Repository<AcctMstr, AcctMstrId> {

    @Query(value = """
            SELECT f.biz_key       AS "bizKey",
                   f.ref_date      AS "refDate",
                   TRIM(f.ccy_cd)  AS "ccyCd",
                   TRIM(f.acct_type_cd) AS "acctTypeCd",
                   f.bank_cd       AS "bankCd",
                   f.bank_nm       AS "bankNm",
                   f.acct_no       AS "acctNo",
                   f.acct_nick_nm  AS "acctNickNm",
                   f.bal           AS "bal",
                   f.loan_nm       AS "loanNm",
                   f.expi_dt       AS "expiDt",
                   f.agmt_amt      AS "agmtAmt",
                   f.snd_cnt       AS "sndCnt",
                   f.snd_amt       AS "sndAmt",
                   f.rcv_cnt       AS "rcvCnt",
                   f.rcv_amt       AS "rcvAmt",
                   f.intl_rcv_amt  AS "intlRcvAmt",
                   f.intl_snd_amt  AS "intlSndAmt",
                   f.intl_rcv_cnt  AS "intlRcvCnt",
                   f.intl_snd_cnt  AS "intlSndCnt",
                   TRIM(m.actv_yn) AS "actvYn"
              FROM rept_0015_001_r001_rec1(:bizKey, :reqDate, :reqDate2) f
              JOIN acct_mstr m
                ON m.biz_key = f.biz_key
               AND m.bank_cd = f.bank_cd
               AND m.acct_no = f.acct_no
               AND m.ccy_cd  = f.ccy_cd
               AND m.acct_type_cd = f.acct_type_cd
             WHERE (CAST(:actvFilter AS varchar)  IS NULL OR m.actv_yn   = :actvFilter)
               AND (CAST(:ccyExact AS varchar)    IS NULL OR TRIM(f.ccy_cd) = :ccyExact)
               AND (CAST(:ccyExclude AS varchar)  IS NULL OR TRIM(f.ccy_cd) <> :ccyExclude)
             ORDER BY f.acct_type_cd, f.bank_cd, f.acct_no
            """, nativeQuery = true)
    List<DailyFinancialCmcRec1Row> rec1(
            @Param("bizKey") String bizKey,
            @Param("reqDate") String reqDate,
            @Param("reqDate2") String reqDate2,
            @Param("ccyExact") String ccyExact,
            @Param("ccyExclude") String ccyExclude,
            @Param("actvFilter") String actvFilter);
}
