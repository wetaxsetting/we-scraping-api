package com.kosign.wecms.domain.payment.overseas.repository;

import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link OvrsPymnSumr}.
 * Used by the overseas-payment Excel upload submit flow to persist
 * one summary row per uploaded batch.
 */
public interface OvrsPymnSumrRepository extends JpaRepository<OvrsPymnSumr, OvrsPymnSumrId> {

    Optional<OvrsPymnSumr> findByBizKeyAndOvrsPymnNo(String bizKey, String ovrsPymnNo);

    // APPR_SEQ_NO is now allocated through the single
    // LegacyFunctionRepository.nextApprSeqNo funnel (V140 #1 — the GLOBAL
    // sq_appr_seq_no sequence shared across all Payment modules), replacing the
    // former per-tenant MAX+1 finder.

    /** Look up the batch row that owns the given approval sequence number.
     *  Used as a fallback when the caller supplies a DOC_NO instead of
     *  an OVRS_PYMN_NO — the service resolves DOC_NO → APPR_SEQ_NO via
     *  EXPT_RESOL_MSTR, then calls this to get the OVRS_PYMN_SUMR row. */
    Optional<OvrsPymnSumr> findByBizKeyAndApprSeqNo(String bizKey, String apprSeqNo);

    /**
     * HAENGSUNG Oracle Refresh import — dedup lookup by the ERP batch key
     * {@code (BIZ_KEY, ERP_FILE_NUM, ERP_FILE_DATE)} for Overseas (FILE_GB='AF1')
     * batches. Returns the existing draft summary so the import updates it
     * rather than inserting a duplicate (see {@code haengsung-payment-refresh}
     * §6.4). */
    Optional<OvrsPymnSumr> findFirstByBizKeyAndErpFileNumAndErpFileDate(
            String bizKey, String erpFileNum, String erpFileDate);

    /**
     * HAENGSUNG Oracle dispatch-result write-back (Step 5) — resolve the overseas
     * batch(es) bound to a just-finalized firm-banking pay-file by its shadow
     * keys {@code (BIZ_KEY, PAY_TRAN_DT, PAY_FILE_SEQ_NO)}. The write-back filters
     * to ERP batches by {@code ERP_FILE_*} afterwards.
     */
    List<OvrsPymnSumr> findByBizKeyAndPayTranDtAndPayFileSeqNo(
            String bizKey, String payTranDt, String payFileSeqNo);

    /**
     * HAENGSUNG Oracle dispatch-result write-back (Step 5) — mark the overseas
     * batch's ERP result as pushed ({@code ERP_PROC_CMPL_YN='Y'}). Called ONLY
     * after the Oracle write-back succeeds (legacy {@code ERP_0100_004}
     * OverseaProcess STEP 5). Own transaction — an Oracle failure leaves the flag
     * at {@code 'N'} for retry.
     */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("""
            UPDATE OvrsPymnSumr s
               SET s.erpProcCmplYn = 'Y',
                   s.modId         = :modId,
                   s.modDtm        = :modDtm
             WHERE s.bizKey      = :bizKey
               AND s.ovrsPymnNo  = :ovrsPymnNo
            """)
    int markErpProcComplete(@Param("bizKey")     String bizKey,
                            @Param("ovrsPymnNo") String ovrsPymnNo,
                            @Param("modId")      String modId,
                            @Param("modDtm")     String modDtm);

    /** Batch-fetch (apprSeqNo → ccyCd) pairs for the Approval Box list.
     *  Returns only rows whose apprSeqNo is in the given set so the
     *  caller can build a lookup map without an N+1 query. */
    @Query("SELECT ops.apprSeqNo, ops.ccyCd FROM OvrsPymnSumr ops " +
           "WHERE ops.bizKey = :bizKey AND ops.apprSeqNo IN :apprSeqNos")
    List<Object[]> findCcyCdByBizKeyAndApprSeqNos(
            @Param("bizKey")      String bizKey,
            @Param("apprSeqNos") Collection<String> apprSeqNos);

    /** Batch-fetch (apprSeqNo → ORG_FILE_NM) pairs for the Approval Box
     *  "File Name" column — the OVRS half of the refactor source
     *  {@code COALESCE(SCDUL.ORG_FILE_NM, OVRS.ORG_FILE_NM)} (the refactor
     *  stores the uploaded file name in {@code ORG_FILE_NM}, not the
     *  legacy {@code TITL} which now holds the typed title). Applied with
     *  lower precedence than SCDUL in the service merge. {@code ORG_FILE_NM}
     *  is null for overseas batches whose submit path does not stamp it —
     *  the column then renders blank (per spec). */
    @Query("SELECT ops.apprSeqNo, ops.orgFileNm FROM OvrsPymnSumr ops " +
           "WHERE ops.bizKey = :bizKey AND ops.apprSeqNo IN :apprSeqNos")
    List<Object[]> findOrgFileNmByBizKeyAndApprSeqNos(
            @Param("bizKey")      String bizKey,
            @Param("apprSeqNos") Collection<String> apprSeqNos);

    // updateApprStts removed 2026-06-10: OVRS_PYMN_SUMR has no APPR_STTS column
    // in the legacy JEX schema (same as SCDUL_PYMN_SUMR). The Approval Box no
    // longer mirrors status onto the payment batch — the Overseas list/detail
    // derives it on read from EXPT_RESOL_MSTR.APPR_TYPE_CD
    // (OverseasPaymentService.mapApprTypeCd over the joined approval master).

    /** Clear the approval stamp on the overseas payment batch when its
     *  Approval Box request is withdrawn — nulls {@code APPR_SEQ_NO} so the row
     *  returns to Draft. Called by {@code ApprovalBoxService} after
     *  {@code ApprovalBoxFanoutService.withdrawApproval} deletes the master.
     *  Locator is (bizKey, apprSeqNo). */
    @Modifying
    @Query("""
            UPDATE OvrsPymnSumr s
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
