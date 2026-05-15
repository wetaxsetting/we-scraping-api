package com.kosign.wecms.domain.payment.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.dto.OverseasApprover;
import com.kosign.wecms.domain.payment.dto.OverseasCurrencyTotal;
import com.kosign.wecms.domain.payment.dto.OverseasResultListResponse;
import com.kosign.wecms.domain.payment.dto.OverseasResultRow;
import com.kosign.wecms.domain.payment.dto.OverseasScheduledListResponse;
import com.kosign.wecms.domain.payment.dto.OverseasScheduledRow;
import com.kosign.wecms.domain.payment.repository.OverseasApproverRepository;
import com.kosign.wecms.domain.payment.repository.OverseasPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Overseas-payment scheduled-payment list service.
 *
 * <p>Mirrors the legacy {@code pymn_0007_001_r001} read path:
 * <ul>
 *   <li>Paged UNION ALL query across {@code OVRS_PYMN_SUMR} (type E) and
 *       {@code OVRS_PYMN_MSTR} (type M) joined to {@code EXPT_RESOL_MSTR}.</li>
 *   <li>Per-row approver list from {@code EXPT_RESOL_APPRVR_INFM} (batch
 *       fetch to avoid N+1).</li>
 *   <li>Per-currency totals (mirrors IDO.OVRS_PYMN_SUMR_R003).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OverseasPaymentService {

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE     = 100;

    private final OverseasPaymentRepository paymentRepo;
    private final OverseasApproverRepository approverRepo;

    /**
     * Overseas scheduled-payment list.
     *
     * @param fromDt      required, yyyyMMdd
     * @param toDt        required, yyyyMMdd
     * @param apprTypeCds optional approval-status filter
     * @param keyword     optional search term (matched against file title / beneficiary name)
     * @param page        zero-based page index
     * @param size        rows per page (0 = use default)
     */
    public OverseasScheduledListResponse list(
            String fromDt,
            String toDt,
            List<String> apprTypeCds,
            String keyword,
            int page,
            int size) {

        if (fromDt == null || fromDt.isBlank()) {
            throw new BusinessException("WBE_VALIDATION", "FROM_DT is required");
        }
        if (toDt == null || toDt.isBlank()) {
            throw new BusinessException("WBE_VALIDATION", "TO_DT is required");
        }

        String bizKey = TenantContext.currentBizKey();

        int pageSize   = resolvePageSize(size);
        int pageIndex  = Math.max(0, page);
        int offset     = pageIndex * pageSize;

        String likePattern = buildLikePattern(keyword);

        long totalCount = paymentRepo.countScheduled(bizKey, fromDt, toDt, apprTypeCds, likePattern);

        List<Object[]> rawRows = totalCount == 0
                ? List.of()
                : paymentRepo.findScheduled(bizKey, fromDt, toDt, apprTypeCds, likePattern, offset, pageSize);

        // Batch-fetch approvers for all rows on this page
        List<String> apprSeqNos = rawRows.stream()
                .map(r -> (String) r[11])
                .filter(seq -> seq != null)
                .distinct()
                .collect(Collectors.toList());

        Map<String, List<Object[]>> approversBySeq =
                approverRepo.findByApprSeqNos(bizKey, apprSeqNos);

        List<OverseasScheduledRow> rows = rawRows.stream()
                .map(r -> mapRow(r, approversBySeq))
                .collect(Collectors.toList());

        // Currency totals (same filter as the paged query)
        List<Object[]> rawTotals = paymentRepo.sumByCurrency(bizKey, fromDt, toDt, apprTypeCds, likePattern);
        List<OverseasCurrencyTotal> totals = rawTotals.stream()
                .map(this::mapTotal)
                .collect(Collectors.toList());

        return new OverseasScheduledListResponse(totalCount, pageIndex, pageSize, rows, totals);
    }

    // ─── single row ───────────────────────────────────────────────────────────

    /**
     * Fetch a single scheduled-payment row by {@code OVRS_PYMN_NO}.
     *
     * @throws BusinessException {@code WBE_NOT_FOUND} when the id is unknown or belongs to a different tenant
     */
    public OverseasScheduledRow getById(String ovrsPymnNo) {
        String bizKey = TenantContext.currentBizKey();
        Object[] r = paymentRepo.findScheduledById(bizKey, ovrsPymnNo)
                .orElseThrow(() -> new BusinessException("WBE_NOT_FOUND",
                        "overseas payment not found: " + ovrsPymnNo));
        return mapRow(r, java.util.Map.of());
    }

    // ─── result list ──────────────────────────────────────────────────────────

    public OverseasResultListResponse listResult(
            String fromDt,
            String toDt,
            String keyword,
            int page,
            int size) {

        if (fromDt == null || fromDt.isBlank()) throw new BusinessException("WBE_VALIDATION", "FROM_DT is required");
        if (toDt   == null || toDt.isBlank())   throw new BusinessException("WBE_VALIDATION", "TO_DT is required");

        String bizKey     = TenantContext.currentBizKey();
        int    pageSize   = resolvePageSize(size);
        int    pageIndex  = Math.max(0, page);
        int    offset     = pageIndex * pageSize;
        String likePat    = buildLikePattern(keyword);

        long totalCount = paymentRepo.countResult(bizKey, fromDt, toDt, likePat);

        java.util.List<Object[]> rawRows = totalCount == 0
                ? java.util.List.of()
                : paymentRepo.findResult(bizKey, fromDt, toDt, likePat, offset, pageSize);

        java.util.List<OverseasResultRow> rows = rawRows.stream()
                .map(this::mapResultRow)
                .collect(java.util.stream.Collectors.toList());

        java.util.List<Object[]> rawTotals = paymentRepo.sumResultByCurrency(bizKey, fromDt, toDt, likePat);
        java.util.List<OverseasCurrencyTotal> totals = rawTotals.stream()
                .map(this::mapTotal)
                .collect(java.util.stream.Collectors.toList());

        return new OverseasResultListResponse(totalCount, pageIndex, pageSize, rows, totals);
    }

    public OverseasResultRow getResultById(String ovrsPymnNo) {
        String bizKey = TenantContext.currentBizKey();
        Object[] r = paymentRepo.findResultById(bizKey, ovrsPymnNo)
                .orElseThrow(() -> new BusinessException("WBE_NOT_FOUND",
                        "overseas payment result not found: " + ovrsPymnNo));
        return mapResultRow(r);
    }

    // ─── mappers ──────────────────────────────────────────────────────────────

    private OverseasScheduledRow mapRow(Object[] r, Map<String, List<Object[]>> approversBySeq) {
        String apprSeqNo = (String) r[11];

        List<OverseasApprover> approvers = approversBySeq
                .getOrDefault(apprSeqNo, List.of())
                .stream()
                .map(this::mapApprover)
                .collect(Collectors.toList());

        Number apprvrTotCnt     = (Number) r[24];
        Number apprvrApprTotCnt = (Number) r[25];

        return new OverseasScheduledRow(
                (String)     r[2],                                     // ovrsPymnNo
                (String)     r[6],                                     // ccyCd
                (String)     r[3],                                     // ovrsPymnTypeCd
                (String)     r[5],                                     // titl
                apprSeqNo,                                             // apprSeqNo
                toBigDecimal(r[7]),                                     // totalTranAmt
                toInt(r[8]),                                            // totTrnsCnt
                (String)     r[9],                                     // regDtm
                (String)     r[12],                                    // draftUserId
                (String)     r[13],                                    // draftUserNm
                (String)     r[15],                                    // apprStts
                (String)     r[17],                                    // trnfYn
                apprvrApprTotCnt != null ? apprvrApprTotCnt.intValue() : 0,
                apprvrTotCnt     != null ? apprvrTotCnt.intValue()     : 0,
                (String)     r[18],                                    // apprDv
                (String)     r[14],                                    // docNo
                null,                                                  // fileUrl (not in base query for R&D)
                (String)     r[19],                                    // outBankCd
                (String)     r[21],                                    // outBankNm
                (String)     r[20],                                    // wdrlActNo
                (String)     r[22],                                    // bnfcNm
                (String)     r[1],                                     // ovrsPymnType ('E'/'M')
                (String)     r[23],                                    // payDate
                (String)     r[4],                                     // fileNm
                (String)     r[10],                                    // regId
                approvers
        );
    }

    private OverseasApprover mapApprover(Object[] r) {
        return new OverseasApprover(
                (String) r[2],  // apprvrId
                (String) r[3],  // apprvrNm
                (String) r[4],  // apprvrStts
                (String) r[1],  // apprvrLineNo
                (String) r[5],  // modDtm
                (String) r[6]   // regImgUrl
        );
    }

    private OverseasCurrencyTotal mapTotal(Object[] r) {
        return new OverseasCurrencyTotal(
                (String) r[0],   // ccyCd
                toBigDecimal(r[1]),  // totlAmt
                toInt(r[2]),         // totCntCcyCd (row count)
                toInt(r[3])          // totTrnsCnt
        );
    }

    /** Maps a result-list query row (see {@link com.kosign.wecms.domain.payment.repository.OverseasPaymentRepository#buildResultSql}). */
    private OverseasResultRow mapResultRow(Object[] r) {
        return new OverseasResultRow(
                (String)     r[0],   // ovrsPymnNo
                (String)     r[1],   // ccyCd
                (String)     r[2],   // fileNm (titl)
                toBigDecimal(r[3]),  // totalTranAmt
                toInt(r[4]),         // totTrnsCnt
                (String)     r[5],   // regDtm
                (String)     r[6],   // draftUserNm
                (String)     r[7],   // regId
                (String)     r[8],   // outBankNm
                (String)     r[9],   // outBankCd
                (String)     r[10],  // wdrlActNo
                (String)     r[11],  // trnfYn
                (String)     r[12],  // docNo
                (String)     r[13],  // titl (approval title)
                0,                   // successCount — not yet tracked at batch level
                0                    // failedCount  — not yet tracked at batch level
        );
    }

    // ─── utilities ────────────────────────────────────────────────────────────

    private int resolvePageSize(int requested) {
        if (requested <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    /**
     * Escapes special LIKE metacharacters in user input and wraps with %
     * to produce an ILIKE pattern. Mirrors the JEX escaping convention
     * (escape char = '!'). Returns null when keyword is blank.
     */
    private String buildLikePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String escaped = keyword
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped.toLowerCase() + "%";
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        return new BigDecimal(val.toString());
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        return Integer.parseInt(val.toString());
    }
}
