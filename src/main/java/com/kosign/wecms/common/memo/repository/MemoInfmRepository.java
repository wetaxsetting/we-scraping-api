package com.kosign.wecms.common.memo.repository;

import com.kosign.wecms.common.memo.entity.MemoInfm;
import com.kosign.wecms.common.memo.entity.MemoInfmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Polymorphic memo storage — one {@code MEMO_INFM} table shared by every
 * memo-enabled screen, discriminated by {@code WEB_SVC_ID} + the
 * {@code REF_NO..REF_NO9} keys.
 *
 * <p>Two thread shapes:
 * <ul>
 *   <li><b>Single-key</b> (Card Transactions) — {@code refNo = CARD_SEQ_NO},
 *       {@code REF_NO2..9} NULL. Uses the {@code …RefNo…} finders.</li>
 *   <li><b>4-key</b> (Transaction History VND/FCY) — {@code refNo = BANK_CD},
 *       {@code refNo2 = ACCT_NO}, {@code refNo3 = TRSC_DT},
 *       {@code refNo4 = ACCT_SEQ_NO}. Uses the {@code …RefNoAndRefNo2AndRefNo3AndRefNo4…}
 *       finders. {@code ACCT_SEQ_NO} (REF_NO4) is unique per
 *       {@code (bizKey, webSvcId)}, so the grid batch keys on it alone.</li>
 * </ul>
 */
public interface MemoInfmRepository extends JpaRepository<MemoInfm, MemoInfmId> {

    /* ---- single-key (Card Transactions) --------------------------------- */

    /** Every memo for one (tenant, page, parent). Sorted by
     *  {@code MOD_DTM DESC} so an edited memo bubbles to the top —
     *  legacy {@code comm_0105_001_r002_act.jsp:68} (`order by mod_dtm desc`). */
    List<MemoInfm> findByBizKeyAndWebSvcIdAndRefNoOrderByModDtmDescSeqNoDesc(
            String bizKey, String webSvcId, String refNo);

    /** Every memo across a page of single-key parent rows — one query for
     *  the whole list (Card Transactions memo icon count + color). */
    List<MemoInfm> findByBizKeyAndWebSvcIdAndRefNoIn(
            String bizKey, String webSvcId, Collection<String> refNos);

    // SEQ_NO allocation is NOT a repository concern — it funnels through
    // LegacyFunctionRepository.nextMemoInfmSeqNo → wb_get_next_val('memo_infm',
    // 'seq_no', biz_key, web_svc_id), matching legacy MemoInfmDAO.create.

    /** Row-level color (single-key thread): set {@code COLOR} on <b>every</b>
     *  memo sharing {@code (bizKey, webSvcId, refNo)}. Mirrors legacy
     *  {@code MEMO_INFO_U002}. Color-only, no author gate, no audit-column
     *  touch (legacy parity). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE MemoInfm m SET m.color = :color " +
           "WHERE m.bizKey = :bizKey AND m.webSvcId = :webSvcId AND m.refNo = :refNo")
    int updateColorByRef(
            @Param("bizKey")   String bizKey,
            @Param("webSvcId") String webSvcId,
            @Param("refNo")    String refNo,
            @Param("color")    String color);

    /* ---- 4-key (Transaction History VND/FCY) ---------------------------- */

    /** Every memo for one 4-key parent (one bank transaction). Sorted
     *  {@code MOD_DTM DESC, SEQ_NO DESC} — legacy parity. */
    List<MemoInfm>
            findByBizKeyAndWebSvcIdAndRefNoAndRefNo2AndRefNo3AndRefNo4OrderByModDtmDescSeqNoDesc(
                    String bizKey, String webSvcId,
                    String refNo, String refNo2, String refNo3, String refNo4);

    /** Every memo across a page of transaction rows, keyed by
     *  {@code REF_NO4 = ACCT_SEQ_NO} (unique per tenant+screen). One query
     *  for the whole grid — count + row-level sticker color. Grouping /
     *  newest-color selection happens in {@code MemoService}. */
    List<MemoInfm> findByBizKeyAndWebSvcIdAndRefNo4In(
            String bizKey, String webSvcId, Collection<String> refNo4s);

    /** Row-level color (4-key thread): set {@code COLOR} on <b>every</b>
     *  memo sharing all four ref keys — the whole thread on one bank
     *  transaction shares one sticker color. Mirrors legacy
     *  {@code MEMO_INFO_U002} with the {@code ref_no2..ref_no4} dynamic
     *  binding used by {@code fnsh_0002_001} / {@code fnsh_0006_001}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE MemoInfm m SET m.color = :color " +
           "WHERE m.bizKey = :bizKey AND m.webSvcId = :webSvcId " +
           "AND m.refNo = :refNo AND m.refNo2 = :refNo2 " +
           "AND m.refNo3 = :refNo3 AND m.refNo4 = :refNo4")
    int updateColorByRefKeys(
            @Param("bizKey")   String bizKey,
            @Param("webSvcId") String webSvcId,
            @Param("refNo")    String refNo,
            @Param("refNo2")   String refNo2,
            @Param("refNo3")   String refNo3,
            @Param("refNo4")   String refNo4,
            @Param("color")    String color);
}
