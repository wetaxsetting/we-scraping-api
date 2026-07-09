package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.AcctMstrId;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cash Flow report source — the <b>single funnel</b> for the legacy DB
 * function {@code get_cashflow_report(p_biz_key, p_year, p_quarter,
 * p_base_currency, p_usd_rate, p_krw_rate, p_bank_filters)} (legacy IDO
 * {@code CASH_FLOW_R001} → {@code cash_0001_001_r001_act.jsp}).
 *
 * <p><b>Architecture decision (2026-06-13):</b> Cash Flow uses the legacy
 * DB function as the source of truth — the Java reimplementation
 * (PaymentDataAggregator / BalanceCalculator / FxConverter /
 * ChartOfAccountsHierarchy / CashFlowRowAssembler) is bypassed on the
 * active path to eliminate parity drift. The function (and its helpers
 * {@code wb_get_appr_stts} → {@code wb_get_admin_yn},
 * {@code wb_get_chart_acct_level_text} → {@code wb_get_chart_acct_level})
 * must be present in the target DB.
 *
 * <p>The function already orders its output row-type-major
 * ({@code sort_order}); {@code CashFlowReportService} re-assembles the rows
 * into the frontend's root-major order using {@code group_key} +
 * {@code parent_l2_code}. Columns are aliased camelCase so the
 * {@link CashFlowReportRow} interface projection binds (same convention as
 * {@link DailyFinancialHsReportRepository}).
 *
 * <p>The nullable parameters are explicitly {@code CAST} so Hibernate can
 * bind a Java {@code null} to the typed PL/pgSQL parameter without a
 * "could not determine type" error. {@code p_quarter} is always passed
 * {@code null} by the service (production parity — the live UI sends
 * {@code YEAR_QUAT=null} and hides out-of-quarter columns client-side);
 * {@code p_bank_filters} is a JSON array of {@code {"BANK_CD":..,"ACCT_NO":..}}
 * or {@code null}.
 */
public interface CashFlowReportRepository extends Repository<AcctMstr, AcctMstrId> {

    @Query(value = """
            SELECT sort_order        AS "sortOrder",
                   display_code      AS "displayCode",
                   display_name      AS "displayName",
                   item_code         AS "itemCode",
                   item_name         AS "itemName",
                   row_type          AS "rowType",
                   group_key         AS "groupKey",
                   parent_l2_code    AS "parentL2Code",
                   jan_amount        AS "janAmount",
                   feb_amount        AS "febAmount",
                   mar_amount        AS "marAmount",
                   apr_amount        AS "aprAmount",
                   may_amount        AS "mayAmount",
                   jun_amount        AS "junAmount",
                   jul_amount        AS "julAmount",
                   aug_amount        AS "augAmount",
                   sep_amount        AS "sepAmount",
                   oct_amount        AS "octAmount",
                   nov_amount        AS "novAmount",
                   dec_amount        AS "decAmount",
                   total_amount      AS "totalAmount",
                   display_currency  AS "displayCurrency"
              FROM get_cashflow_report(
                       :bizKey,
                       :year,
                       CAST(:quarter AS varchar),
                       :baseCurrency,
                       CAST(:usdRate AS numeric),
                       CAST(:krwRate AS numeric),
                       CAST(:bankFilters AS text))
            """, nativeQuery = true)
    List<CashFlowReportRow> fetchReport(
            @Param("bizKey") String bizKey,
            @Param("year") int year,
            @Param("quarter") String quarter,
            @Param("baseCurrency") String baseCurrency,
            @Param("usdRate") BigDecimal usdRate,
            @Param("krwRate") BigDecimal krwRate,
            @Param("bankFilters") String bankFilters);
}
