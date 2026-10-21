package com.kosign.wecms.domain.einvoice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.banking.nxisas.audit.entity.NxsiasModuleTranLogHis;
import com.kosign.wecms.domain.banking.nxisas.audit.NxsiasModuleTranLogHisRepository;
import com.kosign.wecms.domain.banking.synchistory.entity.ExtTranCallHis;
import com.kosign.wecms.domain.banking.synchistory.repository.ExtTranCallHisRepository;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceScrapeRequest;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceScrapeResult;
import com.kosign.wecms.domain.einvoice.dto.ScrapedEInvoiceRow;
import com.kosign.wecms.domain.einvoice.entity.BusaDocMstr;
import com.kosign.wecms.domain.einvoice.entity.BusaDocMstrId;
import com.kosign.wecms.domain.einvoice.repository.BusaDocMstrRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persists GDT e-invoice rows scraped by the local NXiSAS engine.
 *
 * <p>Refactor port of {@code nxisas_einvoice_c001_act.jsp} — the "Update"
 * (scrape) save action. The browser drives the local engine
 * ({@code LOGIN → INVOICE_LIST → INVOICE_DETAIL} over
 * {@code wss://ibasews.coocon.co.kr:14102}), decrypts the {@code Result}
 * client-side (the refactor does not bundle Coocon {@code ISASSeedCBC} —
 * OQ-S2, same stance as {@code OatNxisasService.passthrough}), and POSTs
 * the structured {@code invoiceList} rows. This service re-applies the
 * legacy accept / branch / PROV_STTS rules and writes:
 * <ul>
 *   <li>{@code BUSA_DOC_MSTR} — one row per accepted invoice
 *       (legacy {@code BUSA_DOC_MSTR_C002} batch insert), and</li>
 *   <li>{@code EXT_TRAN_CALL_HIS} — one audit row per call
 *       ({@code ExtTranCallHisDAO.create}).</li>
 * </ul>
 *
 * <p><b>Idempotency (refactor improvement over legacy):</b> legacy does a
 * blind batch INSERT that PK-conflicts on re-scrape of the same range.
 * The refactor upserts by {@code (BIZ_KEY, DOC_ID)} — existing rows are
 * updated in place (status / amounts / mod stamp) so re-scraping is safe.
 * This matches the "save / update" intent and the idempotent
 * {@code ScrapingResultIntakeRequest} pattern.
 *
 * <p><b>Runtime caveat (OQ-S1):</b> the upstream scrape leg depends on
 * the local {@code .exe}, which is licensed per page origin and is NOT
 * licensed on {@code localhost} (memory {@code reference_nxisas_local_
 * engine_origin}). This persistence layer is unit-testable in isolation,
 * but the end-to-end scrape cannot be exercised in the R&D environment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EInvoiceScrapeService {

    /** Legacy constant issuer tax id for inbound (expense / received)
     *  invoices — {@code nxisas_einvoice_c001_act.jsp:159,225}. */
    private static final String EXPENSE_ISSU_TAX_ID = "99999999999999999999";

    /** Refactor route slug for the audit row (identifier-naming rule),
     *  replacing the legacy WSVC id {@code nxisas_einvoice_c001}. */
    private static final String WEB_SVC_ID = "addon-einvoices-scrape";

    /* --- NXSIAS_MODULE_TRAN_LOG_HIS per-invoice inquiry-log constants ---
     * Mirror the legacy {@code nxisas_logging_act.jsp} INVOICE_DETAIL rows
     * (the leg the Firm Banking History "Vietnam GDT Inquiry" filter reads).
     * The refactor consolidated the legacy two-call model (logging + save)
     * into this one intake call, so the per-invoice log is written here. */
    private static final String NXISAS_CLASS  = "COMMON";
    private static final String NXISAS_MODULE = "GLOBAL/VNM/PUBLIC_GDT";
    private static final String NXISAS_JOB    = "INVOICE_DETAIL";
    private static final String NXISAS_OK      = "00000000";
    private static final String NXISAS_TYPE_SALES   = "E-INVOICE_SALES_INQUIRY";
    private static final String NXISAS_TYPE_EXPENSE = "E-INVOICE_EXPENSE_INQUIRY";

    /** Serialises the compact {@code REQ_DATA} JSON. A dedicated instance —
     *  {@code Map} keys pass through verbatim (the global SNAKE_CASE strategy
     *  applies to POJO properties, not map keys), so the legacy camelCase
     *  keys are preserved regardless of app config. */
    private static final ObjectMapper LOG_JSON = new ObjectMapper();

    private static final String PROV_STTS_OPEN  = "OP";
    private static final String PROV_STTS_VOID   = "VD";
    private static final String HAND_REG_NO      = "N";

    /** {@code checkResult} phrases accepted on the type-driven branch
     *  (legacy {@code nxisas_einvoice_c001_act.jsp:145-147}). */
    private static final Set<String> CHECK_RESULT_BRANCH_A = Set.of(
            "Đã cấp mã hóa đơn",
            "Hóa đơn không đủ điều kiện cấp mã",
            "Tổng cục Thuế đã nhận");

    /** {@code checkResult} phrases accepted on the forced-Bill branch
     *  (legacy {@code nxisas_einvoice_c001_act.jsp:207-209}) — always
     *  stored as {@code DOC_TYPE 'B'} with the seller-tax DOC_ID suffix. */
    private static final Set<String> CHECK_RESULT_BRANCH_B = Set.of(
            "Cục thuế đã nhận không mã",
            "Cục Thuế đã nhận hóa đơn có mã khởi tạo từ máy tính tiền",
            "Cục Thuế đã nhận");

    /** invoiceStatus → PROV_STTS 'VD' on branch A
     *  (legacy {@code :187}). */
    private static final Set<String> VOID_STATUS_BRANCH_A = Set.of(
            "Hóa đơn đã bị thay thế",
            "Hóa đơn đã bị hủy");

    /** invoiceStatus → PROV_STTS 'VD' on branch B
     *  (legacy {@code :249}). */
    private static final Set<String> VOID_STATUS_BRANCH_B = Set.of(
            "Hóa đơn đã bị điều chỉnh",
            "Hóa đơn đã bị hủy");

    private static final DateTimeFormatter DTM_14 =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATE_8 =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BusaDocMstrRepository    busaRepo;
    private final ExtTranCallHisRepository auditRepo;
    private final BusiInfmRepository       busiRepo;
    private final NxsiasModuleTranLogHisRepository nxisasLogRepo;
    private final com.kosign.wecms.common.legacy.LegacyFunctionRepository legacyFunctions;

    @Transactional
    public EInvoiceScrapeResult saveScrapedInvoices(EInvoiceScrapeRequest req) {
        final String bizKey = requireBizKey();
        final String userId = TenantContext.currentUserId();
        final boolean isSales = "SALES".equalsIgnoreCase(req.type());
        final String regDtm = OffsetDateTime.now(ZoneOffset.UTC).format(DTM_14);
        final String salesIssuTaxId = isSales ? resolveSalesIssuerTaxId(bizKey, req.issuerTaxCode()) : null;

        int saved = 0;
        int updated = 0;
        int skipped = 0;

        // Pass 1 — compute the doc identity + derived fields for every
        // non-skipped row (skip logic UNCHANGED), collecting doc ids for a
        // single batch prefetch. This replaces the per-row findById N+1.
        record ScrapeWork(ScrapedEInvoiceRow row, String checkResult, String base,
                          String docType, String issuTaxId, String sellerTax,
                          String provStts, String docId) { }
        java.util.List<ScrapeWork> work = new java.util.ArrayList<>(req.invoices().size());
        java.util.List<BusaDocMstrId> ids = new java.util.ArrayList<>(req.invoices().size());
        for (ScrapedEInvoiceRow row : req.invoices()) {
            final String checkResult = trim(row.checkResult());
            final boolean branchA = CHECK_RESULT_BRANCH_A.contains(checkResult);
            final boolean branchB = !branchA && CHECK_RESULT_BRANCH_B.contains(checkResult);
            if (!branchA && !branchB) {
                skipped++;
                continue;
            }

            final String base = trim(row.numericSymbol()) + trim(row.invoiceSymbol())
                    + "-" + trim(row.invoiceNo());
            if (base.equals("-") || trim(row.invoiceNo()).isEmpty()) {
                // No invoice identity — engine returned a malformed row.
                skipped++;
                continue;
            }

            // Branch A keeps the request type; branch B forces Bill.
            final String docType = branchA ? (isSales ? "I" : "B") : "B";
            final String sellerTax = trim(row.sellerTaxCode());
            final boolean appendSellerTax = !(branchA && isSales); // SALES branch-A keeps base; all others append
            final String docId = appendSellerTax ? base + "-" + sellerTax : base;
            final String issuTaxId = (branchA && isSales) ? salesIssuTaxId : EXPENSE_ISSU_TAX_ID;
            final Set<String> voidSet = branchA ? VOID_STATUS_BRANCH_A : VOID_STATUS_BRANCH_B;
            final String provStts = voidSet.contains(trim(row.invoiceStatus()))
                    ? PROV_STTS_VOID : PROV_STTS_OPEN;

            work.add(new ScrapeWork(row, checkResult, base, docType, issuTaxId,
                    sellerTax, provStts, docId));
            ids.add(new BusaDocMstrId(bizKey, docId));
        }

        // Single batch read of all candidate masters.
        java.util.Map<String, BusaDocMstr> byDocId = new java.util.HashMap<>();
        for (BusaDocMstr m : busaRepo.findAllById(ids)) {
            byDocId.put(m.getDocId(), m);
        }

        // Pass 2 — upsert. byDocId is updated after each row so two input
        // rows sharing one doc id behave exactly as before (first inserts,
        // second sees it as existing → update), matching the prior
        // in-transaction findById-sees-managed-entity semantics.
        java.util.List<BusaDocMstr> toSave = new java.util.ArrayList<>(work.size());
        for (ScrapeWork w : work) {
            final ScrapedEInvoiceRow row = w.row();
            final BusaDocMstr existing = byDocId.get(w.docId());
            final BusaDocMstr e = existing != null ? existing : new BusaDocMstr();

            if (existing == null) {
                e.setBizKey(bizKey);
                e.setDocId(w.docId());
                e.setRegId(userId);
                e.setRegDtm(regDtm);
            }
            e.setDocType(w.docType());
            e.setDispDocId(w.base());
            e.setIssuTaxId(w.issuTaxId() != null ? w.issuTaxId() : "");
            e.setTrcoKey(existing != null && existing.getTrcoKey() != null ? existing.getTrcoKey() : "");
            // TRCO_TAX_ID is the counterparty's tax id. For an EXPENSE the scraped
            // sellerTax IS the counterparty (supplier), so stamp it. For a SALES
            // row the scraped sellerTax is our OWN tax (we are the seller) —
            // stamping it would mislabel the buyer on the BKBR annex, so leave
            // TRCO_TAX_ID for the detail scrape to fill with the real buyer tax
            // (EInvoiceDetailScrapeService). Blank never overwrites an existing id.
            if (!isSales && !w.sellerTax().isEmpty()) {
                e.setTrcoTaxId(w.sellerTax());
            }
            e.setTrcoCmnm(blankToExisting(row.trcoCmnm(), e.getTrcoCmnm()));
            e.setTrcoTrscCcyCd(blankToExisting(row.currencyCd(), e.getTrcoTrscCcyCd()));
            e.setIssuDt(trim(row.invoiceDate()));
            e.setTotlAmt(parseAmount(row.totalAmt()));
            e.setSplyAmtSum(parseAmount(row.totalAmtWithoutTax()));
            e.setTaxAmtSum(parseAmount(row.totalTaxAmt()));
            e.setTotlDisAmt(parseAmount(row.totalDiscount()));
            e.setHandDocId(trim(row.invoiceNo()));
            e.setEinvInvoiceinfo(row.invoiceInfo());
            e.setEinvInvoicestatus(trim(row.invoiceStatus()));
            e.setEinvInvoicestatusBef(trim(row.invoiceStatus()));
            e.setEinvCheckresults(w.checkResult());
            e.setEinvNumericsymbol(trim(row.numericSymbol()));
            e.setEinvInvoicesymbol(trim(row.invoiceSymbol()));
            e.setEinvTestresults(normalizeTestResult(row.einvTestResults()));
            e.setProvStts(w.provStts());
            e.setHandRegYn(HAND_REG_NO);
            e.setUserId(userId);
            e.setModId(userId);
            e.setModDtm(regDtm);

            if (existing != null) {
                updated++;
            } else {
                saved++;
            }
            byDocId.put(w.docId(), e);
            toSave.add(e);
        }
        busaRepo.saveAll(toSave);

        writeAudit(isSales, req, bizKey, userId, regDtm);
        int logged = writeInquiryLog(isSales, req, bizKey, userId, regDtm);

        log.info("e-invoice scrape intake: bizKey={} type={} saved={} updated={} skipped={} logged={} rslt={}",
                bizKey, req.type(), saved, updated, skipped, logged, req.rsltCd());
        return new EInvoiceScrapeResult(saved, updated, skipped, regDtm);
    }

    /* ------------------------------------------------------------------ */

    /** Resolve the own-company tax id for SALES rows from
     *  {@code BUSI_INFM.TAX_ID} (legacy {@code loginUser.getTaxInfo()
     *  .getTaxId()}); fall back to the request value, then empty. */
    private String resolveSalesIssuerTaxId(String bizKey, String fallback) {
        String tax = busiRepo.findById(bizKey)
                .map(b -> b.getTaxId())
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);
        if (tax != null) return tax;
        return fallback != null && !fallback.isBlank() ? fallback : "";
    }

    /** Write the per-call audit row. {@code SEQ_NO} is allocated per
     *  {@code (SVC_ID, REF_DATE)} through the single {@code wb_get_next_val}
     *  funnel (V140 #21). */
    private void writeAudit(boolean isSales, EInvoiceScrapeRequest req,
                            String bizKey, String userId, String regDtm) {
        final String svcId = isSales ? "GDT_INQUIRY_SALES" : "GDT_INQUIRY_EXPENSES";
        final String refDate = OffsetDateTime.now(ZoneOffset.UTC).format(DATE_8);
        final BigDecimal nextSeq = new BigDecimal(
                legacyFunctions.nextExtTranCallHisSeqNo(svcId, refDate));

        final ExtTranCallHis h = new ExtTranCallHis();
        h.setSvcId(svcId);
        h.setRefDate(refDate);
        h.setSeqNo(nextSeq);
        h.setWebSvcId(WEB_SVC_ID);
        h.setRegDtm(regDtm);
        h.setRsltCd(req.rsltCd());
        h.setErrCont(req.rsltMsg());
        h.setBizKey(bizKey);
        h.setUserId(userId);
        h.setStartDate(req.fromDt());
        h.setEndDate(req.toDt());
        auditRepo.save(h);
    }

    /**
     * Append one {@code NXSIAS_MODULE_TRAN_LOG_HIS} row per scraped invoice,
     * mirroring the legacy {@code nxisas_logging_act.jsp} INVOICE_DETAIL rows
     * ({@code :213-231}) that the Firm Banking History "Vietnam GDT Inquiry"
     * leg reads ({@code TB_FIRM_TRAN_R002.xml} → {@code FirmBankingHistoryView}
     * G leg: {@code WHERE NXISAS_TYPE IN ('E-INVOICE_SALES_INQUIRY',
     * 'E-INVOICE_EXPENSE_INQUIRY')}).
     *
     * <p>Legacy writes these in a separate browser-driven logging call; the
     * refactor consolidated the scrape into this single intake, so the log is
     * written here, in the same tenant transaction (rolls back with the
     * invoice save if that fails). Append-only — a re-scrape inserts fresh
     * rows (random {@code UUID}), matching legacy history semantics.
     *
     * <p>One row per invoice with an invoice identity — mirrors legacy's
     * per-{@code INVOICE_DETAIL} logging, independent of the accept/skip
     * branch that {@code BUSA_DOC_MSTR} applies. {@code REQ_DATA} carries the
     * legacy engine {@code Input} shape so the frontend composes the e-Invoice
     * No. ({@code numericSymbol + invoiceSymbol + '-' + invoiceNo}) exactly as
     * {@code comm_0221_001.js:258-260} does. Returns the row count logged.
     */
    private int writeInquiryLog(boolean isSales, EInvoiceScrapeRequest req,
                                String bizKey, String userId, String regDtm) {
        final String type = isSales ? NXISAS_TYPE_SALES : NXISAS_TYPE_EXPENSE;
        final String invoiceType = isSales ? "1" : "2";

        List<NxsiasModuleTranLogHis> rows = new ArrayList<>(req.invoices().size());
        for (ScrapedEInvoiceRow row : req.invoices()) {
            final String invoiceNo = trim(row.invoiceNo());
            if (invoiceNo.isEmpty()) {
                // No invoice identity — engine returned a malformed row; the
                // save path skips it too, so there is nothing to log.
                continue;
            }
            NxsiasModuleTranLogHis h = new NxsiasModuleTranLogHis();
            h.setUuid(UUID.randomUUID().toString().replace("-", ""));
            h.setRegDtm(regDtm);
            h.setBizKey(bizKey);
            h.setRegId(userId);
            h.setNxisasClass(NXISAS_CLASS);
            h.setNxisasModule(NXISAS_MODULE);
            h.setNxisasJob(NXISAS_JOB);
            h.setNxisasErrCode(NXISAS_OK);
            h.setNxisasErrCont(null);
            h.setReqData(buildReqData(req, row, invoiceType, invoiceNo));
            h.setNxisasType(type);
            rows.add(h);
        }
        if (!rows.isEmpty()) {
            nxisasLogRepo.saveAll(rows);
        }
        return rows.size();
    }

    /** Compose the legacy {@code REQ_DATA} JSON for one INVOICE_DETAIL log row
     *  (matches the engine {@code Input} shape stored by legacy
     *  {@code nxisas_logging_act.jsp}). */
    private static String buildReqData(EInvoiceScrapeRequest req, ScrapedEInvoiceRow row,
                                       String invoiceType, String invoiceNo) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("sellerTaxCode", trim(row.sellerTaxCode()));
        data.put("endDate", req.toDt());
        data.put("numericSymbol", trim(row.numericSymbol()));
        data.put("invoiceType", invoiceType);
        data.put("invoiceSymbol", trim(row.invoiceSymbol()));
        data.put("invoiceNo", invoiceNo);
        data.put("startDate", req.fromDt());
        try {
            return LOG_JSON.writeValueAsString(data);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            // Simple String map — serialization cannot realistically fail;
            // degrade to no payload rather than aborting the scrape save.
            return null;
        }
    }

    /** Engine amounts arrive as plain numeric strings; strip grouping
     *  and parse. Blank / unparseable → {@code null}. */
    static BigDecimal parseAmount(String raw) {
        if (raw == null) return null;
        String cleaned = raw.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isEmpty() || "-".equals(cleaned)) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Normalise the symbol-type marker to the legacy {@code ''/'1'/'2'}
     *  set; the column is {@code char(1)}. */
    static String normalizeTestResult(String raw) {
        String v = trim(raw);
        return ("1".equals(v) || "2".equals(v)) ? v : "";
    }

    private static String blankToExisting(String incoming, String existing) {
        String v = trim(incoming);
        return v.isEmpty() ? existing : v;
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
