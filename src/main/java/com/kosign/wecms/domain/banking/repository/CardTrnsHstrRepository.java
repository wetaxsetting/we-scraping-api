package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.CardTrnsHstr;
import com.kosign.wecms.domain.banking.entity.CardTrnsHstrId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Read access to {@code CARD_TRNS_HSTR} for the Cash Flow report
 * aggregation. Each row contributes to the corporate-card source of
 * {@code get_cashflow_report()}'s {@code all_payment_data} UNION when
 * {@code chart_acct_cd} is non-empty AND the parent approval row's
 * status resolves to {@code 'C'} (Completed).
 *
 * <p>The repository is currently minimal — only the year-filtered fetch
 * used by the Phase 2 cash-flow aggregator. Other consumers (Card
 * Transactions list page, etc.) add their methods as they land.
 */
public interface CardTrnsHstrRepository extends JpaRepository<CardTrnsHstr, CardTrnsHstrId> {

    /**
     * All classified card transactions for the tenant in a calendar year.
     * Mirrors the function body's per-source predicate:
     * {@code WHERE biz_key = ? AND chart_acct_cd <> '' AND
     * EXTRACT(YEAR FROM TO_DATE(trsc_dt,'YYYYMMDD')) = ?}.
     *
     * <p>The year filter is applied as a string-prefix match
     * ({@code SUBSTRING(trsc_dt,1,4) = ?}) so the index on
     * {@code (biz_key, chart_acct_cd, trsc_dt)} is usable. The
     * approval-status gate runs in Java, not in this query.
     */
    @Query("""
            SELECT c FROM CardTrnsHstr c
             WHERE c.bizKey = :bizKey
               AND c.chartAcctCd IS NOT NULL
               AND c.chartAcctCd <> ''
               AND SUBSTRING(c.trscDt, 1, 4) = :year
            """)
    List<CardTrnsHstr> findClassifiedByBizKeyAndYear(
            @Param("bizKey") String bizKey,
            @Param("year") String year);
}
