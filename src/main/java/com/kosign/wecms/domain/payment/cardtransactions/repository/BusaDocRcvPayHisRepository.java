package com.kosign.wecms.domain.payment.cardtransactions.repository;

import com.kosign.wecms.domain.payment.cardtransactions.entity.BusaDocRcvPayHis;
import com.kosign.wecms.domain.payment.cardtransactions.entity.BusaDocRcvPayHisId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * Per-document card-link rows ({@code BUSA_DOC_RCV_PAY_HIS}). Card flow
 * writes one row per linked expense; one master {@code CARD_TRNS_RCV_PAY_INFM}
 * row coordinates them. Schema mirrors DEV (V13 + V23).
 */
public interface BusaDocRcvPayHisRepository
        extends JpaRepository<BusaDocRcvPayHis, BusaDocRcvPayHisId> {

    // RCV_PAY_HIS_SEQ_NO allocation now routes through
    // LegacyFunctionRepository.nextBusaDocRcvPayHisSeqNo (V140 #29 — per
    // (biz_key, doc_id) MAX+1, verbatim from the live JEX function) so every
    // wb_get_next_val call goes through the one funnel.

    /**
     * Batch-resolve linked docs for a page of card transactions —
     * returns one row per (cardSeqNo, docId) link, joined to
     * {@code BUSA_DOC_MSTR} so the service can build the
     * {@code linked_docs[]} array per card without an N+1 fan-out.
     *
     * <p>Filtered to {@code RCV_PAY_WAY_CD='R'} — the legacy
     * {@code RcvPayWayCd.CARD} value (the enum maps {@code 'C'} to CASH).
     * Matches legacy {@code IDO.CARD_MSTR_R002.xml:137} so transfer / cash
     * links on the same document don't leak into the card list.
     * (Was {@code 'C'}; corrected 2026-06-19, audit D1.)
     *
     * <p>Native query (no JPQL JOIN on {@link com.kosign.wecms.domain.einvoice.entity.BusaDocMstr}
     * from this domain — would require pulling that entity into the
     * card-transactions package).
     */
    @Query(value = """
            SELECT h.REF_NO   AS card_seq_no,
                   h.DOC_ID   AS doc_id,
                   m.DISP_DOC_ID AS disp_doc_id,
                   m.DOC_TYPE AS doc_type,
                   h.DEDU_AMT AS dedu_amt
              FROM BUSA_DOC_RCV_PAY_HIS h
              JOIN BUSA_DOC_MSTR m
                ON m.BIZ_KEY = h.BIZ_KEY AND m.DOC_ID = h.DOC_ID
             WHERE h.BIZ_KEY        = :bizKey
               AND h.RCV_PAY_WAY_CD = 'R'
               AND h.REF_NO IN (:cardSeqNos)
             ORDER BY h.REF_NO, h.DOC_ID
            """, nativeQuery = true)
    List<LinkedDocProjection> findLinkedDocsByCardSeqNos(
            @Param("bizKey")     String bizKey,
            @Param("cardSeqNos") List<String> cardSeqNos);

    /** Tuple shape for {@link #findLinkedDocsByCardSeqNos}. */
    interface LinkedDocProjection {
        String getCardSeqNo();
        String getDocId();
        String getDispDocId();
        String getDocType();
        BigDecimal getDeduAmt();
    }

    /** Drop every {@code BUSA_DOC_RCV_PAY_HIS} row that links a
     *  card transaction to one or more documents. Returns the count
     *  deleted so the service can log it. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM BusaDocRcvPayHis h " +
           " WHERE h.bizKey = :bizKey " +
           "   AND h.refNo  = :cardSeqNo")
    int deleteByBizKeyAndRefNo(
            @Param("bizKey")    String bizKey,
            @Param("cardSeqNo") String cardSeqNo);
}
