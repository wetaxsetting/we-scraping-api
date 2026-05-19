package com.kosign.wecms.domain.payment.cardtransactions.repository;

import com.kosign.wecms.domain.payment.cardtransactions.entity.CardMstr;
import com.kosign.wecms.domain.payment.cardtransactions.entity.CardMstrId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA repository for {@link CardMstr}. Read-only in this MR
 * — surfaces the dropdown-population query for the
 * {@code GET /api/v1/payment/card-transactions/cards} endpoint and an
 * in-memory lookup helper for the LEFT JOIN columns the ledger renders.
 *
 * <p>Every query is tenant-scoped via an explicit {@code bizKey}
 * parameter — same pattern as {@link CardTrnsHstrRepository}.
 *
 * <p>Note: legacy parity — the {@code ACTV_YN='Y'} filter is left out of
 * the dropdown query because the legacy JEX implementation comments it
 * out (see {@code card_0001_001_r002_act.jsp:35-36}). The refactor
 * mirrors that until OQ-A is resolved by the PO.
 */
public interface CardMstrRepository extends JpaRepository<CardMstr, CardMstrId> {

    /**
     * All cards for the tenant ordered most-recently-modified-first.
     * Optional {@code bankCd} narrows the dropdown when the frontend
     * has selected a single bank first.
     *
     * <p>Used by both:
     * <ul>
     *   <li>The cards endpoint (full list).</li>
     *   <li>The service's per-row registry warm-up — caches once per
     *       request so the ledger render doesn't fan out one query per
     *       (bank_cd, card_no) pair.</li>
     * </ul>
     */
    @Query("""
            SELECT c FROM CardMstr c
             WHERE c.bizKey = :bizKey
               AND (:bankCd IS NULL OR c.bankCd = :bankCd)
             ORDER BY c.modDtm DESC, c.bankCd ASC, c.cardNo ASC
            """)
    List<CardMstr> findActiveCards(@Param("bizKey") String bizKey,
                                   @Param("bankCd") String bankCd);

    /**
     * Cards eligible for the Update Cards scrape sync — every active
     * row in the tenant that has a registered {@code BANK_CERT_SEQ}
     * (so the orchestrator can decrypt credentials + Coocon-login on
     * the user's behalf). Mirrors legacy {@code card_0001_001.loadCard}
     * which the JEX side feeds into {@code nxisas_cardTranList}.
     *
     * <p>Ordered by bank then card-no for deterministic per-credential
     * batching. {@code BANK_CERT_SEQ} is null on rows whose legacy
     * import never carried a credential — those rows are excluded
     * from the sync since the orchestrator can't authenticate without
     * one.
     */
    @Query("""
            SELECT c FROM CardMstr c
             WHERE c.bizKey = :bizKey
               AND c.actvYn = 'Y'
               AND c.bankCertSeq IS NOT NULL
             ORDER BY c.bankCd ASC, c.cardNo ASC
            """)
    List<CardMstr> findActiveCardsForSync(@Param("bizKey") String bizKey);

    /* ----------------------------------------------------------------
     *  Card Management Setting modal (legacy card_0001_003)
     * ---------------------------------------------------------------- */

    /**
     * Paged + sortable list for the Card Management Setting modal.
     * Mirrors legacy {@code card_0001_003_r001} which feeds
     * {@code IDO.CARD_MSTR_R004} — the legacy SQL filters by
     * {@code BIZ_KEY} only (no {@code ACTV_YN} filter — both active
     * and inactive cards render in the modal). Optional case-insensitive
     * search runs over {@code CARD_NM} / {@code CARD_NICK_NM} /
     * {@code CARD_HLDR_NM} (ILIKE).
     *
     * <p>{@code srchWdPattern} is pre-lowercased {@code %foo%} (built
     * in the service) so the JPQL never has to {@code CONCAT} a
     * possibly-null param — same precedent as
     * {@link CardTrnsHstrRepository#findLedgerPage}.
     *
     * <p>{@code BANK_NM} is resolved server-side by the service via
     * {@code BankCdResolver} (no JOIN here) — see backend doc §3.4.
     * Sort by the "Card Company" header therefore uses
     * {@code CardMstrSortField.BANK_CD}, never a resolved-name string.
     */
    @Query("""
            SELECT c FROM CardMstr c
             WHERE c.bizKey = :bizKey
               AND (:srchWdPattern IS NULL
                    OR LOWER(COALESCE(c.cardNm,     '')) LIKE :srchWdPattern
                    OR LOWER(COALESCE(c.cardNickNm, '')) LIKE :srchWdPattern
                    OR LOWER(c.cardHldrNm) LIKE :srchWdPattern)
            """)
    Page<CardMstr> findManagementPage(
            @Param("bizKey")        String   bizKey,
            @Param("srchWdPattern") String   srchWdPattern,
            Pageable                pageable);

    /** Narrow {@code ACTV_YN} writer for the modal's toggle switch.
     *  Mirrors legacy {@code card_0001_003_u001}. Returns rows affected
     *  so the service collapses missing-row + wrong-tenant into one
     *  {@code WBE_CARD_TRANSACTIONS_NOT_FOUND} envelope. */
    @Modifying
    @Query("""
            UPDATE CardMstr c
               SET c.actvYn  = :actvYn,
                   c.modId   = :modId,
                   c.modDtm  = :modDtm
             WHERE c.bizKey  = :bizKey
               AND c.bankCd  = :bankCd
               AND c.cardNo  = :cardNo
            """)
    int patchActiveYn(@Param("bizKey") String bizKey,
                      @Param("bankCd") String bankCd,
                      @Param("cardNo") String cardNo,
                      @Param("actvYn") String actvYn,
                      @Param("modId")  String modId,
                      @Param("modDtm") String modDtm);

    /** Always-paired writer for the modal's inline-rename
     *  ({@code CARD_HLDR_NM} + {@code CARD_NICK_NM}). Mirrors legacy
     *  {@code card_0001_003_u002}. */
    @Modifying
    @Query("""
            UPDATE CardMstr c
               SET c.cardHldrNm = :cardHldrNm,
                   c.cardNickNm = :cardNickNm,
                   c.modId      = :modId,
                   c.modDtm     = :modDtm
             WHERE c.bizKey     = :bizKey
               AND c.bankCd     = :bankCd
               AND c.cardNo     = :cardNo
            """)
    int patchCardInfo(@Param("bizKey")     String bizKey,
                      @Param("bankCd")     String bankCd,
                      @Param("cardNo")     String cardNo,
                      @Param("cardHldrNm") String cardHldrNm,
                      @Param("cardNickNm") String cardNickNm,
                      @Param("modId")      String modId,
                      @Param("modDtm")     String modDtm);

    /** Hard-delete by composite PK — mirrors legacy
     *  {@code card_0001_003_d001} via {@code CardMstrDAO.deleteByPk}.
     *  Refactor's {@code CARD_MSTR} has no {@code DEL_YN} column;
     *  legacy parity. Callers MUST run the in-use guard first (see
     *  {@code CardTrnsHstrRepository.countByBizKeyAndBankCdAndCardNo}). */
    @Modifying
    @Query("""
            DELETE FROM CardMstr c
             WHERE c.bizKey = :bizKey
               AND c.bankCd = :bankCd
               AND c.cardNo = :cardNo
            """)
    int deleteByBizKeyAndBankCdAndCardNo(
            @Param("bizKey") String bizKey,
            @Param("bankCd") String bankCd,
            @Param("cardNo") String cardNo);
}
