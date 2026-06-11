package com.kosign.wecms.domain.banking.transferhistory.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.auth.entity.UserInfm;
import com.kosign.wecms.domain.auth.repository.UserInfmRepository;
import com.kosign.wecms.domain.banking.entity.NapasRemitTrnsHstr;
import com.kosign.wecms.domain.banking.service.BankCdResolver;
import com.kosign.wecms.domain.banking.transferhistory.dto.TransferHistoryListRequest;
import com.kosign.wecms.domain.banking.transferhistory.dto.TransferHistoryListResponse;
import com.kosign.wecms.domain.banking.transferhistory.dto.TransferHistoryRow;
import com.kosign.wecms.domain.banking.transferhistory.dto.TransferHistorySortField;
import com.kosign.wecms.domain.banking.transferhistory.repository.TransferHistoryRepository;
import com.kosign.wecms.domain.banking.transferhistory.repository.TransferHistoryTotals;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Banking &gt; Internal Transfer &gt; Transfer History — read-only list
 * service. Mirrors legacy {@code trns_0003_001_r001} in
 * {@code WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0003_001_r001_act.jsp:32-159}.
 *
 * <p>The Sprint-1 interim {@code TransferHistoryService} +
 * {@code /api/v1/banking/transfers/history} endpoint (UPPER_SNAKE wire)
 * that this superseded was deleted 2026-06-07. This service is now the
 * sole Transfer History query path, backing the spec-aligned endpoint
 * {@code /api/v1/banking/internal-transfer/transfer-history} with the
 * lowercase snake_case wire shape mandated by the 2026-05-08 CTO
 * directive.
 *
 * <p>Tenant scoping: every read constrains on
 * {@link TenantContext#currentBizKey()} as a Specification predicate.
 * Request body / query / path parameters never carry {@code biz_key}.
 *
 * <p>Audit-log: <b>NO writes</b> for this endpoint — OQ-4 resolved
 * 2026-05-20 (legacy parity; the JEX framework's per-call
 * {@code mntLogYn=Y} log is a framework attribute the refactor has no
 * equivalent of). Deliberately matches the
 * {@code approval-line / erp-transaction-history / chart-of-accounts}
 * read-side precedents.
 *
 * <p>Bank-name resolution (OQ-1 resolved 2026-05-20):
 * <ul>
 *   <li>{@code out_bank_nm} — {@link BankCdResolver} (reads BANK_INFM
 *       seeded V20).</li>
 *   <li>{@code in_bank_nm} — {@link LegacyFunctionRepository#resolveNapasOrCitadBankName}
 *       (V86 surviving legacy function, Contacts precedent). Used
 *       because the {@code BANK_INFM} coverage of the NAPAS + CITAD
 *       code space is unverified for this flow.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferHistoryQueryService {

    /** Default ORDER BY for the list endpoint:
     *  {@code reg_dtm DESC, seq_no DESC}. The second clause is a
     *  deterministic tie-break for rows that share a timestamp. */
    static final Sort DEFAULT_SORT =
            Sort.by(Sort.Order.desc("regDtm"), Sort.Order.desc("seqNo"));

    /** Status code returned by the Coocon engine on success.
     *  Anything else is a failure code (engine-specific catalogue
     *  echoed verbatim to the wire — refactor does not parse it). */
    static final String STATUS_SUCCESS = "00000000";

    /** Sentinel the legacy view radios emit when the operator picks
     *  "Failed" (any non-success status). Translates to a {@code
     *  TRNS_STTS &lt;&gt; '00000000'} predicate at the DB layer per
     *  legacy {@code trns_0003_001_r001_act.jsp:73-81}. */
    static final String STATUS_FAILED_SENTINEL = "99999999";

    /** Display timezone for the date/time columns. The {@code REG_DTM}
     *  column is stored UTC ({@code yyyyMMddHHmmss}); legacy
     *  {@code trns_0003_001_r001_act.jsp:101-105} normalizes it to the
     *  operator's timezone (via {@code DateTimeUtils.normalizeResult})
     *  before splitting into {@code TRNS_DT} / {@code TRNS_TM}. The
     *  refactor pins the same project display zone the sibling Own
     *  Account Transfer service uses ({@code Asia/Ho_Chi_Minh}, UTC+7). */
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter REG_DTM_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TransferHistoryRepository repo;
    private final UserInfmRepository userRepo;
    private final BankCdResolver bankCdResolver;
    private final LegacyFunctionRepository legacyFn;

    /**
     * List transfer history rows for the current tenant within the
     * inclusive date range, optionally narrowed by status. The page
     * is sorted by the whitelist field + direction or by the default
     * ORDER BY when either is missing / invalid.
     *
     * @throws BusinessException {@code WBE_TRANSFER_HISTORY_INVALID_PERIOD}
     *         when {@code to_dt &lt; from_dt}.
     */
    @Transactional(readOnly = true)
    public TransferHistoryListResponse list(TransferHistoryListRequest req) {
        String bizKey = TenantContext.currentBizKey();
        String fromDt = req.fromDt();
        String toDt   = req.toDt();

        if (toDt.compareTo(fromDt) < 0) {
            throw new BusinessException("WBE_TRANSFER_HISTORY_INVALID_PERIOD");
        }

        String statusEq  = STATUS_SUCCESS.equals(req.trnsStts()) ? STATUS_SUCCESS : null;
        String statusNeq = STATUS_FAILED_SENTINEL.equals(req.trnsStts()) ? STATUS_SUCCESS : null;

        Specification<NapasRemitTrnsHstr> spec = buildSpec(bizKey, fromDt, toDt, statusEq, statusNeq);

        int pageSize = parsePageSize(req.pagePerCnt());
        int pageNo   = req.pageNo() == null ? 1 : Math.max(1, req.pageNo());

        Sort sort = buildSort(
                TransferHistorySortField.parse(req.sortField()).orElse(null),
                SortDirection.parse(req.sortDirection()));
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize, sort);

        Page<NapasRemitTrnsHstr> page = repo.findAll(spec, pageable);

        // One funnel call per request — language pinned to the operator
        // locale (V86 function returns NULL outside {en, vi} so we
        // collapse Korean / Khmer to English).
        String langForBankFn = legacyLangCd(TenantContext.currentLangCd());

        Map<String, String> outBankNmCache = new HashMap<>();
        Map<String, String> inBankNmCache  = new HashMap<>();
        Map<String, String> userNmCache    = resolveUserNms(page.getContent());

        List<TransferHistoryRow> rows = new ArrayList<>(page.getNumberOfElements());
        for (NapasRemitTrnsHstr r : page.getContent()) {
            rows.add(toRow(r, outBankNmCache, inBankNmCache, userNmCache, langForBankFn));
        }

        TransferHistoryTotals totals = repo.aggregateTotals(bizKey, fromDt, toDt, statusEq, statusNeq);

        log.debug("[transfer-history] bizKey={} from={} to={} status={} rows={}/{}",
                bizKey, fromDt, toDt, req.trnsStts(), rows.size(), totals.totalCount());

        return new TransferHistoryListResponse(
                totals.totalCount(),
                totals.totalVndAmount(),
                totals.totalUsdAmount(),
                totals.totalVndCount(),
                totals.totalUsdCount(),
                rows);
    }

    /* ---------------------------------------------------------------
     *  Internals
     * --------------------------------------------------------------- */

    private static Specification<NapasRemitTrnsHstr> buildSpec(
            String bizKey, String fromDt, String toDt, String statusEq, String statusNeq) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>(4);
            preds.add(cb.equal(root.get("bizKey"), bizKey));
            // substring(REG_DTM, 1, 8) BETWEEN :fromDt AND :toDt — mirrors
            // trns_0003_001_r001_act.jsp:68.
            preds.add(cb.between(cb.substring(root.get("regDtm"), 1, 8), fromDt, toDt));
            if (statusEq != null) {
                preds.add(cb.equal(root.get("trnsStts"), statusEq));
            } else if (statusNeq != null) {
                // "non-success" matches both "not equal" AND legacy nulls — keep null safety.
                Predicate isNull   = cb.isNull(root.get("trnsStts"));
                Predicate notEq    = cb.notEqual(root.get("trnsStts"), statusNeq);
                preds.add(cb.or(isNull, notEq));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    private static Sort buildSort(TransferHistorySortField field, SortDirection direction) {
        if (field == null) return DEFAULT_SORT;
        Sort.Direction springDir = direction == SortDirection.ASC
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        // Always tie-break on seqNo DESC so paging is stable.
        return Sort.by(new Sort.Order(springDir, field.entityProperty()))
                .and(Sort.by(Sort.Order.desc("seqNo")));
    }

    private static int parsePageSize(String raw) {
        if (raw == null || raw.isBlank()) {
            return TransferHistoryListRequest.DEFAULT_PAGE_PER_CNT;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            // Bean validation would already have rejected this; defensive.
            return TransferHistoryListRequest.DEFAULT_PAGE_PER_CNT;
        }
    }

    /** Map the JWT-resolved {@link LangCd} to the lowercase code the
     *  legacy {@code wb_get_napas_bank_nm_or_citad_bank_nm} branches
     *  on. The DEV function has branches only for {@code "en"} /
     *  {@code "vi"} — Korean and Khmer fall back to English so the
     *  funnel never returns null because of a language mismatch. */
    private static String legacyLangCd(LangCd langCd) {
        if (langCd == null) return "en";
        return switch (langCd) {
            case VI -> "vi";
            case EN, KO, KM -> "en";
        };
    }

    private TransferHistoryRow toRow(
            NapasRemitTrnsHstr r,
            Map<String, String> outBankNmCache,
            Map<String, String> inBankNmCache,
            Map<String, String> userNmCache,
            String langForBankFn) {

        // G1 parity: normalize the UTC-stored REG_DTM to the operator
        // display zone BEFORE splitting, mirroring legacy
        // trns_0003_001_r001_act.jsp:101-105 (TRNS_DT = local date,
        // TRNS_TM = local HHmm). The date FILTER (buildSpec) deliberately
        // stays on the raw UTC string — legacy filters raw, displays local.
        String regDtm = toDisplayZone14(r.getRegDtm());
        String trnsDt = regDtm.length() >= 8  ? regDtm.substring(0, 8)  : "";
        String trnsTm = regDtm.length() >= 12 ? regDtm.substring(8, 12) : "";

        String outBankNm = outBankNmCache.computeIfAbsent(
                nullSafe(r.getOutBankCode()),
                code -> code.isEmpty() ? "" : bankCdResolver.resolveBankNm(code));
        // G5/U2 fix: resolve in_bank_nm from the NAPAS code (IN_NAPAS_BANK_CODE),
        // NOT the internal IN_BANK_CODE. The resolver reads tb_firm_bank_code,
        // which is keyed by NAPAS codes (970xxx) — feeding it the internal
        // 11704xxx code matched no row and rendered the To-Bank name blank for
        // every row. The wire in_bank_code (below) also carries the NAPAS code
        // (G4), so the resolved name and the emitted code stay on the same code
        // space. Legacy resolved the name from its internal IN_BANK_CODE via a
        // different table (napas_bank_info) — the refactor's chosen table forces
        // the NAPAS code as the only key that yields a non-empty name.
        String inBankNm = inBankNmCache.computeIfAbsent(
                nullSafe(r.getInNapasBankCode()),
                code -> code.isEmpty() ? "" : nullToEmpty(legacyFn.resolveNapasOrCitadBankName(code, langForBankFn)));

        String userNm = userNmCache.getOrDefault(nullSafe(r.getRegId()), "");

        Long seqNo = r.getSeqNo() == null ? null : r.getSeqNo().longValueExact();
        BigDecimal trscAmt = r.getTrscAmt() == null ? BigDecimal.ZERO : r.getTrscAmt();

        return new TransferHistoryRow(
                seqNo,
                trnsDt,
                trnsTm,
                nullSafe(r.getOutBankCode()),
                outBankNm,
                nullSafe(r.getOutAcctNo()),
                // G4 parity: the wire in_bank_code carries IN_NAPAS_BANK_CODE,
                // matching the legacy IDO alias (IN_NAPAS_BANK_CODE AS
                // IN_BANK_CODE — R003.xml:46). The name lookup above still
                // uses the internal IN_BANK_CODE (R003.xml:43), exactly as
                // legacy resolves wb_get_napas_bank_nm on the internal code.
                nullSafe(r.getInNapasBankCode()),
                inBankNm,
                nullSafe(r.getInAcctNo()),
                nullSafe(r.getOwacNm()),
                r.getCcyCd() == null ? "" : r.getCcyCd().trim(),
                trscAmt,
                r.getFee(),
                r.getTransType(),
                r.getRmrk(),
                r.getTrnsStts(),
                r.getTrnsMsg(),
                userNm);
    }

    /** Single batched lookup over {@code USER_INFM} for every distinct
     *  {@code REG_ID} on the page — replaces the legacy IDO's
     *  {@code WB_GET_USER_NM(REG_ID)} function call. */
    private Map<String, String> resolveUserNms(List<NapasRemitTrnsHstr> rows) {
        List<String> ids = rows.stream()
                .map(NapasRemitTrnsHstr::getRegId)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();
        if (ids.isEmpty()) return Map.of();
        Map<String, String> out = new HashMap<>(ids.size());
        for (UserInfm u : userRepo.findAllById(ids)) {
            if (u.getUserId() != null && u.getUserNm() != null) {
                out.put(u.getUserId(), u.getUserNm());
            }
        }
        return out;
    }

    /**
     * Convert a UTC-stored {@code yyyyMMddHHmmss} string to the
     * {@link #DISPLAY_ZONE} (UTC+7) for display. Mirrors the sibling
     * Own Account Transfer service's {@code toDisplayZone14} and legacy
     * {@code DateTimeUtils.normalizeResult(dtm, loginUser)}. Null / blank
     * input returns {@code ""}; an input shorter than 14 chars or one
     * that fails to parse is returned trimmed-and-unchanged so the
     * downstream substring split degrades gracefully instead of throwing.
     */
    private static String toDisplayZone14(String utc14) {
        if (utc14 == null || utc14.isBlank()) return "";
        String trimmed = utc14.trim();
        if (trimmed.length() < 14) return trimmed;
        try {
            return LocalDateTime.parse(trimmed.substring(0, 14), REG_DTM_FMT)
                    .atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(DISPLAY_ZONE)
                    .format(REG_DTM_FMT);
        } catch (DateTimeParseException ex) {
            return trimmed;
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
