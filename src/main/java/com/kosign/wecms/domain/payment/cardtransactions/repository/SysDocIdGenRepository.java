package com.kosign.wecms.domain.payment.cardtransactions.repository;

import com.kosign.wecms.domain.payment.cardtransactions.entity.SysDocIdGen;
import com.kosign.wecms.domain.payment.cardtransactions.entity.SysDocIdGenId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Per-day document-number counter ({@code SYS_DOC_ID_GEN}). The receipt-create
 * path upserts the daily row after {@code wb_get_next_val('sys_doc_id_gen',…)}
 * mints the id, so subsequent same-day allocations increment (audit finding G7).
 */
public interface SysDocIdGenRepository extends JpaRepository<SysDocIdGen, SysDocIdGenId> {

    /**
     * Record the newly-issued {@code doc_id} as the latest for
     * {@code (biz_key, doc_type, req_date)} — insert the daily row on the first
     * issue of the day, update it on every subsequent issue. Matches the legacy
     * population that keeps the {@code #48} read-only {@code max+1} branch
     * advancing. Idempotent on the PK via {@code ON CONFLICT}.
     */
    @Modifying
    @Query(value = """
            INSERT INTO SYS_DOC_ID_GEN (BIZ_KEY, DOC_TYPE, REQ_DATE, DOC_ID)
            VALUES (:bizKey, :docType, :reqDate, :docId)
            ON CONFLICT (BIZ_KEY, DOC_TYPE, REQ_DATE)
            DO UPDATE SET DOC_ID = EXCLUDED.DOC_ID
            """, nativeQuery = true)
    void upsertDailyDocId(
            @Param("bizKey")  String bizKey,
            @Param("docType") String docType,
            @Param("reqDate") String reqDate,
            @Param("docId")   String docId);
}
