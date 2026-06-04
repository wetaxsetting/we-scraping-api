package com.kosign.wecms.domain.einvoice.repository;

import com.kosign.wecms.domain.einvoice.entity.EinvTaxDetl;
import com.kosign.wecms.domain.einvoice.entity.EinvTaxDetlId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Tax-detail rows for the e-Invoice phase-2 detail-save (delete-then-insert). */
public interface EinvTaxDetlRepository extends JpaRepository<EinvTaxDetl, EinvTaxDetlId> {

    @Modifying
    @Query("DELETE FROM EinvTaxDetl t WHERE t.bizKey = :bizKey AND t.docId = :docId")
    void deleteByBizKeyAndDocId(@Param("bizKey") String bizKey, @Param("docId") String docId);

    /** Read the tax-rate lines of one invoice in display order — for the
     *  "Download with Details" export. */
    List<EinvTaxDetl> findByBizKeyAndDocIdOrderByDispOrdAsc(String bizKey, String docId);
}
