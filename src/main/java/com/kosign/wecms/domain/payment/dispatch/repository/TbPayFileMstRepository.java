package com.kosign.wecms.domain.payment.dispatch.repository;

import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMstId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TbPayFileMstRepository
        extends JpaRepository<TbPayFileMst, TbPayFileMstId> {

    /**
     * Highest existing {@code FILE_SEQ_NO} for a given tenant + date.
     * Returns 0 when no row exists yet.
     */
    @Query("""
            SELECT COALESCE(MAX(CAST(m.fileSeqNo AS long)), 0)
              FROM TbPayFileMst m
             WHERE m.bizKey = :bizKey
               AND m.tranDt = :tranDt
            """)
    long maxFileSeqNo(@Param("bizKey") String bizKey, @Param("tranDt") String tranDt);
}
