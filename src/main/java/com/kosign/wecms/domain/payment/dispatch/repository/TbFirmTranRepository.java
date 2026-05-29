package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTranId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read-only access to {@code TB_FIRM_TRAN} for the Phase 2 Result-detail
 * resolver — supplies the API-path {@code RS_CD} + {@code RS_MSG} for
 * each dispatched line.
 */
public interface TbFirmTranRepository
        extends JpaRepository<TbFirmTran, TbFirmTranId> {

    /**
     * Bulk-fetch the response rows for one batch of API-path dispatches.
     * The resolver builds an in-memory map keyed by {@code tranSeq} to
     * join back to the detail lines.
     */
    List<TbFirmTran> findByBizKeyAndTranDtAndTranSeqIn(
            String bizKey, String tranDt, Collection<String> tranSeqs);
}
