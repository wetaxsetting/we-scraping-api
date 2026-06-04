package com.kosign.wecms.domain.einvoice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.banking.synchistory.entity.ExtTranCallHis;
import com.kosign.wecms.domain.banking.synchistory.repository.ExtTranCallHisRepository;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceDetailHistoryResponse;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceDetailHistoryRow;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceHistoryResponse;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceHistoryRow;
import com.kosign.wecms.domain.einvoice.entity.BusaDocMstr;
import com.kosign.wecms.domain.einvoice.repository.BusaDocMstrRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * e-Invoice "Latest Inquiry History" reader — refactor port of legacy
 * {@code nxisas_einvhis_r001_act.jsp}. Reads back the GDT scrape calls
 * recorded in {@code EXT_TRAN_CALL_HIS} (written by
 * {@link EInvoiceScrapeService} phase 1) so the History popup can show
 * when each inquiry ran and whether it succeeded.
 *
 * <p>Reuses the shared {@link ExtTranCallHisRepository} (same cross-domain
 * access {@link EInvoiceScrapeService} already uses to write the audit
 * row). Read-only — touches no scrape / detail / master logic.
 *
 * <p>Legacy parity:
 * <ul>
 *   <li>Filter: {@code SUBSTRING(REG_DTM,1,8)} window + {@code BIZ_KEY}
 *       + {@code SVC_ID LIKE} ({@code GDT_INQUIRY%} by default).</li>
 *   <li>{@code STTS} 0/1 → success/fail filter.</li>
 *   <li>{@code normalizeResult}: when {@code ERR_CONT} is a JSON object,
 *       surface {@code Output.ErrorMessage} (legacy {@code :84-96}).</li>
 *   <li>Newest call first.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EInvoiceHistoryService {

    private static final String SVC_SALES    = "GDT_INQUIRY_SALES";
    private static final String SVC_EXPENSES = "GDT_INQUIRY_EXPENSES";
    private static final String SVC_ALL_LIKE = "GDT_INQUIRY%";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExtTranCallHisRepository repo;
    private final BusaDocMstrRepository masterRepo;
    private final EInvoiceService einvoiceService;

    @Transactional(readOnly = true)
    public EInvoiceHistoryResponse list(String type, String fromDt, String toDt, String status) {
        final String bizKey = requireBizKey();
        final String svcPattern = resolveSvcPattern(type);
        final Boolean successFilter = resolveSuccessFilter(status);

        List<ExtTranCallHis> rows =
                repo.findEInvoiceInquiryHistory(bizKey, fromDt, toDt, svcPattern, successFilter);

        List<EInvoiceHistoryRow> out = new ArrayList<>(rows.size());
        for (ExtTranCallHis h : rows) {
            final String svcId = trim(h.getSvcId());
            final String rsltCd = trim(h.getRsltCd());
            out.add(new EInvoiceHistoryRow(
                    trim(h.getRegDtm()),
                    typeOf(svcId),
                    svcId,
                    trim(h.getStartDate()),
                    trim(h.getEndDate()),
                    "00000000".equals(rsltCd) ? "SUCCESS" : "FAIL",
                    rsltCd,
                    normalizeErrCont(h.getErrCont())));
        }
        return new EInvoiceHistoryResponse(out);
    }

    /**
     * "Latest Inquiry — Detail" popup — refactor port of legacy
     * {@code nxisas_einvhis_dt_r001_act.jsp}. Drills into one GDT inquiry
     * batch and lists the {@code BUSA_DOC_MSTR} invoices it produced,
     * filtered on {@code (DOC_TYPE, BIZ_KEY, ISSU_DT range, MOD_DTM,
     * HAND_REG_YN='N')} and ordered by the legacy {@code SortHelper}
     * whitelist (default {@code issu_dt DESC}).
     *
     * <p><b>Sales-only</b>: the legacy action rejects Expense
     * ({@code dt_r001:122-124}). {@link EInvoiceService#resolveDocType}
     * maps {@code SALES → 'I'}; any non-{@code 'I'} type fails with the
     * e-Invoice bad-request code (legacy {@code WBE0001} on {@code DOC_TYPE}).
     *
     * <p><b>MOD_DTM linkage caveat:</b> phase-1 scrape stamps the same
     * timestamp onto {@code BUSA_DOC_MSTR.MOD_DTM} and the
     * {@code EXT_TRAN_CALL_HIS.REG_DTM} audit row, so the History →
     * Detail drill-down lines up for list-only invoices. The phase-2
     * detail-save later re-stamps {@code MOD_DTM}; re-aligning the two
     * phases on a shared batch timestamp belongs to the scrape flow
     * (out of scope here). Documented in the parity audit.
     */
    @Transactional(readOnly = true)
    public EInvoiceDetailHistoryResponse detailHistory(
            String type, String fromDt, String toDt, String modDtm,
            String sortField, String sortDirection) {
        final String bizKey = requireBizKey();

        final String docType = einvoiceService.resolveDocType(type);
        if (!"I".equals(docType)) {
            // Legacy detail-history is Sales-only — reject Expense / Bill.
            throw new BusinessException("WBE2002");
        }
        einvoiceService.validateDateRange(fromDt, toDt);

        final Sort sort = resolveDetailSort(sortField, sortDirection);
        List<BusaDocMstr> rows =
                masterRepo.findDetailHistory(bizKey, docType, fromDt, toDt, trim(modDtm), sort);

        List<EInvoiceDetailHistoryRow> out = new ArrayList<>(rows.size());
        for (BusaDocMstr m : rows) {
            out.add(new EInvoiceDetailHistoryRow(
                    trim(m.getDocId()),
                    trim(m.getIssuDt()),
                    trim(m.getTrcoCmnm()),
                    m.getTotlAmt(),
                    trim(m.getEinvInvoicestatus()),
                    trim(m.getHandRegYn()),
                    trim(m.getDispDocId())));
        }
        return new EInvoiceDetailHistoryResponse(out);
    }

    /**
     * Build the detail-history {@link Sort} from the wire field / direction.
     * Mirrors legacy {@code SortHelper.getSortDynamic("nxisas_einvhis_dt_r001")}:
     * default {@code issu_dt DESC}; a recognised field sorts by that column
     * with {@code issu_dt DESC} appended as a stable secondary key.
     */
    private static Sort resolveDetailSort(String sortField, String sortDirection) {
        EInvoiceDetailHistorySortField field = EInvoiceDetailHistorySortField.parse(sortField);
        Sort defaultSort = Sort.by(Sort.Order.desc("issuDt"));
        if (field == null) {
            return defaultSort;
        }
        boolean desc = SortDirection.parse(sortDirection) == SortDirection.DESC;
        Sort.Order primary = desc
                ? Sort.Order.desc(field.entityProperty())
                : Sort.Order.asc(field.entityProperty());
        return Sort.by(primary).and(defaultSort);
    }

    /** SALES → sales svc id, EXPENSE/BILL → expenses svc id, else the
     *  {@code GDT_INQUIRY%} LIKE pattern (legacy default = both). */
    private static String resolveSvcPattern(String type) {
        if (type == null) return SVC_ALL_LIKE;
        String t = type.trim().toUpperCase();
        if (t.equals("SALES")) return SVC_SALES;
        if (t.equals("EXPENSE") || t.equals("BILL")) return SVC_EXPENSES;
        return SVC_ALL_LIKE;
    }

    /** {@code success}/{@code 0} → TRUE, {@code fail}/{@code 1} → FALSE,
     *  else null (All). */
    private static Boolean resolveSuccessFilter(String status) {
        if (status == null) return null;
        String s = status.trim().toLowerCase();
        if (s.equals("success") || s.equals("0")) return Boolean.TRUE;
        if (s.equals("fail") || s.equals("1")) return Boolean.FALSE;
        return null;
    }

    private static String typeOf(String svcId) {
        if (svcId == null) return "";
        if (svcId.endsWith("SALES")) return "SALES";
        if (svcId.endsWith("EXPENSES")) return "EXPENSE";
        return "";
    }

    /**
     * Legacy {@code normalizeResult}: if {@code ERR_CONT} is a JSON object
     * carrying {@code Output.ErrorMessage}, surface that message; otherwise
     * return the raw content. Blank → null.
     */
    static String normalizeErrCont(String errCont) {
        if (errCont == null) return null;
        String v = errCont.trim();
        if (v.isEmpty()) return null;
        if (v.startsWith("{") && v.endsWith("}")) {
            try {
                JsonNode root = MAPPER.readTree(v);
                JsonNode msg = root.path("Output").path("ErrorMessage");
                if (msg.isTextual()) {
                    String m = msg.asText();
                    return m.isEmpty() ? null : m;
                }
            } catch (Exception ignore) {
                // Not valid JSON — fall through to the raw value.
            }
        }
        return v;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String requireBizKey() {
        final String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) {
            throw new BusinessException("WBE9999");
        }
        return bizKey;
    }
}
