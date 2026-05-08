package com.kosign.wecms.domain.operation.repository;

import com.kosign.wecms.domain.operation.entity.OprtSttsInfo;
import com.kosign.wecms.domain.operation.entity.OprtSttsInfoId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OprtSttsInfoRepository extends JpaRepository<OprtSttsInfo, OprtSttsInfoId> {

    /**
     * Server-side filter + pagination for Operation Status Inquiry list /
     * export. Optional predicates use the {@code (:param IS NULL OR …)}
     * pattern so a single JPQL query handles every filter combination
     * without a Specification graph.
     *
     * <p>Always constrains on {@code BIZ_KEY} (tenant). No other
     * baseline filter applies — unlike Sales e-Invoices, the legacy
     * {@code aosv_0001_001_r001_act.jsp} does NOT hide drafts /
     * hand-registered / empty-detail rows on this page (the table is
     * GDT-sourced and every row is meant to be visible).
     *
     * <ul>
     *   <li>{@code companyStatusPattern} — pre-lowercased,
     *       pre-{@code %}-wrapped LIKE pattern matched against the raw
     *       {@code COMPANYSTATUS} (the GDT free-text Vietnamese string).
     *       Mirrors legacy {@code companystatus ILIKE '%X%'} at line 50
     *       of {@code aosv_0001_001_r001_act.jsp}. {@code null} skips.
     *   <li>{@code keywordPattern} — pre-lowercased,
     *       pre-{@code %}-wrapped LIKE pattern OR-matched against
     *       {@code COMPANYNAME} and {@code TAXCODE} (legacy fixed
     *       2-column whitelist at lines 54-61 of the action JSP).
     *       {@code null} skips.
     * </ul>
     *
     * <p>Sort + pagination flow through {@link Pageable} — pass
     * {@link Pageable#unpaged(org.springframework.data.domain.Sort)}
     * for the export path so the full filtered set is returned in one
     * round trip.
     *
     * <p>Both pattern parameters use {@code ESCAPE '!'} so {@code %} /
     * {@code _} / {@code !} in user input are neutralised; the service
     * (see {@code OperationStatusService#buildLikePattern}) does the
     * escape-character insertion before binding.
     */
    @Query("""
            SELECT o FROM OprtSttsInfo o
             WHERE o.bizKey = :bizKey
               AND (:companyStatusPattern IS NULL
                    OR LOWER(COALESCE(o.companystatus, '')) LIKE :companyStatusPattern ESCAPE '!')
               AND (:keywordPattern IS NULL
                    OR LOWER(COALESCE(o.companyname, '')) LIKE :keywordPattern ESCAPE '!'
                    OR LOWER(COALESCE(o.taxcode,     '')) LIKE :keywordPattern ESCAPE '!')
            """)
    Page<OprtSttsInfo> findFiltered(
            @Param("bizKey")               String bizKey,
            @Param("companyStatusPattern") String companyStatusPattern,
            @Param("keywordPattern")       String keywordPattern,
            Pageable pageable);
}
