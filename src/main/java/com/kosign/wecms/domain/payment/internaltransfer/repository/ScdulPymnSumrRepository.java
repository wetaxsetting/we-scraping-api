package com.kosign.wecms.domain.payment.internaltransfer.repository;

import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link ScdulPymnSumr}. Every query is
 * tenant-scoped — callers pass {@code bizKey} from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()};
 * a method without a {@code bizKey} parameter does not exist on this
 * interface, so cross-tenant leakage is impossible by construction.
 *
 * <p>List queries filter on {@code SCDUL_PYMN_TYPE_CD = '03'} so the
 * Internal Transfer endpoints only see their own batches (other Payment
 * modules share the same physical table — see legacy
 * {@code PymnHelper.pymn_0001_001_r001} which is parameterised by
 * payment type).
 *
 * <p>Date filters expect {@code yyyyMMddHHmmss}-shaped {@code REG_DTM}
 * values — same shape the legacy IDOs use. The Service layer converts
 * the wire {@code yyyyMMdd} date filters to {@code yyyyMMdd000000} /
 * {@code yyyyMMdd235959} bounds.
 */
public interface ScdulPymnSumrRepository extends JpaRepository<ScdulPymnSumr, ScdulPymnSumrId> {

    /**
     * Detail read — single row by composite PK + tenant + payment type.
     * Type-filtering at the repository layer prevents Internal Transfer
     * endpoints from accidentally returning a Vendor / Expense batch
     * that happens to share a generated SCDUL_PYMN_NO.
     */
    Optional<ScdulPymnSumr> findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(
            String bizKey, String scdulPymnNo, String scdulPymnTypeCd);

    /**
     * List read — paged, filtered by tenant + payment type + date range
     * (inclusive on REG_DTM) + optional title / doc-no LIKE + optional
     * {@code TRNF_YN} (used by the Transfer Result list to drop drafts).
     *
     * <p>Reason for JPQL over a derived method name: the derived form
     * would be 30+ characters and harder to read than the explicit JPQL.
     * Tenant + type are non-optional in every variant.
     */
    /**
     * <p>{@code srchWdPattern} is the pre-built lowercase {@code "%foo%"}
     * LIKE pattern (or {@code null} to skip the predicate). Building
     * the pattern in the service rather than inside the JPQL avoids a
     * PostgreSQL {@code function lower(bytea) does not exist} error
     * that fires when a null {@code :srchWd} parameter inside
     * {@code CONCAT(...)} leaves the param type un-inferable — the
     * driver then sends it as {@code bytea}. Mirrors the precedent
     * established by {@code AcctMstrRepository#findFiltered}.
     */
    @Query("""
            SELECT s FROM ScdulPymnSumr s
            WHERE s.bizKey             = :bizKey
              AND s.scdulPymnTypeCd    = :scdulPymnTypeCd
              AND s.regDtm BETWEEN :fromDtm AND :toDtm
              AND (:srchWdPattern IS NULL
                   OR LOWER(s.titl) LIKE :srchWdPattern
                   OR LOWER(COALESCE(s.apprSeqNo, '')) LIKE :srchWdPattern)
              AND (:trnfYn IS NULL OR s.trnfYn = :trnfYn)
            """)
    Page<ScdulPymnSumr> search(
            @Param("bizKey")           String bizKey,
            @Param("scdulPymnTypeCd")  String scdulPymnTypeCd,
            @Param("fromDtm")          String fromDtm,
            @Param("toDtm")            String toDtm,
            @Param("srchWdPattern")    String srchWdPattern,
            @Param("trnfYn")           String trnfYn,
            Pageable pageable);
}
