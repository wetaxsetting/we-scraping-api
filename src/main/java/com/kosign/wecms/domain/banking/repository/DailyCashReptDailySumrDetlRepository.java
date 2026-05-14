package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.DailyCashReptDailySumrDetl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DailyCashReptDailySumrDetlRepository
        extends JpaRepository<DailyCashReptDailySumrDetl, DailyCashReptDailySumrDetl.Pk> {

    /**
     * Per-account detail rows for one tenant + ref_date, optionally
     * filtered by currency. {@code currencyFilter} semantics:
     * <ul>
     *   <li>{@code null} — no filter (ALL)</li>
     *   <li>{@code "FCY"} — every currency except the local one
     *       (caller passes the local currency via {@code localCcy})</li>
     *   <li>any other value — exact match</li>
     * </ul>
     */
    @Query("""
            SELECT d FROM DailyCashReptDailySumrDetl d
             WHERE d.bizKey = :bizKey
               AND d.refDate = :refDate
               AND ( :ccyExact IS NULL OR d.ccyCd = :ccyExact )
               AND ( :ccyExclude IS NULL OR d.ccyCd <> :ccyExclude )
             ORDER BY d.ccyCd ASC, d.acctTypeCd ASC, d.bankCd ASC, d.acctNo ASC
            """)
    List<DailyCashReptDailySumrDetl> findRows(
            @Param("bizKey") String bizKey,
            @Param("refDate") String refDate,
            @Param("ccyExact") String ccyExact,
            @Param("ccyExclude") String ccyExclude);
}
