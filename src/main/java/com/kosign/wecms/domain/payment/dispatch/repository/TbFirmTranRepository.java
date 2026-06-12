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

    /**
     * No-response real-time transfers awaiting a result check — legacy
     * {@code PROCESSING_RESULT_CHECK_BATCH} / {@code TB_FIRM_TRAN_R001}
     * ({@code TRAN_TP='202' AND PROC_STAT='6'}). The caller further filters by
     * date + the 2-minute age window before issuing the 203 inquiry.
     */
    List<TbFirmTran> findByTranTpAndProcStat(String tranTp, String procStat);
}
