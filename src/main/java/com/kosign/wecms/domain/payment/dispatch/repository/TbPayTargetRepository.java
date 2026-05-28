package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTargetId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TbPayTargetRepository
        extends JpaRepository<TbPayTarget, TbPayTargetId> {

    /**
     * Highest existing {@code TRAN_SEQ} for a given tenant + Vietnam-local
     * date. Returns 0 when no row exists yet (the dispatcher then writes
     * sequence 1).
     */
    @Query("""
            SELECT COALESCE(MAX(CAST(t.tranSeq AS long)), 0)
              FROM TbPayTarget t
             WHERE t.bizKey = :bizKey
               AND t.tranDt = :tranDt
            """)
    long maxTranSeq(@Param("bizKey") String bizKey, @Param("tranDt") String tranDt);

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
}
