package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTargetId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TbPayTargetRepository
        extends JpaRepository<TbPayTarget, TbPayTargetId> {

    // TRAN_SEQ allocation now routes through
    // LegacyFunctionRepository.nextTbPayTargetTranSeq (V140 #2 — the GLOBAL
    // firm_seq_no sequence) so every wb_get_next_val call goes through the one
    // funnel and lines up with the live JEX firm-banking dispatch.

    /**
     * Bulk-fetch dispatch rows for the API path. One query per Result-detail
     * page render — the resolver builds an in-memory map keyed by
     * {@code tranSeq} to join back to the detail lines.
     *
     * <p>Used by {@link com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver}
     * to surface {@code PROC_STAT} / {@code PROC_DTTM} on the Result
     * detail page.
     */
    List<TbPayTarget> findByBizKeyAndTranDtAndTranSeqIn(
            String bizKey, String tranDt, Collection<String> tranSeqs);

    /**
     * Re-read a single committed dispatch row for the after-commit real-time
     * dispatch (C2). The {@link com.kosign.wecms.domain.payment.dispatch.service.RealtimeDispatchService}
     * gates on {@code PROC_STAT='9'} so a row already advanced (or absent) is a
     * safe no-op — mirrors the legacy {@code TB_PAY_TARGET_R001}
     * {@code WHERE ... PROC_STAT='9'} pickup.
     */
    Optional<TbPayTarget> findByBizKeyAndTranDtAndTranSeq(
            String bizKey, String tranDt, String tranSeq);
}
