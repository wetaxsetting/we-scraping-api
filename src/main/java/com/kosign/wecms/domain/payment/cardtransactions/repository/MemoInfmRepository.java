package com.kosign.wecms.domain.payment.cardtransactions.repository;

import com.kosign.wecms.domain.payment.cardtransactions.entity.MemoInfm;
import com.kosign.wecms.domain.payment.cardtransactions.entity.MemoInfmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * Polymorphic memo storage. Every method scopes by
 * {@code (bizKey, webSvcId, refNo)} — the legacy 3-coordinate that
 * pins a memo thread to its parent. The refactor's first consumer
 * passes {@code webSvcId="card-transactions"} + {@code refNo=cardSeqNo}.
 */
public interface MemoInfmRepository extends JpaRepository<MemoInfm, MemoInfmId> {

    /** Every memo for one (tenant, page, parent). Sorted newest-first
     *  so the side panel can prepend without re-sorting. */
    List<MemoInfm> findByBizKeyAndWebSvcIdAndRefNoOrderByRegDtmDescSeqNoDesc(
            String bizKey, String webSvcId, String refNo);

    /** Per-(tenant,page) MAX(SEQ_NO)+1 allocator. Returns
     *  {@code BigDecimal.ZERO} when this is the first memo for the page. */
    @Query("SELECT COALESCE(MAX(m.seqNo), 0) FROM MemoInfm m " +
           "WHERE m.bizKey = :bizKey AND m.webSvcId = :webSvcId")
    BigDecimal findMaxSeqNoByBizKeyAndWebSvcId(
            @Param("bizKey")   String bizKey,
            @Param("webSvcId") String webSvcId);
}
