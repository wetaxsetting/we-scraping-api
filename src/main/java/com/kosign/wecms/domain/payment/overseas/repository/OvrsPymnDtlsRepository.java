package com.kosign.wecms.domain.payment.overseas.repository;

import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetl;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumrDetlId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OvrsPymnDtlsRepository extends JpaRepository<OvrsPymnSumrDetl, OvrsPymnSumrDetlId> {

    java.util.List<OvrsPymnSumrDetl> findByBizKeyAndOvrsPymnNoOrderByOvrsPymnDetlNoAsc(
            String bizKey, String ovrsPymnNo);

    java.util.Optional<OvrsPymnSumrDetl> findByBizKeyAndOvrsPymnNoAndOvrsPymnDetlNo(
            String bizKey, String ovrsPymnNo, String ovrsPymnDetlNo);

    @Modifying
    @Query("DELETE FROM OvrsPymnSumrDetl d WHERE d.bizKey = :bizKey AND d.ovrsPymnNo = :ovrsPymnNo")
    int deleteByBizKeyAndOvrsPymnNo(@Param("bizKey") String bizKey, @Param("ovrsPymnNo") String ovrsPymnNo);

    /**
     * P4 Smart Organizer (banking→payment) overseas candidate scan — the
     * overseas branch of legacy {@code OVRS_PYMN_SUMR_DETL_R006}
     * ({@code comm_0196_001_r001}). Given a bank withdrawal's transfer date +
     * exact amount, returns unmatched overseas detail lines whose parent batch
     * is transferred, for a matching transfer date (legacy joins
     * {@code OVRS_PYMN_SUMR} on {@code TRNF_DT}, a 14-char {@code yyyyMMddHHmmss}
     * despite the {@code _DT} suffix).
     *
     * <p>No account filter (legacy {@code R006} has none) and — unlike the SCDUL
     * branch — no header {@code LINK_MATCH_YN} check (the R006 overseas branch
     * guards only the detail's {@code LINK_MATCH_YN}). Transfer-time floor (#6),
     * per-line dispatch success (#12, via
     * {@code DispatchResultResolver.resolveOverseas}) and the locked-match
     * exclusion (#11) are applied in {@code MatchService.listCandidates} in Java.
     */
    @Query(value = """
            SELECT d.* FROM OVRS_PYMN_SUMR_DETL d
              JOIN OVRS_PYMN_SUMR s
                ON s.BIZ_KEY = d.BIZ_KEY AND s.OVRS_PYMN_NO = d.OVRS_PYMN_NO
             WHERE d.BIZ_KEY = :bizKey
               AND s.TRNF_YN = 'Y'
               AND COALESCE(d.LINK_MATCH_YN, 'N') <> 'Y'
               AND d.TRNF_AMT = :trscAmt
               AND s.TRNF_DT IS NOT NULL
               AND LENGTH(s.TRNF_DT) >= 8
               AND SUBSTRING(s.TRNF_DT FROM 1 FOR 8) = :trscDt
            """, nativeQuery = true)
    java.util.List<OvrsPymnSumrDetl> findOverseasBankMatchCandidates(
            @Param("bizKey") String bizKey,
            @Param("trscDt") String trscDt,
            @Param("trscAmt") java.math.BigDecimal trscAmt);

    /**
     * Auto-match candidate scan (overseas detail-level) — Branch 3 of the
     * legacy auto-match candidate query {@code OVRS_PYMN_SUMR_DETL_R007}
     * ({@code fnsh_0001_001_c001_act.jsp:1908}). Given a scraped bank
     * withdrawal's account + transfer date + exact amount, returns unmatched
     * overseas detail lines whose parent batch was dispatched
     * ({@code TRNF_YN='Y'}) on the same withdrawal account.
     *
     * <p>Identical shape to {@link #findOverseasBankMatchCandidates} (the R006
     * / Smart-Organizer branch) except it ADDS the {@code S.WDRL_ACT_NO} filter
     * — R007 (auto-match, post-sync) is account-scoped, R006 (manual) is not.
     * The remaining legacy predicates — dispatch success (via
     * {@code DispatchResultResolver.resolveOverseas}), transfer-time
     * {@literal <} bank-time (overseas {@code TRNF_DT} is date-only so this
     * passes), remark-contained (line {@code MEMO}), and the exactly-one +
     * locked-{@code BUSI_MATCH_LOG} guards — are applied by
     * {@code AutoMatchService} in Java, exactly as the domestic branch does.
     */
    @Query(value = """
            SELECT d.* FROM OVRS_PYMN_SUMR_DETL d
              JOIN OVRS_PYMN_SUMR s
                ON s.BIZ_KEY = d.BIZ_KEY AND s.OVRS_PYMN_NO = d.OVRS_PYMN_NO
             WHERE d.BIZ_KEY = :bizKey
               AND s.WDRL_ACT_NO = :acctNo
               AND s.TRNF_YN = 'Y'
               AND COALESCE(d.LINK_MATCH_YN, 'N') <> 'Y'
               AND d.TRNF_AMT = :trscAmt
               AND s.TRNF_DT IS NOT NULL
               AND LENGTH(s.TRNF_DT) >= 8
               AND SUBSTRING(s.TRNF_DT FROM 1 FOR 8) = :trscDt
            """, nativeQuery = true)
    java.util.List<OvrsPymnSumrDetl> findOverseasAutoMatchCandidates(
            @Param("bizKey") String bizKey,
            @Param("acctNo") String acctNo,
            @Param("trscDt") String trscDt,
            @Param("trscAmt") java.math.BigDecimal trscAmt);

    // OVRS_PYMN_DETL_NO allocation now routes through
    // LegacyFunctionRepository.nextOvrsPymnDetlNo (V140 #30 — per-tenant MAX+1
    // over (ovrs_pymn_mstr UNION ovrs_pymn_sumr_detl), verbatim from the live
    // JEX function) so every wb_get_next_val call goes through the one funnel.
    // (The live JEX branch DOES exist for this column — the earlier "returns
    // null for this column" note was incorrect.)

    /**
     * P2 overseas banking-match — set the payment-side link (legacy
     * {@code comm_0197_001_c001} {@code isOversea}). Stamps
     * {@code LINK_MATCH_YN='Y'} + {@code ACCT_SEQ_NO} + bank ids. Native UPDATE
     * for parity with the domestic {@code markDetailMatched}. Returns rows
     * updated; the service treats {@code 0} as "line not found / wrong tenant".
     */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query(value = "UPDATE OVRS_PYMN_SUMR_DETL" +
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
    int markOverseasDetailMatched(
            @Param("bizKey") String bizKey,
            @Param("ovrsPymnNo") String ovrsPymnNo,
            @Param("ovrsPymnDetlNo") String ovrsPymnDetlNo,
            @Param("acctSeqNo") String acctSeqNo,
            @Param("bankTrnsShortId") String bankTrnsShortId,
            @Param("bankTrnsFullId") String bankTrnsFullId,
            @Param("userId") String userId,
            @Param("nowDtm") String nowDtm);

    /**
     * P2 overseas unmatch — clear the payment-side link (legacy
     * {@code comm_0197_002_u001} / {@code comm_0197_001_u001} isOversea).
     * Returns rows updated.
     */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query(value = "UPDATE OVRS_PYMN_SUMR_DETL" +
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
    int clearOverseasDetailMatch(
            @Param("bizKey") String bizKey,
            @Param("ovrsPymnNo") String ovrsPymnNo,
            @Param("ovrsPymnDetlNo") String ovrsPymnDetlNo,
            @Param("userId") String userId,
            @Param("nowDtm") String nowDtm);
}
