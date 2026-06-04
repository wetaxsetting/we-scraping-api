package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.AcctMstrId;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Daily Cash <b>live recompute</b> source — refactor equivalent of legacy
 * {@code rept_0900_001_rec1} (account detail), replicated in SQL per the
 * "replicate the query in Java" branch of the wecms-api DB-function policy
 * (no new {@code CREATE FUNCTION} migration). Reads {@code ACCT_MSTR} +
 * {@code ACCT_TRNS_HSTR} at request time instead of the batch snapshot
 * {@code DAILY_CASH_REPT_DAILY_SUMR_DETL}.
 *
 * <p>Legacy parity notes (see {@code rept_0900_001_rec1} on DEV):
 * <ul>
 *   <li>Returns every <b>active</b> account ({@code ACTV_YN='Y'}); loans
 *       ({@code 01}) only when {@code EXPI_DT >= refDate} — same guard.</li>
 *   <li>Balance = as-of-date balance: latest {@code ACCT_TRNS_HSTR} row with
 *       {@code TRSC_DT <= refDate} (ordered {@code TRSC_DT DESC, ACCT_SEQ_NO
 *       DESC}), falling back to {@code ACCT_MSTR.BAL}.</li>
 *   <li>Same-day debit/credit counts + amounts ({@code TRSC_DT = refDate})
 *       attach only to {@code 03/04/05/06} (the join predicate), exactly as
 *       legacy; other types get 0.</li>
 *   <li>{@code wb_get_amt(amt, ccy)} → inline {@code ROUND(amt, VND?0:2)}.</li>
 *   <li>{@code wb_get_bank_nm} dropped — bank name resolved in Java via
 *       {@code BankCdResolver}.</li>
 * </ul>
 * The currency filter mirrors the legacy action's dynamic-0
 * ({@code rept_0009_001_r001_act.jsp:107-131}): {@code ccyExact} pins one
 * currency; {@code ccyExclude} drops the tenant-local currency (FCY). Both
 * null = ALL.
 *
 * <p>Cash ({@code rept_0900_001_rec3/rec4}) is NOT ported — its source
 * {@code CASH_REPY_MSTR} is out of the refactor PROD active-table scope —
 * so {@code total_cash} stays 0 (handled in the service accumulator).
 */
public interface DailyCashRecomputeRepository extends Repository<AcctMstr, AcctMstrId> {

    @Query(value = """
            SELECT mstr.ccy_cd                                            AS "ccyCd",
                   mstr.acct_type_cd                                      AS "acctTypeCd",
                   mstr.bank_cd                                           AS "bankCd",
                   mstr.acct_no                                           AS "acctNo",
                   COALESCE(
                       (SELECT ROUND(h.bal, CASE WHEN mstr.ccy_cd = 'VND' THEN 0 ELSE 2 END)
                          FROM acct_trns_hstr h
                         WHERE h.trsc_dt <= :refDate
                           AND h.biz_key = mstr.biz_key
                           AND h.bank_cd = mstr.bank_cd
                           AND h.acct_no = mstr.acct_no
                           AND h.ccy_cd  = mstr.ccy_cd
                         ORDER BY h.trsc_dt DESC, CAST(h.acct_seq_no AS NUMERIC) DESC
                         LIMIT 1),
                       ROUND(COALESCE(mstr.bal, 0), CASE WHEN mstr.ccy_cd = 'VND' THEN 0 ELSE 2 END)
                   )                                                      AS "bal",
                   mstr.loan_nm                                           AS "loanNm",
                   mstr.expi_dt                                           AS "expiDt",
                   ROUND(COALESCE(mstr.agmt_amt, 0), CASE WHEN mstr.ccy_cd = 'VND' THEN 0 ELSE 2 END) AS "agmtAmt",
                   COALESCE(day.snd_cnt, 0)::numeric                      AS "sndCnt",
                   COALESCE(day.snd_amt, 0)::numeric                      AS "sndAmt",
                   COALESCE(day.rcv_cnt, 0)::numeric                      AS "rcvCnt",
                   COALESCE(day.rcv_amt, 0)::numeric                      AS "rcvAmt"
              FROM acct_mstr mstr
              LEFT JOIN (
                   SELECT biz_key, ccy_cd, bank_cd, acct_no,
                          SUM(CASE WHEN inout_dv = '2' THEN 1 ELSE 0 END)                                                       AS snd_cnt,
                          SUM(CASE WHEN inout_dv = '2' THEN ROUND(trsc_amt, CASE WHEN ccy_cd = 'VND' THEN 0 ELSE 2 END) ELSE 0 END) AS snd_amt,
                          SUM(CASE WHEN inout_dv = '1' THEN 1 ELSE 0 END)                                                       AS rcv_cnt,
                          SUM(CASE WHEN inout_dv = '1' THEN ROUND(trsc_amt, CASE WHEN ccy_cd = 'VND' THEN 0 ELSE 2 END) ELSE 0 END) AS rcv_amt
                     FROM acct_trns_hstr
                    WHERE trsc_dt = :refDate
                      AND biz_key = :bizKey
                    GROUP BY biz_key, ccy_cd, bank_cd, acct_no
              ) day
                ON mstr.biz_key = day.biz_key
               AND mstr.ccy_cd  = day.ccy_cd
               AND mstr.bank_cd = day.bank_cd
               AND mstr.acct_no = day.acct_no
               AND mstr.acct_type_cd IN ('03', '04', '05', '06')
             WHERE mstr.biz_key = :bizKey
               AND mstr.actv_yn = 'Y'
               AND (CASE WHEN mstr.acct_type_cd = '01' THEN (mstr.expi_dt >= :refDate) ELSE TRUE END)
               AND (CAST(:ccyExact AS varchar) IS NULL OR mstr.ccy_cd = :ccyExact)
               AND (CAST(:ccyExclude AS varchar) IS NULL OR mstr.ccy_cd <> :ccyExclude)
             ORDER BY mstr.acct_type_cd ASC, mstr.bank_cd ASC, mstr.acct_no ASC
            """, nativeQuery = true)
    List<DailyCashAccountProjection> findAccountsAsOf(
            @Param("bizKey") String bizKey,
            @Param("refDate") String refDate,
            @Param("ccyExact") String ccyExact,
            @Param("ccyExclude") String ccyExclude);
}
