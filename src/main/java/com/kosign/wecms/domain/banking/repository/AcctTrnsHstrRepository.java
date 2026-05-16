package com.kosign.wecms.domain.banking.repository;

import com.kosign.wecms.domain.banking.entity.AcctTrnsHstr;
import com.kosign.wecms.domain.banking.entity.AcctTrnsHstrId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AcctTrnsHstrRepository extends JpaRepository<AcctTrnsHstr, AcctTrnsHstrId> {

    /** Most-recent-first transaction history for one account (date desc, then time desc). */
    List<AcctTrnsHstr> findByBizKeyAndBankCdAndAcctNoAndTrscDtBetweenOrderByTrscDtDescTrscTmDesc(
            String bizKey, String bankCd, String acctNo, String fromDt, String toDt);

    /** Variant without a date range — used when the UI does not supply from/to. */
    List<AcctTrnsHstr> findByBizKeyAndBankCdAndAcctNoOrderByTrscDtDescTrscTmDesc(
            String bizKey, String bankCd, String acctNo);

    /**
     * Idempotency lookup for the Bank Transaction Sync orchestrator —
     * mirrors legacy {@code fnsh_0001_001_c001_act.jsp:853} (Coocon JOB
     * pump checks {@code biz_key = ? AND bank_trns_full_id = ? AND
     * locked_yn = ?} before inserting). A non-empty result means the
     * row already exists for this scrape window and we MUST NOT
     * INSERT a duplicate.
     *
     * <p>Returns the matched row (any one — `BANK_TRNS_FULL_ID` is
     * deterministic over the natural key so duplicates are an
     * invariant violation, not normal). Service uses {@code isPresent}
     * as a boolean and ignores the payload.
     *
     * <p>Backed by the V156 index
     * {@code ix_acct_trns_hstr_biz_full_id}; expected O(log N).
     */
    java.util.Optional<AcctTrnsHstr> findFirstByBizKeyAndBankTrnsFullId(
            String bizKey, String bankTrnsFullId);

    /**
     * Smart Organizer — set the bank-side match link. Updates the four
     * columns V23 added to ACCT_TRNS_HSTR (`SCDUL_PYMN_NO`,
     * `SCDUL_PYMN_DETL_NO`, plus the existing `LINK_MATCH_YN` flag and
     * audit columns) via a native UPDATE because the entity does not
     * yet map those payment-FK columns (Sprint 2+ deferred — adding
     * them to the entity is a separate change).
     *
     * <p>Returns the number of rows updated; the service treats
     * {@code 0} as "row not found / wrong tenant" and surfaces
     * {@code WBE_TRANSACTION_NOT_FOUND}.
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE ACCT_TRNS_HSTR" +
            "    SET LINK_MATCH_YN = 'Y'," +
            "        SCDUL_PYMN_NO = :scdulPymnNo," +
            "        SCDUL_PYMN_DETL_NO = :scdulPymnDetlNo," +
            "        MOD_ID = :userId," +
            "        MOD_DTM = :nowDtm" +
            "  WHERE BIZ_KEY = :bizKey" +
            "    AND ACCT_SEQ_NO = :acctSeqNo",
            nativeQuery = true)
    int markBankRowMatched(
            @Param("bizKey") String bizKey,
            @Param("acctSeqNo") String acctSeqNo,
            @Param("scdulPymnNo") String scdulPymnNo,
            @Param("scdulPymnDetlNo") String scdulPymnDetlNo,
            @Param("userId") String userId,
            @Param("nowDtm") String nowDtm);

    /** Smart Organizer — clear the bank-side match link (undo flow). */
    @Modifying
    @Transactional
    @Query(value = "UPDATE ACCT_TRNS_HSTR" +
            "    SET LINK_MATCH_YN = 'N'," +
            "        SCDUL_PYMN_NO = NULL," +
            "        SCDUL_PYMN_DETL_NO = NULL," +
            "        OVRS_PYMN_NO = NULL," +
            "        OVRS_PYMN_DETL_NO = NULL," +
            "        MOD_ID = :userId," +
            "        MOD_DTM = :nowDtm" +
            "  WHERE BIZ_KEY = :bizKey" +
            "    AND ACCT_SEQ_NO = :acctSeqNo",
            nativeQuery = true)
    int clearBankRowMatch(
            @Param("bizKey") String bizKey,
            @Param("acctSeqNo") String acctSeqNo,
            @Param("userId") String userId,
            @Param("nowDtm") String nowDtm);
}
