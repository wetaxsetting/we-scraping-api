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

    /** Per-tenant MAX(apprSeqNo::bigint) for the request-approval sequence. */
    @Query(value = "SELECT MAX(CAST(appr_seq_no AS BIGINT)) FROM ovrs_pymn_sumr " +
                   "WHERE biz_key = :bizKey AND appr_seq_no IS NOT NULL AND appr_seq_no ~ '^[0-9]+$'",
           nativeQuery = true)
    Long findMaxApprSeqNoByBizKey(@Param("bizKey") String bizKey);

    /** Look up the batch row that owns the given approval sequence number.
     *  Used as a fallback when the caller supplies a DOC_NO instead of
     *  an OVRS_PYMN_NO — the service resolves DOC_NO → APPR_SEQ_NO via
     *  EXPT_RESOL_MSTR, then calls this to get the OVRS_PYMN_SUMR row. */
    Optional<OvrsPymnSumr> findByBizKeyAndApprSeqNo(String bizKey, String apprSeqNo);

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

    /** Write back the approval status from the Approval Box to the overseas
     *  payment batch row. Called by {@code ApprovalBoxService} after approve /
     *  reject so the Overseas Payment list page reflects the updated status. */
    @Modifying
    @Query("""
            UPDATE OvrsPymnSumr s
               SET s.apprStts = :apprStts,
                   s.modId    = :modId,
                   s.modDtm   = :modDtm
             WHERE s.bizKey      = :bizKey
               AND s.apprSeqNo   = :apprSeqNo
            """)
    int updateApprStts(@Param("bizKey")    String bizKey,
                       @Param("apprSeqNo") String apprSeqNo,
                       @Param("apprStts")  String apprStts,
                       @Param("modId")     String modId,
                       @Param("modDtm")    String modDtm);

    /** Clear the approval stamp on the overseas payment batch when its
     *  Approval Box request is withdrawn — nulls {@code APPR_SEQ_NO} +
     *  {@code APPR_STTS} so the row returns to Draft. Called by
     *  {@code ApprovalBoxService.withdraw} after
     *  {@code ApprovalBoxFanoutService.withdrawApproval} deletes the master.
     *  Locator is (bizKey, apprSeqNo) — same key the write-back uses. */
    @Modifying
    @Query("""
            UPDATE OvrsPymnSumr s
               SET s.apprSeqNo = null,
                   s.apprStts  = null,
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
