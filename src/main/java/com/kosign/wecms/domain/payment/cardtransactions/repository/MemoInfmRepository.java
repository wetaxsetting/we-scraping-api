package com.kosign.wecms.domain.payment.cardtransactions.repository;

import com.kosign.wecms.domain.payment.cardtransactions.entity.MemoInfm;
import com.kosign.wecms.domain.payment.cardtransactions.entity.MemoInfmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Polymorphic memo storage. Every method scopes by
 * {@code (bizKey, webSvcId, refNo)} — the legacy 3-coordinate that
 * pins a memo thread to its parent. The Card Transactions consumer
 * passes {@code webSvcId="card_0001_001"} + {@code refNo=cardSeqNo}.
 */
public interface MemoInfmRepository extends JpaRepository<MemoInfm, MemoInfmId> {

    /** Every memo for one (tenant, page, parent). Sorted by
     *  {@code MOD_DTM DESC} so an edited memo bubbles to the top —
     *  legacy {@code comm_0105_001_r002_act.jsp:68} (`order by mod_dtm desc`). */
    List<MemoInfm> findByBizKeyAndWebSvcIdAndRefNoOrderByModDtmDescSeqNoDesc(
            String bizKey, String webSvcId, String refNo);

    /** Every memo across a page of parent rows — one query for the whole
     *  list. Used to surface the per-row memo count + row-level sticker
     *  color on a list grid (Card Transactions memo icon). Grouping /
     *  newest-color selection happens in the service. */
    List<MemoInfm> findByBizKeyAndWebSvcIdAndRefNoIn(
            String bizKey, String webSvcId, java.util.Collection<String> refNos);

    // SEQ_NO allocation is NOT a repository concern — it funnels through
    // LegacyFunctionRepository.nextMemoInfmSeqNo → wb_get_next_val('memo_infm',
    // 'seq_no', biz_key, web_svc_id), matching legacy MemoInfmDAO.create.
    // The former Java-side findMaxSeqNoByBizKeyAndWebSvcId + 1 was removed
    // 2026-07-02 (funnel exception #2 retired).

    /** Row-level color: set {@code COLOR} on <b>every</b> memo sharing
     *  {@code (bizKey, webSvcId, refNo)} — the whole thread on one
     *  parent row shares one sticker color. Mirrors legacy
     *  {@code MEMO_INFO_U002} (`comm_0105_001_u003` / the post-create
     *  {@code updateColor} in {@code comm_0105_001_c002}). Color-only,
     *  no author gate and no audit-column touch (legacy parity). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE MemoInfm m SET m.color = :color " +
           "WHERE m.bizKey = :bizKey AND m.webSvcId = :webSvcId AND m.refNo = :refNo")
    int updateColorByRef(
            @Param("bizKey")   String bizKey,
            @Param("webSvcId") String webSvcId,
            @Param("refNo")    String refNo,
            @Param("color")    String color);
}
