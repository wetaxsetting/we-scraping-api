package com.kosign.wecms.domain.payment.approvalbox.repository;

import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolApprvrInfm;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolApprvrInfmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository for {@code EXPT_RESOL_APPRVR_INFM} — per-approver rows.
 *
 * <p>Used by:
 * <ul>
 *   <li>List service — chain expansion ({@link #findChain}).</li>
 *   <li>Approve / Reject service — find the caller's row
 *       ({@code findById}), update the row's status, count remaining
 *       pending peers ({@link #countByStatus}), bump downstream
 *       {@code W → S} on reject ({@link #stopDownstream}).</li>
 * </ul>
 *
 * <p>Every method constrains on {@code biz_key} via the composite PK
 * or an explicit parameter; never pass a {@code %} wildcard.
 */
public interface ExptResolApprvrInfmRepository
        extends JpaRepository<ExptResolApprvrInfm, ExptResolApprvrInfmId> {

    /** Ordered chain (line 1 → line N) for one document. */
    @Query("""
           SELECT a FROM ExptResolApprvrInfm a
           WHERE a.bizKey = :bizKey AND a.apprSeqNo = :apprSeqNo
           ORDER BY a.apprvrLineNo ASC
           """)
    List<ExptResolApprvrInfm> findChain(
            @Param("bizKey")    String bizKey,
            @Param("apprSeqNo") String apprSeqNo);

    /** Count approvers on one document with a given status. Used by
     *  Approve to detect whether any pending peers remain. */
    @Query("""
           SELECT COUNT(a) FROM ExptResolApprvrInfm a
           WHERE a.bizKey = :bizKey
             AND a.apprSeqNo = :apprSeqNo
             AND a.apprvrStts = :status
           """)
    long countByStatus(
            @Param("bizKey")    String bizKey,
            @Param("apprSeqNo") String apprSeqNo,
            @Param("status")    String status);

    /** On Reject, mark every still-waiting downstream approver as
     *  stopped ({@code 'S'}). Uses a single UPDATE to keep the cascade
     *  in one round-trip. */
    @Modifying
    @Query("""
           UPDATE ExptResolApprvrInfm a
           SET a.apprvrStts = 'S',
               a.modId      = :modId,
               a.modDtm     = :modDtm
           WHERE a.bizKey = :bizKey
             AND a.apprSeqNo = :apprSeqNo
             AND a.apprvrStts = 'W'
           """)
    int stopDownstream(
            @Param("bizKey")    String bizKey,
            @Param("apprSeqNo") String apprSeqNo,
            @Param("modId")     String modId,
            @Param("modDtm")    String modDtm);
}
