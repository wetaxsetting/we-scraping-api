package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.AcctMstrId;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * HAENGSUNG Daily Financial ledger source — the <b>single funnel</b> for the
 * legacy set-returning DB function
 * {@code fn_get_haengsung_daily_financial_report(p_biz_key, p_as_of_date,
 * p_account_filter)} (legacy IDO {@code DAILY_FINCL_REPT_R001} →
 * {@code rept_0017_001_r001_act.jsp}).
 *
 * <p>Per the wecms-api "Legacy report DB functions" policy this is the
 * Sprint-2 "port the function to local Flyway + swap the stub call site to a
 * single funnel method, never via {@code @Query} scattered across repos"
 * branch. The function + its base table {@code REPT_TRNS_HSTR} were added to
 * the refactor DB out-of-band (PO-approved baseline addition); this repository
 * is the only executable call site.
 *
 * <p>The function already orders its output
 * ({@code account_type_order, bank_name_sort, account_number, transaction_seq});
 * the outer {@code ORDER BY} re-asserts it so row order is deterministic
 * regardless of set-function materialization. Columns are aliased to
 * camelCase so the {@link DailyFinancialHsRow} interface projection binds
 * (same convention as {@link DailyCashRecomputeRepository}).
 *
 * <p>{@code p_account_filter} is {@code 'ALL'} or a JSON array of
 * {@code {"BANK_CD":..,"ACCT_NO":..}} objects (verbatim legacy
 * {@code rept_0017_001.js} contract); the function validates the date as
 * {@code ^\d{8}$} (YYYYMMDD) and raises on a bad format.
 */
public interface DailyFinancialHsReportRepository extends Repository<AcctMstr, AcctMstrId> {

    @Query(value = """
            SELECT row_type               AS "rowType",
                   bank_account           AS "bankAccount",
                   currency               AS "currency",
                   rept_cd                AS "reptCd",
                   pymn_cd                AS "pymnCd",
                   description_english    AS "descriptionEnglish",
                   description_korean     AS "descriptionKorean",
                   customer_supplier      AS "customerSupplier",
                   before_balance         AS "beforeBalance",
                   receipt                AS "receipt",
                   payment                AS "payment",
                   balance                AS "balance",
                   balance_company_actual AS "balanceCompanyActual",
                   account_number         AS "accountNumber",
                   account_type_order     AS "accountTypeOrder",
                   acct_seq_no            AS "acctSeqNo",
                   transaction_date       AS "transactionDate",
                   transaction_time       AS "transactionTime",
                   transaction_seq        AS "transactionSeq",
                   memo                   AS "memo",
                   bank_cd_out            AS "bankCdOut",
                   bank_name_sort         AS "bankNameSort"
              FROM fn_get_haengsung_daily_financial_report(:bizKey, :periodDate, :accountFilter)
             ORDER BY account_type_order, bank_name_sort, account_number, transaction_seq
            """, nativeQuery = true)
    List<DailyFinancialHsRow> fetchReport(
            @Param("bizKey") String bizKey,
            @Param("periodDate") String periodDate,
            @Param("accountFilter") String accountFilter);
}
