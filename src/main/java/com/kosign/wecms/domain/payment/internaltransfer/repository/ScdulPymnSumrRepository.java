package com.kosign.wecms.domain.payment.internaltransfer.repository;

import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
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
     * Fallback detail read — single row by tenant + approval sequence number +
     * payment type. Used when the caller supplies a DOC_NO (VO_YYMMDD_NNN)
     * instead of a SCDUL_PYMN_NO: the service resolves DOC_NO → APPR_SEQ_NO
     * via EXPT_RESOL_MSTR, then calls this to get the SCDUL_PYMN_SUMR row.
     * Type discriminator keeps Vendor / Expense / Payroll isolated even though
     * the APPR_SEQ_NO namespace is shared.
     */
    Optional<ScdulPymnSumr> findByBizKeyAndApprSeqNoAndScdulPymnTypeCd(
            String bizKey, String apprSeqNo, String scdulPymnTypeCd);

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
    /**
     * Filter dispatch table for the legacy {@code APPR_TYPE_CD}
     * codes surfaced by the Approval Status dropdown on every
     * Scheduled Payment list (Vendor / Expense / Payroll /
     * Overseas / Internal Transfer). Mirrors the legacy
     * {@code wabooks.enums.ApprSts} mapping used by
     * {@code comm_0195_001} and the frontend's
     * {@code deriveStatus} helper:
     *
     * <p>Approval status is derived by calling the legacy DB function
     * {@code wb_get_appr_stts(...)} on the {@code (BIZ_KEY, APPR_SEQ_NO)}-joined
     * {@code EXPT_RESOL_MSTR} row, mirroring {@code IDO.SCDUL_PYMN_SUMR_R002.xml:68}
     * (binding {@code (ERM.BIZ_KEY, ERM.REG_ID, ERM.REG_ID, SPS.APPR_SEQ_NO,
     * ERM.APPR_TYPE_CD)}). {@code SCDUL_PYMN_SUMR} has no {@code APPR_STTS} column
     * in the legacy JEX schema, so the function — not a stored column — is the
     * source of truth. The function maps {@code '00'→P, '01'→C, '02'→R, '03'→I}
     * (admin/registrant branch; per-approver ELSE branch is unreachable for the
     * {@code _apprvr_id = _reg_id} binding). The tabs filter on that result:
     *
     * <ul>
     *   <li>{@code null} / blank → All (no filter).</li>
     *   <li>{@code "00"} Draft — never submitted ({@code APPR_SEQ_NO} null/blank).</li>
     *   <li>{@code "01"} Approved — status {@code 'C'} OR {@code TRNF_YN = 'Y'}
     *       (dispatched batches are implicitly approved).</li>
     *   <li>{@code "02"} Rejected — status {@code 'R'}.</li>
     *   <li>{@code "03"} In Progress — status {@code 'I'}.</li>
     *   <li>{@code "04"} Pending — status {@code 'P'}.</li>
     * </ul>
     *
     * <p>Other / unknown codes pass-through as no-op (the OR
     * chain falls through and the whole {@code apprType} clause
     * evaluates true). Same defensive degradation Spring uses
     * elsewhere for unmapped enum values.
     */
    @Query("""
            SELECT s FROM ScdulPymnSumr s
            LEFT JOIN ExptResolMstr erm
                   ON erm.bizKey = s.bizKey AND erm.apprSeqNo = s.apprSeqNo
            WHERE s.bizKey             = :bizKey
              AND s.scdulPymnTypeCd    = :scdulPymnTypeCd
              AND s.regDtm BETWEEN :fromDtm AND :toDtm
              AND (:srchWdPattern IS NULL
                   OR LOWER(s.titl) LIKE :srchWdPattern
                   OR LOWER(COALESCE(s.apprSeqNo, '')) LIKE :srchWdPattern)
              AND (:trnfYn IS NULL OR s.trnfYn = :trnfYn)
              AND (
                    :apprType IS NULL
                 OR :apprType = ''
                 OR (:apprType = '00' AND (s.apprSeqNo IS NULL OR s.apprSeqNo = ''))
                 OR (:apprType = '01' AND (s.trnfYn = 'Y'
                        OR FUNCTION('wb_get_appr_stts', erm.bizKey, erm.regId, erm.regId,
                                    s.apprSeqNo, erm.apprTypeCd) = 'C'))
                 OR (:apprType = '02'
                        AND FUNCTION('wb_get_appr_stts', erm.bizKey, erm.regId, erm.regId,
                                     s.apprSeqNo, erm.apprTypeCd) = 'R')
                 OR (:apprType = '03'
                        AND FUNCTION('wb_get_appr_stts', erm.bizKey, erm.regId, erm.regId,
                                     s.apprSeqNo, erm.apprTypeCd) = 'I')
                 OR (:apprType = '04'
                        AND FUNCTION('wb_get_appr_stts', erm.bizKey, erm.regId, erm.regId,
                                     s.apprSeqNo, erm.apprTypeCd) = 'P')
                 OR :apprType NOT IN ('00','01','02','03','04')
              )
            """)
    Page<ScdulPymnSumr> search(
            @Param("bizKey")           String bizKey,
            @Param("scdulPymnTypeCd")  String scdulPymnTypeCd,
            @Param("fromDtm")          String fromDtm,
            @Param("toDtm")            String toDtm,
            @Param("srchWdPattern")    String srchWdPattern,
            @Param("trnfYn")           String trnfYn,
            @Param("apprType")         String apprType,
            Pageable pageable);

    /**
     * Same shape as {@link #search} but scoped to dispatched batches —
     * the Result list. Uses {@code trnf_yn = 'Y'} as the dispatched flag,
     * matching the legacy marker and the corrected Overseas Result query
     * ({@code OverseasPaymentRepository.buildResultSql}). Un-dispatched
     * batches still in the approval flow carry {@code trnf_yn} NULL/<>'Y'
     * and are excluded.
     *
     * <p>History: this previously gated on {@code trnf_dtm IS NOT NULL},
     * but {@code TRNF_DTM} is written only by the runtime dispatch path
     * ({@code InternalTransferDispatchService.dispatch} and the per-module
     * dispatch flows) and is never populated by demo seeds — so every
     * seeded {@code TRNF_YN='Y'} batch was silently filtered out and the
     * Result lists read empty. {@code trnf_yn = 'Y'} restores parity with
     * Overseas and the legacy behaviour.
     */
    @Query("""
            SELECT s FROM ScdulPymnSumr s
            WHERE s.bizKey             = :bizKey
              AND s.scdulPymnTypeCd    = :scdulPymnTypeCd
              AND s.regDtm BETWEEN :fromDtm AND :toDtm
              AND s.trnfYn = 'Y'
              AND (:srchWdPattern IS NULL
                   OR LOWER(s.titl) LIKE :srchWdPattern
                   OR LOWER(COALESCE(s.apprSeqNo, '')) LIKE :srchWdPattern)
              AND (:trnfYn IS NULL OR s.trnfYn = :trnfYn)
            """)
    Page<ScdulPymnSumr> searchDispatched(
            @Param("bizKey")           String bizKey,
            @Param("scdulPymnTypeCd")  String scdulPymnTypeCd,
            @Param("fromDtm")          String fromDtm,
            @Param("toDtm")            String toDtm,
            @Param("srchWdPattern")    String srchWdPattern,
            @Param("trnfYn")           String trnfYn,
            Pageable pageable);

    // APPR_SEQ_NO is now allocated through the single
    // LegacyFunctionRepository.nextApprSeqNo funnel (V140 #1 — the GLOBAL
    // sq_appr_seq_no sequence shared across all Payment modules), replacing the
    // former per-tenant cross-table MAX+1 finder.

    /** Batch-fetch (apprSeqNo → ccyCd) pairs for the Approval Box list.
     *  Mirrors the equivalent query on {@code OvrsPymnSumrRepository} — both
     *  are merged by {@code ApprovalBoxService} so the correct amount column
     *  is picked regardless of payment type. */
    @Query("SELECT s.apprSeqNo, s.ccyCd FROM ScdulPymnSumr s " +
           "WHERE s.bizKey = :bizKey AND s.apprSeqNo IN :apprSeqNos")
    List<Object[]> findCcyCdByBizKeyAndApprSeqNos(
            @Param("bizKey")      String bizKey,
            @Param("apprSeqNos") Collection<String> apprSeqNos);

    /** Batch-fetch (apprSeqNo → ORG_FILE_NM) pairs for the Approval Box
     *  "File Name" column. Legacy {@code IDO.EXPT_RESOL_MSTR_R003.xml:146}
     *  reads {@code COALESCE(SCDUL_PYMN_SUMR.TITL, OVRS_PYMN_SUMR.TITL)}
     *  because legacy stored the uploaded file name in {@code TITL}; the
     *  refactor moved the file name to {@code ORG_FILE_NM} (TITL now holds
     *  the typed document title — see
     *  {@code InternalTransferService.requestApproval}), so the refactor
     *  equivalent is {@code COALESCE(SCDUL.ORG_FILE_NM, OVRS.ORG_FILE_NM)}.
     *  Matches the {@code orgFileNm} source already used by the Scheduled
     *  Transfer / Transfer Result list pages. The service merges this with
     *  the OVRS variant (SCDUL takes precedence). Separate from the CCY_CD
     *  fetch so the proven currency resolution is untouched. */
    @Query("SELECT s.apprSeqNo, s.orgFileNm FROM ScdulPymnSumr s " +
           "WHERE s.bizKey = :bizKey AND s.apprSeqNo IN :apprSeqNos")
    List<Object[]> findOrgFileNmByBizKeyAndApprSeqNos(
            @Param("bizKey")      String bizKey,
            @Param("apprSeqNos") Collection<String> apprSeqNos);

    // updateApprStts removed 2026-06-10: SCDUL_PYMN_SUMR has no APPR_STTS column
    // in the legacy JEX schema. The Approval Box no longer mirrors status onto the
    // payment batch — status is derived on read via the legacy wb_get_appr_stts(...)
    // function (LegacyFunctionRepository.getApprStts / apprSttsByApprSeqNos).

    /** Clear the approval stamp on the payment batch when its Approval Box
     *  request is withdrawn — nulls {@code APPR_SEQ_NO} so the row returns to
     *  Draft ({@code apprSeqNo IS NULL} → tab '00' in {@link #search}). Called
     *  by {@code ApprovalBoxService.withdraw} after
     *  {@code ApprovalBoxFanoutService.withdrawApproval} deletes the master.
     *  Locator is (bizKey, apprSeqNo). */
    @Modifying
    @Query("""
            UPDATE ScdulPymnSumr s
               SET s.apprSeqNo = null,
                   s.modId     = :modId,
                   s.modDtm    = :modDtm
             WHERE s.bizKey      = :bizKey
               AND s.apprSeqNo   = :apprSeqNo
            """)
    int clearApprSeqNo(@Param("bizKey")    String bizKey,
                       @Param("apprSeqNo") String apprSeqNo,
                       @Param("modId")     String modId,
                       @Param("modDtm")    String modDtm);
}
