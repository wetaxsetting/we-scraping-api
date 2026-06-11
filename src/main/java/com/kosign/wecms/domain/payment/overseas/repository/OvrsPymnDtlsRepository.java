package com.kosign.wecms.domain.payment.overseas.repository;

import com.kosign.wecms.domain.payment.entity.OvrsPymnDtls;
import com.kosign.wecms.domain.payment.entity.OvrsPymnDtlsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OvrsPymnDtlsRepository extends JpaRepository<OvrsPymnDtls, OvrsPymnDtlsId> {

    java.util.List<OvrsPymnDtls> findByBizKeyAndOvrsPymnNoOrderByTrnSeqAsc(
            String bizKey, String ovrsPymnNo);

    java.util.Optional<OvrsPymnDtls> findByBizKeyAndOvrsPymnNoAndTrnSeq(
            String bizKey, String ovrsPymnNo, Integer trnSeq);

    /** Load one overseas detail line by its match identity
     *  {@code OVRS_PYMN_DETL_NO} (OVR-G1 banking match keys on this, not TRN_SEQ). */
    java.util.Optional<OvrsPymnDtls> findByBizKeyAndOvrsPymnNoAndOvrsPymnDetlNo(
            String bizKey, String ovrsPymnNo, String ovrsPymnDetlNo);

    /**
     * OVR-G1 match — set the line-side match link on {@code OVRS_PYMN_DTLS}
     * (legacy {@code comm_0197_001_c001} {@code isOversea}:
     * {@code LINK_MATCH_YN='Y'} + {@code ACCT_SEQ_NO} + bank ids). Keyed by the
     * match identity {@code OVRS_PYMN_DETL_NO}. Returns rows updated (0 → line
     * not found / wrong tenant). Does NOT touch the OVRS header.
     */
    @Modifying
    @Query(value = "UPDATE OVRS_PYMN_DTLS" +
            "    SET LINK_MATCH_YN = 'Y'," +
            "        ACCT_SEQ_NO = :acctSeqNo," +
            "        BANK_TRNS_SHORT_ID = :bankTrnsShortId," +
            "        BANK_TRNS_FULL_ID = :bankTrnsFullId," +
            "        MOD_ID = :userId," +
            "        MOD_DTM = :nowDtm" +
            "  WHERE BIZ_KEY = :bizKey" +
            "    AND OVRS_PYMN_NO = :ovrsPymnNo" +
            "    AND OVRS_PYMN_DETL_NO = :ovrsPymnDetlNo",
            nativeQuery = true)
    int markDetailMatchedOverseas(
            @Param("bizKey") String bizKey,
            @Param("ovrsPymnNo") String ovrsPymnNo,
            @Param("ovrsPymnDetlNo") String ovrsPymnDetlNo,
            @Param("acctSeqNo") String acctSeqNo,
            @Param("bankTrnsShortId") String bankTrnsShortId,
            @Param("bankTrnsFullId") String bankTrnsFullId,
            @Param("userId") String userId,
            @Param("nowDtm") String nowDtm);

    /**
     * OVR-G1 unmatch — clear the line-side match link on {@code OVRS_PYMN_DTLS}
     * (legacy {@code comm_0197_002_u001} {@code isOversea}). Keyed by
     * {@code OVRS_PYMN_DETL_NO}. Returns rows updated.
     */
    @Modifying
    @Query(value = "UPDATE OVRS_PYMN_DTLS" +
            "    SET LINK_MATCH_YN = 'N'," +
            "        ACCT_SEQ_NO = NULL," +
            "        BANK_TRNS_SHORT_ID = NULL," +
            "        BANK_TRNS_FULL_ID = NULL," +
            "        MOD_ID = :userId," +
            "        MOD_DTM = :nowDtm" +
            "  WHERE BIZ_KEY = :bizKey" +
            "    AND OVRS_PYMN_NO = :ovrsPymnNo" +
            "    AND OVRS_PYMN_DETL_NO = :ovrsPymnDetlNo",
            nativeQuery = true)
    int clearDetailMatchOverseas(
            @Param("bizKey") String bizKey,
            @Param("ovrsPymnNo") String ovrsPymnNo,
            @Param("ovrsPymnDetlNo") String ovrsPymnDetlNo,
            @Param("userId") String userId,
            @Param("nowDtm") String nowDtm);

    @Modifying
    @Query("DELETE FROM OvrsPymnDtls d WHERE d.bizKey = :bizKey AND d.ovrsPymnNo = :ovrsPymnNo")
    int deleteByBizKeyAndOvrsPymnNo(@Param("bizKey") String bizKey, @Param("ovrsPymnNo") String ovrsPymnNo);

    /**
     * Highest numeric {@code OVRS_PYMN_DETL_NO} for a tenant (0 when none).
     * Backs the per-tenant MAX+1 allocator in {@code OverseasPaymentKeyGenerator}
     * — legacy {@code wb_get_next_val('ovrs_pymn_sumr_detl','ovrs_pymn_detl_no', BIZ_KEY)}
     * parity (OVR-G1 Phase A). The {@code ~ '^[0-9]+$'} guard skips any
     * non-numeric value before the BIGINT cast (all legacy values are numeric;
     * defensive only). VARCHAR(15) fits comfortably inside BIGINT.
     */
    @Query(value = """
            SELECT COALESCE(MAX(CAST(OVRS_PYMN_DETL_NO AS BIGINT)), 0)
              FROM OVRS_PYMN_DTLS
             WHERE BIZ_KEY = :bizKey
               AND OVRS_PYMN_DETL_NO IS NOT NULL
               AND OVRS_PYMN_DETL_NO ~ '^[0-9]+$'
            """, nativeQuery = true)
    long maxNumericDetlNo(@Param("bizKey") String bizKey);
}
