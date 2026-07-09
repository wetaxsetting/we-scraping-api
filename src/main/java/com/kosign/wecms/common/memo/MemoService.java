package com.kosign.wecms.common.memo;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.memo.dto.MemoListResponse;
import com.kosign.wecms.common.memo.dto.MemoRow;
import com.kosign.wecms.common.memo.entity.MemoInfm;
import com.kosign.wecms.common.memo.entity.MemoInfmId;
import com.kosign.wecms.common.memo.repository.MemoInfmRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.repository.UserInfmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Shared memo service over the polymorphic {@code MEMO_INFM} table.
 * One implementation of the legacy {@code comm_0105_001} memo behavior
 * (list / create / edit / color / delete + grid rollup), parameterized
 * by {@link MemoScope} + {@link MemoRefKey} so every memo-enabled screen
 * reuses it instead of duplicating the logic.
 *
 * <p>Consumers today: Card Transactions (single-key, {@code REF_NO =
 * CARD_SEQ_NO}) and Transaction History VND/FCY (4-key —
 * {@code BANK_CD / ACCT_NO / TRSC_DT / ACCT_SEQ_NO}). Legacy parity:
 * <ul>
 *   <li>list ordered {@code MOD_DTM DESC, SEQ_NO DESC}
 *       ({@code comm_0105_001_r002});</li>
 *   <li>create + row-level color propagation across the whole thread
 *       ({@code comm_0105_001_c002} → {@code updateColor});</li>
 *   <li>edit-text author-only ({@code comm_0105_001_u002} →
 *       {@code WBE_MEMO_NOT_AUTHOR});</li>
 *   <li>color-only bulk update, no author gate
 *       ({@code comm_0105_001_u003} / {@code MEMO_INFO_U002});</li>
 *   <li>delete author-only ({@code comm_0105_001_d002});</li>
 *   <li>{@code SEQ_NO} via the {@code wb_get_next_val('memo_infm','seq_no',
 *       biz_key, web_svc_id)} funnel with a one-shot retry on PK collision.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoService {

    /** Legacy sticker "no color" token. */
    public static final String DEFAULT_COLOR = "fileW";

    private static final DateTimeFormatter DTM14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final MemoInfmRepository memoRepo;
    private final UserInfmRepository userRepo;
    private final LegacyFunctionRepository legacyFunctions;

    /* ================================================================
     *  list
     * ================================================================ */

    /** Every memo for one parent thread, newest-first. Author names are
     *  batch-resolved from {@code USER_INFM}; {@code canEdit} is set for
     *  the request user's own memos. */
    @Transactional(readOnly = true)
    public MemoListResponse list(MemoScope scope, MemoRefKey key) {
        String bizKey = TenantContext.currentBizKey();
        String userId = requireUserId();
        String webSvcId = scope.webSvcId();

        List<MemoInfm> rows = key.isSingle()
                ? memoRepo.findByBizKeyAndWebSvcIdAndRefNoOrderByModDtmDescSeqNoDesc(
                        bizKey, webSvcId, key.refNo())
                : memoRepo.findByBizKeyAndWebSvcIdAndRefNoAndRefNo2AndRefNo3AndRefNo4OrderByModDtmDescSeqNoDesc(
                        bizKey, webSvcId, key.refNo(), key.refNo2(), key.refNo3(), key.refNo4());

        Set<String> userIds = rows.stream()
                .map(MemoInfm::getRegId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> userNmById = new HashMap<>();
        if (!userIds.isEmpty()) {
            userRepo.findAllById(userIds).forEach(u -> {
                if (u.getUserNm() != null) userNmById.put(u.getUserId(), u.getUserNm());
            });
        }

        List<MemoRow> list = rows.stream()
                .map(m -> new MemoRow(
                        m.getSeqNo(),
                        m.getMemo(),
                        m.getColor() != null ? m.getColor() : DEFAULT_COLOR,
                        m.getRegId(),
                        userNmById.getOrDefault(m.getRegId(), m.getRegId()),
                        m.getRegDtm(),
                        userId.equals(m.getRegId())))
                .toList();
        return new MemoListResponse(list.size(), list);
    }

    /* ================================================================
     *  create
     * ================================================================ */

    /** Add a memo to one parent thread. Allocates {@code SEQ_NO} through
     *  the {@code wb_get_next_val} funnel (one-shot retry on PK
     *  collision), then propagates the chosen color across the whole
     *  thread (legacy {@code c002} → {@code updateColor}). */
    @Transactional
    public MemoRow create(MemoScope scope, MemoRefKey key, String memo, String color) {
        String bizKey = TenantContext.currentBizKey();
        String userId = requireUserId();
        String now    = nowDtm14();
        String webSvcId = scope.webSvcId();
        String stickerColor = (color == null || color.isBlank()) ? DEFAULT_COLOR : color.trim();
        String memoText = memo != null ? memo.trim() : "";

        MemoInfm m;
        try {
            m = insertOnce(bizKey, webSvcId, key, memoText, stickerColor, userId, now);
        } catch (DataIntegrityViolationException race) {
            log.info("feature=memo seq race retrying once webSvcId={} bizKey={}", webSvcId, bizKey);
            m = insertOnce(bizKey, webSvcId, key, memoText, stickerColor, userId, now);
        }

        // Row-level color — propagate to the whole thread on this parent.
        propagateColor(bizKey, webSvcId, key, stickerColor);

        String userNm = TenantContext.currentUserNm();
        return new MemoRow(
                m.getSeqNo(), m.getMemo(), stickerColor, userId,
                userNm != null ? userNm : userId, m.getRegDtm(), true);
    }

    private MemoInfm insertOnce(String bizKey, String webSvcId, MemoRefKey key,
                                String memoText, String color, String userId, String now) {
        String nextText = legacyFunctions.nextMemoInfmSeqNo(bizKey, webSvcId);
        BigDecimal nextSeq = new BigDecimal(nextText.trim());

        MemoInfm m = new MemoInfm();
        m.setBizKey(bizKey);
        m.setWebSvcId(webSvcId);
        m.setSeqNo(nextSeq);
        m.setMemo(memoText);
        m.setColor(color);
        m.setRefNo(key.refNo());
        m.setRefNo2(key.refNo2());
        m.setRefNo3(key.refNo3());
        m.setRefNo4(key.refNo4());
        m.setRegId(userId);
        m.setRegDtm(now);
        m.setModId(userId);
        m.setModDtm(now);
        return memoRepo.save(m);
    }

    /* ================================================================
     *  edit text (author-only)
     * ================================================================ */

    /** Edit one memo's text — author-only ({@code WBE_MEMO_NOT_AUTHOR}).
     *  Touches {@code MOD_ID / MOD_DTM} so the edited memo bubbles to the
     *  top of the {@code MOD_DTM DESC} list. Color untouched. */
    @Transactional
    public MemoRow update(MemoScope scope, BigDecimal seqNo, String memo) {
        if (memo == null || memo.isBlank()) throw new BusinessException("WBE_VALIDATION");
        String bizKey = TenantContext.currentBizKey();
        String userId = requireUserId();

        MemoInfm m = memoRepo.findById(new MemoInfmId(bizKey, scope.webSvcId(), seqNo))
                .orElseThrow(() -> new BusinessException("WBE_NOT_FOUND", "memo not found"));
        if (!userId.equals(m.getRegId())) {
            throw new BusinessException("WBE_MEMO_NOT_AUTHOR");
        }

        String now = nowDtm14();
        m.setMemo(memo.trim());
        m.setModId(userId);
        m.setModDtm(now);
        memoRepo.save(m);

        String userNm = TenantContext.currentUserNm();
        return new MemoRow(
                m.getSeqNo(), m.getMemo(),
                m.getColor() != null ? m.getColor() : DEFAULT_COLOR,
                m.getRegId(), userNm != null ? userNm : m.getRegId(),
                m.getRegDtm(), true);
    }

    /* ================================================================
     *  color (row-level, no author gate)
     * ================================================================ */

    /** Set the row-level sticker color for a whole thread — no author
     *  gate (legacy {@code u003} parity). Idempotent when the thread has
     *  no memos yet (0 rows updated). */
    @Transactional
    public void updateColor(MemoScope scope, MemoRefKey key, String color) {
        String bizKey = TenantContext.currentBizKey();
        String stickerColor = (color == null || color.isBlank()) ? DEFAULT_COLOR : color.trim();
        propagateColor(bizKey, scope.webSvcId(), key, stickerColor);
    }

    private void propagateColor(String bizKey, String webSvcId, MemoRefKey key, String color) {
        if (key.isSingle()) {
            memoRepo.updateColorByRef(bizKey, webSvcId, key.refNo(), color);
        } else {
            memoRepo.updateColorByRefKeys(bizKey, webSvcId,
                    key.refNo(), key.refNo2(), key.refNo3(), key.refNo4(), color);
        }
    }

    /* ================================================================
     *  delete (author-only)
     * ================================================================ */

    /** Hard-delete one memo — author-only ({@code WBE_MEMO_NOT_AUTHOR}).
     *  Returns silently when the memo doesn't exist (idempotent). */
    @Transactional
    public void delete(MemoScope scope, BigDecimal seqNo) {
        String bizKey = TenantContext.currentBizKey();
        String userId = requireUserId();
        memoRepo.findById(new MemoInfmId(bizKey, scope.webSvcId(), seqNo)).ifPresent(m -> {
            if (!userId.equals(m.getRegId())) {
                throw new BusinessException("WBE_MEMO_NOT_AUTHOR");
            }
            memoRepo.delete(m);
        });
    }

    /* ================================================================
     *  grid rollup (count + row-level color per parent)
     * ================================================================ */

    /** Batch rollup for a single-key list grid (Card Transactions) —
     *  keyed by {@code REF_NO}. */
    @Transactional(readOnly = true)
    public Map<String, MemoAggregate> aggregateBySingleRef(
            MemoScope scope, String bizKey, Collection<String> refNos) {
        List<String> keys = distinctNonBlank(refNos);
        if (keys.isEmpty()) return Map.of();
        List<MemoInfm> memos = memoRepo.findByBizKeyAndWebSvcIdAndRefNoIn(
                bizKey, scope.webSvcId(), keys);
        return rollup(memos, MemoInfm::getRefNo);
    }

    /** Batch rollup for a 4-key list grid (Transaction History VND/FCY) —
     *  keyed by {@code REF_NO4 = ACCT_SEQ_NO} (unique per tenant+screen). */
    @Transactional(readOnly = true)
    public Map<String, MemoAggregate> aggregateByRefNo4(
            MemoScope scope, String bizKey, Collection<String> refNo4s) {
        List<String> keys = distinctNonBlank(refNo4s);
        if (keys.isEmpty()) return Map.of();
        List<MemoInfm> memos = memoRepo.findByBizKeyAndWebSvcIdAndRefNo4In(
                bizKey, scope.webSvcId(), keys);
        return rollup(memos, MemoInfm::getRefNo4);
    }

    private Map<String, MemoAggregate> rollup(List<MemoInfm> memos, Function<MemoInfm, String> keyFn) {
        Map<String, MemoAggregate> out = new HashMap<>();
        memos.stream()
                .filter(m -> keyFn.apply(m) != null)
                .collect(Collectors.groupingBy(keyFn))
                .forEach((k, list) -> {
                    String color = list.stream()
                            .max(Comparator
                                    .comparing((MemoInfm m) -> m.getModDtm() == null ? "" : m.getModDtm())
                                    .thenComparing(m -> m.getSeqNo() == null ? BigDecimal.ZERO : m.getSeqNo()))
                            .map(MemoInfm::getColor)
                            .orElse(null);
                    out.put(k, new MemoAggregate(list.size(), color != null ? color : DEFAULT_COLOR));
                });
        return out;
    }

    private static List<String> distinctNonBlank(Collection<String> in) {
        if (in == null) return List.of();
        return in.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();
    }

    private static String requireUserId() {
        String userId = TenantContext.currentUserId();
        if (userId == null) {
            throw new IllegalStateException(
                    "TenantContext.userId is not populated — endpoint requires authentication");
        }
        return userId;
    }

    private static String nowDtm14() {
        return OffsetDateTime.now(ZoneOffset.UTC).format(DTM14);
    }
}
