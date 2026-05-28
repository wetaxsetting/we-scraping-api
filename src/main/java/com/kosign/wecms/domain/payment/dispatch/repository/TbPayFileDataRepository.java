package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileDataId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TbPayFileDataRepository
        extends JpaRepository<TbPayFileData, TbPayFileDataId> {

    /**
     * Bulk-fetch every dispatch row for one batch's file header. The
     * resolver builds an in-memory map keyed by {@code seq} (stored on
     * the detail line as {@code pay_tran_seq}, varchar) to surface
     * per-line {@code RS_CD} on the Result detail page.
     */
    List<TbPayFileData> findByBizKeyAndTranDtAndFileSeqNo(
            String bizKey, String tranDt, String fileSeqNo);
}
