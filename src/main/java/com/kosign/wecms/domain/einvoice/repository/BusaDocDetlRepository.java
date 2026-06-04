package com.kosign.wecms.domain.einvoice.repository;

import com.kosign.wecms.domain.einvoice.entity.BusaDocDetl;
import com.kosign.wecms.domain.einvoice.entity.BusaDocDetlId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Line-item rows for the e-Invoice phase-2 detail-save. The save path
 * deletes the existing lines for a {@code (BIZ_KEY, DOC_ID)} then
 * re-inserts (idempotent re-scrape) — see {@link #deleteByBizKeyAndDocId}.
 */
public interface BusaDocDetlRepository extends JpaRepository<BusaDocDetl, BusaDocDetlId> {

    @Modifying
    @Query("DELETE FROM BusaDocDetl d WHERE d.bizKey = :bizKey AND d.docId = :docId")
    void deleteByBizKeyAndDocId(@Param("bizKey") String bizKey, @Param("docId") String docId);

    /** Read the line items of one invoice in display order — for the
     *  "Download with Details" export. */
    List<BusaDocDetl> findByBizKeyAndDocIdOrderByDispOrdAsc(String bizKey, String docId);
}
