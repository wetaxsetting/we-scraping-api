package com.kosign.wecms.domain.vatdeclaration.repository;

import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclaration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Tenant-scoped access to {@code VAT_DECL}. Every method constrains
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

    /**
     * Any declaration strictly BEFORE this period, same period type.
     * Lock uses it to tell "first period ever" (nothing earlier — locking is
     * fine) from "a hole in the chain" (K-1 is missing but older periods exist).
     */
    @Query("""
            select count(d) > 0 from VatDeclaration d
            where d.bizKey = :bizKey
              and d.delYn = 'N'
              and d.periodType = :periodType
              and (d.periodYear < :year
                   or (d.periodYear = :year and d.periodValue < :value))
            """)
    boolean existsEarlierPeriod(@Param("bizKey") String bizKey,
                                @Param("periodType") String periodType,
                                @Param("year") Integer year,
                                @Param("value") Integer value);

    /**
     * Any declaration strictly AFTER this period, whatever its status.
     * Delete uses it (OQ-VAT-6): removing K would leave K+1 carrying a [22] from
     * a period that no longer exists, and K+1 could then never be locked because
     * the chain check would see a hole.
     */
    @Query("""
            select count(d) > 0 from VatDeclaration d
            where d.bizKey = :bizKey
              and d.delYn = 'N'
              and d.periodType = :periodType
              and (d.periodYear > :year
                   or (d.periodYear = :year and d.periodValue > :value))
            """)
    boolean existsLaterPeriod(@Param("bizKey") String bizKey,
                              @Param("periodType") String periodType,
                              @Param("year") Integer year,
                              @Param("value") Integer value);

    /**
     * Any LOCKED declaration strictly AFTER this period, same period type.
     * Unlock uses it: re-opening K invalidates the carry-forward of every later
     * period, so no later period may already be closed — not just K+1.
     */
    @Query("""
            select count(d) > 0 from VatDeclaration d
            where d.bizKey = :bizKey
              and d.delYn = 'N'
              and d.periodType = :periodType
              and d.status = :lockedStatus
              and (d.periodYear > :year
                   or (d.periodYear = :year and d.periodValue > :value))
            """)
    boolean existsLaterLockedPeriod(@Param("bizKey") String bizKey,
                                    @Param("periodType") String periodType,
                                    @Param("year") Integer year,
                                    @Param("value") Integer value,
                                    @Param("lockedStatus") String lockedStatus);

    /**
     * Which period types the tenant has already used in one calendar year.
     *
     * <p>Monthly and quarterly filing may not be mixed inside a year (Điều 9
     * Nghị định 126/2020 — the choice is fixed for the calendar year), and the
     * overlap is not merely formal: Q2 covers April–June, so a tenant that has
     * already declared and closed those three months would be declaring the
     * same revenue twice.
     *
     * <p>Returns a list rather than one value so pre-existing mixed data is
     * visible instead of collapsing into an arbitrary pick.
     */
    @Query("""
            select distinct d.periodType from VatDeclaration d
            where d.bizKey = :bizKey
              and d.delYn = 'N'
              and d.periodYear = :year
            """)
    List<String> periodTypesInYear(@Param("bizKey") String bizKey,
                                   @Param("year") Integer year);

    /**
     * The declaration immediately preceding a period in TIME, whatever its type.
     *
     * <p>Ordered by {@code TO_DT} because month and quarter numbers are not
     * comparable — M12 and Q4 are both "12" and "4" in different scales, so only
     * the date range orders them. Used at the one point where the type-scoped
     * lookups cannot see the predecessor: the first period after a legal
     * year-boundary switch from monthly to quarterly (or back), where [43] must
     * still carry into [22] or the tenant silently loses deductible credit.
     *
     * <p>Supplementary filings ({@code SUPPL_NO > 0}) are excluded — the
     * carry-forward chain runs through original declarations.
     */
    @Query("""
            select d from VatDeclaration d
            where d.bizKey = :bizKey
              and d.delYn = 'N'
              and d.supplNo = 0
              and d.toDt < :periodStart
            order by d.toDt desc
            """)
    List<VatDeclaration> findPrecedingByDate(@Param("bizKey") String bizKey,
                                             @Param("periodStart") LocalDate periodStart,
                                             Pageable pageable);
}
