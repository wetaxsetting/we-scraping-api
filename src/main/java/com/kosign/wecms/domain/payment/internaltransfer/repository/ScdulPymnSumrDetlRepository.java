package com.kosign.wecms.domain.payment.internaltransfer.repository;

import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetlId;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link ScdulPymnSumrDetl}. Every query
 * is tenant-scoped on {@code BIZ_KEY}.
 */
public interface ScdulPymnSumrDetlRepository
        extends JpaRepository<ScdulPymnSumrDetl, ScdulPymnSumrDetlId> {

    /** Detail-line lookup by composite PK + tenant. Used by the
     *  Reporting Account update endpoint. */
    Optional<ScdulPymnSumrDetl> findByBizKeyAndScdulPymnNoAndScdulPymnDetlNo(
            String bizKey, String scdulPymnNo, String scdulPymnDetlNo);

    /** Lines for one batch, sorted by the caller-supplied {@code Sort}.
     *  Used by the detail endpoint to render the per-line table. */
    List<ScdulPymnSumrDetl> findByBizKeyAndScdulPymnNo(
            String bizKey, String scdulPymnNo, Sort sort);

    /** Bulk delete by batch — used when deleting a draft batch. The
     *  parent SCDUL_PYMN_SUMR row must be deleted in the same
     *  transaction (see InternalTransferService#delete). */
    long deleteByBizKeyAndScdulPymnNo(String bizKey, String scdulPymnNo);

    /**
     * Smart Organizer candidate scan — domestic scheduled payment lines
     * for the tenant whose `LINK_MATCH_YN != 'Y'`. The service layer
     * applies date / amount / keyword filters in memory before
     * returning to the frontend — the row count is bounded by the
     * tenant's payment-detail volume.
     */
    @Query("SELECT d FROM ScdulPymnSumrDetl d WHERE d.bizKey = :bizKey" +
            "   AND (d.linkMatchYn IS NULL OR d.linkMatchYn <> 'Y')")
    List<ScdulPymnSumrDetl> findUnmatchedForTenant(@Param("bizKey") String bizKey);

    /**
     * Smart Organizer — set the payment-side match link. Mirrors legacy
     * {@code comm_0196_001_c001_act.jsp} which patches
     * {@code SCDUL_PYMN_SUMR_DETL.LINK_MATCH_YN = 'Y'} plus the
     * bank-transaction back-references.
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE SCDUL_PYMN_SUMR_DETL" +
            "    SET LINK_MATCH_YN = 'Y'," +
            "        ACCT_SEQ_NO = :acctSeqNo," +
            "        BANK_TRNS_SHORT_ID = :bankTrnsShortId," +
            "        BANK_TRNS_FULL_ID = :bankTrnsFullId," +
            "        MOD_ID = :userId," +
            "        MOD_DTM = :nowDtm" +
            "  WHERE BIZ_KEY = :bizKey" +
            "    AND SCDUL_PYMN_NO = :scdulPymnNo" +
            "    AND SCDUL_PYMN_DETL_NO = :scdulPymnDetlNo",
            nativeQuery = true)
    int markDetailMatched(
            @Param("bizKey") String bizKey,
            @Param("scdulPymnNo") String scdulPymnNo,
            @Param("scdulPymnDetlNo") String scdulPymnDetlNo,
            @Param("acctSeqNo") String acctSeqNo,
            @Param("bankTrnsShortId") String bankTrnsShortId,
            @Param("bankTrnsFullId") String bankTrnsFullId,
            @Param("userId") String userId,
            @Param("nowDtm") String nowDtm);

    /** Smart Organizer — clear the payment-side match link (undo flow). */
    @Modifying
    @Transactional
    @Query(value = "UPDATE SCDUL_PYMN_SUMR_DETL" +
            "    SET LINK_MATCH_YN = 'N'," +
            "        ACCT_SEQ_NO = NULL," +
            "        BANK_TRNS_SHORT_ID = NULL," +
            "        BANK_TRNS_FULL_ID = NULL," +
            "        MOD_ID = :userId," +
            "        MOD_DTM = :nowDtm" +
            "  WHERE BIZ_KEY = :bizKey" +
            "    AND SCDUL_PYMN_NO = :scdulPymnNo" +
            "    AND SCDUL_PYMN_DETL_NO = :scdulPymnDetlNo",
            nativeQuery = true)
    int clearDetailMatch(
            @Param("bizKey") String bizKey,
            @Param("scdulPymnNo") String scdulPymnNo,
            @Param("scdulPymnDetlNo") String scdulPymnDetlNo,
            @Param("userId") String userId,
            @Param("nowDtm") String nowDtm);
}
