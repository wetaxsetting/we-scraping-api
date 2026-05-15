package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTargetId;
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
}
