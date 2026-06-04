package com.kosign.wecms.domain.einvoice.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.export.ExcelColumnSpec;
import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelWorkbookWriter;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceListResponse;
import com.kosign.wecms.domain.einvoice.dto.EInvoiceRow;
import com.kosign.wecms.domain.einvoice.entity.BusaDocDetl;
import com.kosign.wecms.domain.einvoice.entity.EinvFeeDetl;
import com.kosign.wecms.domain.einvoice.entity.EinvTaxDetl;
import com.kosign.wecms.domain.einvoice.repository.BusaDocDetlRepository;
import com.kosign.wecms.domain.einvoice.repository.EinvFeeDetlRepository;
import com.kosign.wecms.domain.einvoice.repository.EinvTaxDetlRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Download Excel with Details" — refactor port of the legacy
 * {@code einv_0001_001_r002_excel} server-side POI generator's
 * detail-expansion behaviour.
 *
 * <p>Legacy flattens each invoice into one row per {@code BUSA_DOC_DETL}
 * line item (master columns repeated), via {@code BUSA_DOC_MSTR_R024}
 * (master ⋈ detail ⋈ fee ⋈ tax) into a 63-column bilingual template.
 * The refactor reproduces the <b>essence</b> — master rows expanded with
 * their line / fee / tax children — using the data the refactor actually
 * persists (Scope 1's phase-2 detail-save wrote {@code BUSA_DOC_DETL} /
 * {@code EINV_FEE_DETL} / {@code EINV_TAX_DETL}). A {@code Detail Type}
 * column marks each child row PRODUCT / FEE / TAX.
 *
 * <p><b>Deliberate scope boundary:</b> the legacy 63-column layout draws
 * heavily on master-enrichment columns the refactor does NOT yet populate
 * (title, bank accounts, signatures, original-invoice relation, mccqt,
 * addresses) — those are out of this scope (no master enrichment). This
 * export carries the master + line/fee/tax columns the refactor has. It
 * does NOT use the legacy pre-built bilingual template; headers are
 * emitted in English (localisation deferred — same limitation noted for
 * the shared list export's backend-trusted headers).
 *
 * <p>Reuses {@link EInvoiceService#list} (unpaged) so the Details export
 * shares the exact filter chain (IR-1/2/3/5/6) with the on-screen list
 * and the "Download with List" export — single query path. The "List"
 * export ({@code EInvoiceExportProvider}) is untouched.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EInvoiceDetailExportService {

    private static final String DETAIL_TYPE_PRODUCT = "PRODUCT";
    private static final String DETAIL_TYPE_FEE     = "FEE";
    private static final String DETAIL_TYPE_TAX     = "TAX";
    private static final String DETAIL_TYPE_INVOICE = "INVOICE"; // master-only row (no children)

    /** Fixed Details column set (backend-defined — not View-Setting-driven). */
    private static final List<ExcelColumnSpec> COLUMNS = List.of(
            col("INVOICE",        "Invoice No.",          "TEXT"),
            col("SYMBOL",         "Symbol",               "TEXT"),
            col("ISSU_DT",        "Issue Date",           "DATE"),
            col("STATUS",         "Invoice Status",       "TEXT"),
            col("COUNTERPARTY",   "Counterparty",         "TEXT"),
            col("CP_TAX_ID",      "Counterparty Tax ID",  "TEXT"),
            col("CCY",            "Currency",             "TEXT"),
            col("LINE_TYPE",      "Detail Type",          "TEXT"),
            col("DISP_ORD",       "No.",                  "NUMBER"),
            col("LINE_NAME",      "Name",                 "TEXT"),
            col("UOM",            "Unit",                 "TEXT"),
            col("QTY",            "Quantity",             "NUMBER"),
            col("UNIT_PRICE",     "Unit Price",           "AMOUNT"),
            col("TAX_RATE",       "Tax Rate",             "TEXT"),
            col("SUPPLY_AMT",     "Supply Amount",        "AMOUNT"),
            col("DISCOUNT",       "Discount",             "AMOUNT"),
            col("FEE_AMT",        "Fee",                  "AMOUNT"),
            col("TAX_AMT",        "Tax Amount",           "AMOUNT"),
            col("INV_SUPPLY_SUM", "Invoice Supply Total", "AMOUNT"),
            col("INV_TAX_SUM",    "Invoice Tax Total",    "AMOUNT"),
            col("INV_TOTAL",      "Invoice Total",        "AMOUNT")
    );

    private final EInvoiceService eInvoiceService;
    private final BusaDocDetlRepository detlRepo;
    private final EinvFeeDetlRepository feeRepo;
    private final EinvTaxDetlRepository taxRepo;
    private final ExcelWorkbookWriter writer;

    public void exportTo(HttpServletResponse response, ExcelExportRequest request) {
        final String bizKey = TenantContext.currentBizKey();
        Map<String, Object> filters = request.filters() != null ? request.filters() : Map.of();
        String type    = stringOrNull(filters.get("type"));
        String fromDt  = stringOrNull(filters.get("from"));
        String toDt    = stringOrNull(filters.get("to"));
        String keyword = stringOrNull(filters.get("keyword"));

        // Same filtered query the list endpoint / List export run (IR-1/2/3/5/6).
        // Validation (required date range, doc type) happens inside list().
        EInvoiceListResponse data = eInvoiceService.list(
                type, fromDt, toDt, keyword, Pageable.unpaged(), null, null);
        List<EInvoiceRow> masters = data.rows() != null ? data.rows() : List.of();

        List<Map<String, Object>> rows = new ArrayList<>();
        int invoiceCount = 0;
        int lineCount = 0;
        for (EInvoiceRow m : masters) {
            invoiceCount++;
            String docId = m.docId();
            List<BusaDocDetl> products = detlRepo.findByBizKeyAndDocIdOrderByDispOrdAsc(bizKey, docId);
            List<EinvFeeDetl> fees     = feeRepo.findByBizKeyAndDocIdOrderByDispOrdAsc(bizKey, docId);
            List<EinvTaxDetl> taxes    = taxRepo.findByBizKeyAndDocIdOrderByDispOrdAsc(bizKey, docId);

            boolean any = false;
            for (BusaDocDetl p : products) {
                Map<String, Object> r = masterCells(m);
                r.put("LINE_TYPE",  DETAIL_TYPE_PRODUCT);
                r.put("DISP_ORD",   p.getDispOrd());
                r.put("LINE_NAME",  firstNonBlank(p.getEinvProductname(), p.getItemNm()));
                r.put("UOM",        p.getItemSpec());
                r.put("QTY",        p.getItemQunt());
                r.put("UNIT_PRICE", p.getItemUnitAmt());
                r.put("TAX_RATE",   p.getEinvTaxrate());
                r.put("SUPPLY_AMT", p.getSplyAmt());
                r.put("DISCOUNT",   p.getDcAmt());
                rows.add(r);
                lineCount++;
                any = true;
            }
            for (EinvFeeDetl f : fees) {
                Map<String, Object> r = masterCells(m);
                r.put("LINE_TYPE", DETAIL_TYPE_FEE);
                r.put("DISP_ORD",  f.getDispOrd());
                r.put("LINE_NAME", f.getEinvFeename());
                r.put("FEE_AMT",   f.getEinvFee());
                rows.add(r);
                lineCount++;
                any = true;
            }
            for (EinvTaxDetl tx : taxes) {
                Map<String, Object> r = masterCells(m);
                r.put("LINE_TYPE",   DETAIL_TYPE_TAX);
                r.put("DISP_ORD",    tx.getDispOrd());
                r.put("TAX_RATE",    tx.getEinvTaxrate());
                r.put("SUPPLY_AMT",  tx.getAmt());
                r.put("TAX_AMT",     tx.getTaxAmt());
                rows.add(r);
                lineCount++;
                any = true;
            }
            if (!any) {
                // Invoice with no persisted detail yet — still list it so
                // the export isn't silently missing invoices.
                Map<String, Object> r = masterCells(m);
                r.put("LINE_TYPE", DETAIL_TYPE_INVOICE);
                rows.add(r);
            }
        }

        String subHeader = String.format(Locale.ROOT,
                "%d invoice(s)  ·  %d detail line(s)", invoiceCount, lineCount);
        String title = firstNonBlank(request.title(), request.fileName(), "e-Invoice Details");
        String fileName = sanitizeFileName(firstNonBlank(request.fileName(), "e-Invoice Details"));

        log.info("e-invoice details export: bizKey={} type={} invoices={} lines={}",
                bizKey, type, invoiceCount, lineCount);

        configureResponseHeaders(response, fileName);
        try (OutputStream out = response.getOutputStream()) {
            writer.write(out, title, subHeader, COLUMNS, rows);
        } catch (IOException e) {
            log.error("e-invoice details export write failed", e);
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            if (!response.isCommitted()) {
                throw new BusinessException("WBE2012");
            }
            throw e;
        }
    }

    /* ------------------------------------------------------------------ */

    /** Master columns repeated on every child row of an invoice. */
    private static Map<String, Object> masterCells(EInvoiceRow m) {
        Map<String, Object> r = new HashMap<>();
        r.put("INVOICE",        m.dispDocId());
        r.put("SYMBOL",         m.einvInvoicesymbol());
        r.put("ISSU_DT",        m.issuDt());
        r.put("STATUS",         m.einvInvoicestatus());
        r.put("COUNTERPARTY",   m.trcoCmnm());
        r.put("CP_TAX_ID",      m.trcoTaxId());
        r.put("CCY",            m.trcoTrscCcyCd());
        r.put("INV_SUPPLY_SUM", m.splyAmtSum());
        r.put("INV_TAX_SUM",    m.taxAmtSum());
        r.put("INV_TOTAL",      m.totlAmt());
        return r;
    }

    private static ExcelColumnSpec col(String fieldId, String header, String format) {
        // align/ccyCd/widthChars left null — writer right-aligns AMOUNT/NUMBER
        // automatically and auto-measures widths from header + content.
        return new ExcelColumnSpec(fieldId, header, format, null, null, null);
    }

    private static void configureResponseHeaders(HttpServletResponse response, String fileName) {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ContentDisposition cd = ContentDisposition
                .builder("attachment")
                .filename(fileName + ".xlsx", StandardCharsets.UTF_8)
                .build();
        response.setHeader("Content-Disposition", cd.toString());
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
    }

    private static String sanitizeFileName(String raw) {
        String s = raw == null ? "" : raw.trim();
        s = s.replaceAll("[\\\\/\\u0000-\\u001f\"]+", " ").trim();
        if (s.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            s = s.substring(0, s.length() - 5);
        }
        if (s.length() > 120) s = s.substring(0, 120).trim();
        return s.isEmpty() ? "e-Invoice Details" : s;
    }

    private static String stringOrNull(Object raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.trim().isEmpty()) return v;
        }
        return "";
    }
}
