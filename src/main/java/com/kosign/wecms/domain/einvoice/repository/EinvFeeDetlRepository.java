package com.kosign.wecms.domain.einvoice.repository;

import com.kosign.wecms.domain.einvoice.entity.EinvFeeDetl;
import com.kosign.wecms.domain.einvoice.entity.EinvFeeDetlId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Fee-detail rows for the e-Invoice phase-2 detail-save (delete-then-insert). */
public interface EinvFeeDetlRepository extends JpaRepository<EinvFeeDetl, EinvFeeDetlId> {

    @Modifying
    @Query("DELETE FROM EinvFeeDetl f WHERE f.bizKey = :bizKey AND f.docId = :docId")
    void deleteByBizKeyAndDocId(@Param("bizKey") String bizKey, @Param("docId") String docId);

    /** Read the fee lines of one invoice in display order — for the
     *  "Download with Details" export. */
    List<EinvFeeDetl> findByBizKeyAndDocIdOrderByDispOrdAsc(String bizKey, String docId);
}
