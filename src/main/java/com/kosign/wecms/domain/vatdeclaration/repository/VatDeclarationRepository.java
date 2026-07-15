package com.kosign.wecms.domain.vatdeclaration.repository;

import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclaration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Tenant-scoped access to {@code TAX_DECLARATIONS}. Every method constrains
 * on {@code BIZ_KEY} (tenant-scope rule) and excludes soft-deleted rows.
 */
public interface VatDeclarationRepository extends JpaRepository<VatDeclaration, Long> {

    /** Detail lookup — merges "not found" and "wrong tenant" into one empty result. */
    Optional<VatDeclaration> findBySidAndBizKeyAndDelYn(Long sid, String bizKey, String delYn);

    /**
     * Dashboard list — optional year/status filters, newest period first.
     * Pagination via the standard zero-based {@code page}/{@code size} contract.
     */
    @Query("""
            select d from VatDeclaration d
            where d.bizKey = :bizKey
              and d.delYn = 'N'
              and (:year is null or d.periodYear = :year)
              and (:status is null or d.status = :status)
            order by d.periodYear desc, d.periodValue desc, d.supplNo desc
            """)
    Page<VatDeclaration> search(@Param("bizKey") String bizKey,
                                @Param("year") Integer year,
                                @Param("status") String status,
                                Pageable pageable);

    /** Duplicate-period guard (create flow, WEB_VAT_CRE_MSG_001). */
    boolean existsByBizKeyAndPeriodTypeAndPeriodValueAndPeriodYearAndSupplNoAndDelYn(
            String bizKey, String periodType, Integer periodValue, Integer periodYear,
            Integer supplNo, String delYn);

    /** Exact-period lookup — e.g. resolve period K-1 for the ct22 carry-forward. */
    Optional<VatDeclaration> findByBizKeyAndPeriodTypeAndPeriodValueAndPeriodYearAndSupplNoAndDelYn(
            String bizKey, String periodType, Integer periodValue, Integer periodYear,
            Integer supplNo, String delYn);

    /** Latest supplement of a period (official L00 + supplements L01..n share the period). */
    Optional<VatDeclaration> findTopByBizKeyAndPeriodTypeAndPeriodValueAndPeriodYearAndDelYnOrderBySupplNoDesc(
            String bizKey, String periodType, Integer periodValue, Integer periodYear, String delYn);
}
