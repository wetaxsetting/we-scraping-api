package com.kosign.wecms.domain.einvoice.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.einvoice.entity.BusaDocDetl;
import com.kosign.wecms.domain.einvoice.entity.BusaDocMstr;
import com.kosign.wecms.domain.einvoice.entity.EinvFeeDetl;
import com.kosign.wecms.domain.einvoice.export.EInvoiceDetailWorkbookWriter;
import com.kosign.wecms.domain.einvoice.repository.BusaDocDetlRepository;
import com.kosign.wecms.domain.einvoice.repository.EinvFeeDetlRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Download Excel with Details" — refactor port of the legacy
 * {@code einv_0001_001_r002_excel} 63-column full e-Invoice detail
 * export (production file {@code e_Invoice_EN.xlsx} / IDO
 * {@code BUSA_DOC_MSTR_R024}).
 *
 * <p><b>2026-06-27 legacy-parity rewrite.</b> Previously this produced a
 * scoped 21-column sheet via the shared {@code ExcelWorkbookWriter} and
 * the slim {@link EInvoiceRow} list DTO. It now:
 * <ul>
 *   <li>loads the <b>full {@link BusaDocMstr} master entities</b> for the
 *       same filter (via {@link EInvoiceService#listMastersForExport}),
 *       reading the 40+ enrichment columns the list row never carried;</li>
 *   <li>expands each invoice into <b>one row per {@link BusaDocDetl} line
 *       item</b> (master / seller / buyer / totals / first-fee values
 *       repeated), matching the legacy {@code BUSA_DOC_MSTR ⋈ BUSA_DOC_DETL}
 *       row model;</li>
 *   <li>delegates layout to the dedicated {@link EInvoiceDetailWorkbookWriter}
 *       (63-column grouped header, merges, autofilter, sheet name
 *       {@code "Sales e-Invoice"}) — the shared writer is NOT used and NOT
 *       modified.</li>
 * </ul>
 *
 * <p><b>Intentionally-blank columns</b> (PO-approved 2026-06-27 — do NOT
 * port the legacy DB functions in this task):
 * <ul>
 *   <li>col 48 <i>Nature of invoice</i> — legacy {@code wb_get_einv_orig(STATUS)};</li>
 *   <li>col 54 <i>Purpose/Account</i> — legacy {@code wb_get_usag_nm(BUSA_USAG_CD)};</li>
 *   <li>col 56 <i>Project/site</i> — legacy {@code wb_get_prjt_nm(PRJT_NO)}.</li>
 * </ul>
 * <b>Blank in production too</b> (R024 never selects them):
 * col 57 Payment method ({@code RCV_PAY_WAY_CD}), col 58 Payment term
 * ({@code PAY_DUE_CD}, commented out in the legacy JSP), col 60 Balance
 * ({@code BAL}), col 62 Requestor ({@code REG_NM}).
 *
 * <p><b>Documented divergence:</b> the legacy R024 inner-joins
 * {@code BUSA_DOC_DETL}, silently dropping invoices with no persisted line
 * items. To avoid silently losing scraped invoices, this export keeps a
 * <b>master-only fallback row</b> for such invoices (PO-approved).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EInvoiceDetailExportService {

    /** Legacy discount line marker — item name "Chiet khau thuong mai"
     *  (trade discount). On such lines the unit-price/supply cells move to
     *  the discount-ratio/discount-amount columns. */
    private static final String DISCOUNT_ITEM_NM =
            "Chiết khấu thương mại";

    /* Legacy EINV_ORIGINALINVOICE label phrases stripped before splitting
     * the related-invoice tuple (replacement / adjustment invoices).
     * Verbatim from einv_0001_001_r002_excel_view.jsp. */
    private static final String REL_P1 =
            "Điều chỉnh cho ký hiệu mẫu số hóa đơn";
    private static final String REL_P2 = "ký hiệu hóa đơn";
    private static final String REL_P3 = "số hóa đơn";
    private static final String REL_P4 = "ngày lập";
    private static final String REL_P5 =
            "Thay thế cho ký hiệu mẫu số hóa đơn";

    private static final DateTimeFormatter DATE8 = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final EInvoiceService eInvoiceService;
    private final BusaDocDetlRepository detlRepo;
    private final EinvFeeDetlRepository feeRepo;
    private final EInvoiceDetailWorkbookWriter writer;

    public void exportTo(HttpServletResponse response, ExcelExportRequest request) {
        final String bizKey = TenantContext.currentBizKey();
        var filters = request.filters() != null ? request.filters() : java.util.Map.<String, Object>of();
        String type    = stringOrNull(filters.get("type"));
        String fromDt  = stringOrNull(filters.get("from"));
        String toDt    = stringOrNull(filters.get("to"));
        String keyword = stringOrNull(filters.get("keyword"));

        // Full master entities — same filter chain as the list (IR-1/2/3/5/6).
        List<BusaDocMstr> masters = eInvoiceService.listMastersForExport(
                type, fromDt, toDt, keyword, null, null);

        List<Object[]> rows = new ArrayList<>();
        int invoiceCount = 0;
        BigDecimal vndTotal = BigDecimal.ZERO;
        int vndCount = 0;

        for (BusaDocMstr m : masters) {
            invoiceCount++;
            if (isVnd(m.getTrcoTrscCcyCd())) {
                vndCount++;
                if (m.getTotlAmt() != null) vndTotal = vndTotal.add(m.getTotlAmt());
            }

            EinvFeeDetl firstFee = firstFee(bizKey, m.getDocId());
            Object[] base = masterCells(m, firstFee);

            List<BusaDocDetl> items = detlRepo.findByBizKeyAndDocIdOrderByDispOrdAsc(bizKey, m.getDocId());
            if (items.isEmpty()) {
                // Master-only fallback (PO-approved divergence from legacy
                // inner-join, which would drop this invoice).
                rows.add(base);
            } else {
                for (BusaDocDetl d : items) {
                    rows.add(lineRow(base, d));
                }
            }

            if (rows.size() > ExcelExportService.MAX_ROWS) {
                throw new BusinessException("WBE2011", rows.size(), ExcelExportService.MAX_ROWS);
            }
        }

        String title  = firstNonBlank(request.title(), request.fileName(), "SALES E-INVOICE");
        String period = buildPeriod(fromDt, toDt);
        String totals = buildTotals(vndCount, vndTotal);
        String fileName = sanitizeFileName(firstNonBlank(request.fileName(), "e-Invoice Details"));

        log.info("e-invoice details export: bizKey={} type={} invoices={} rows={}",
                bizKey, type, invoiceCount, rows.size());

        configureResponseHeaders(response, fileName);
        try (OutputStream out = response.getOutputStream()) {
            writer.write(out, title, period, totals, rows);
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

    /** Master + seller + buyer + totals + first-fee cells, repeated on
     *  every line-item row of an invoice. Line-item cells (23-36) are left
     *  null here and filled per line by {@link #lineRow}. */
    private Object[] masterCells(BusaDocMstr m, EinvFeeDetl firstFee) {
        Object[] r = new Object[EInvoiceDetailWorkbookWriter.COL_COUNT];
        r[0]  = m.getEinvInvoicestatus();
        r[1]  = m.getEinvTitle();
        r[2]  = m.getEinvNumericsymbol();
        r[3]  = m.getEinvInvoicesymbol();
        r[4]  = m.getHandDocId();
        r[5]  = fmtDate(m.getIssuDt(), ISO_DATE);                 // Invoice date → yyyy-MM-dd
        r[6]  = m.getTrcoCmnm();                                  // Seller block = TRCO_* (counterparty)
        r[7]  = m.getTrcoTaxId();
        r[8]  = joinAddr(m.getTrcoBsunDtlAdrs(), m.getTrcoBsunPostAdrs(), m.getTrcoBsunCity(), m.getTrcoBsunSt());
        r[9]  = m.getEinvSellerbankacc();
        r[10] = m.getEinvSellerbankname();
        r[11] = m.getIssuRprsTlph();
        r[12] = m.getEinvBuyerfullname();                        // Buyer block = ISSU_* / EINV_BUYER*
        r[13] = m.getIssuCmnm();
        r[14] = joinAddr(m.getIssuBsunDtlAdrs(), m.getIssuBsunPostAdrs(), m.getIssuBsunCity(), m.getIssuBsunSt());
        r[15] = m.getIssuTaxId();
        r[16] = m.getEinvBuyerbankacc();
        r[17] = m.getEinvBuyerbankname();
        r[18] = m.getEinvListnumber();
        r[19] = m.getEinvListdate();
        r[20] = m.getTrcoTrscCcyCd();
        r[21] = m.getEinvPaymenttype();
        r[22] = fmtRate(m.getEinvRate());
        r[37] = m.getSplyAmtSum();
        r[38] = m.getTaxAmtSum();
        if (firstFee != null) {
            r[39] = firstFee.getEinvFeename();
            r[40] = firstFee.getEinvFee();
        }
        r[41] = m.getTotlDisAmt();
        r[42] = m.getTotlAmt();
        r[43] = m.getEinvTotalamtinletter();
        r[44] = m.getEinvMccqt();
        r[45] = signDateTime(m.getEinvSellersigndate(), m.getEinvSellersigntime());
        r[46] = m.getEinvBuyersignby();
        r[47] = signDateTime(m.getEinvBuyersigndate(), m.getEinvBuyersigntime());
        // r[48] Nature of invoice — BLANK: legacy wb_get_einv_orig(STATUS) not ported (PO-approved).
        String[] rel = parseRelated(m.getEinvOriginalInvoice());
        r[49] = rel[0];   // Related type invoice (legacy uses param1)
        r[50] = rel[0];   // Related Form No (legacy also uses param1)
        r[51] = rel[1];   // Related Serial No
        r[52] = rel[2];   // Related Invoice No
        r[53] = rel[3];   // Related Invoice Date
        // r[54] Purpose/Account — BLANK: legacy wb_get_usag_nm(BUSA_USAG_CD) not ported (PO-approved).
        r[55] = "E".equals(m.getDocType()) ? m.getCont() : m.getDocTitle();   // Description
        // r[56] Project/site — BLANK: legacy wb_get_prjt_nm(PRJT_NO) not ported (PO-approved).
        // r[57] Payment method — BLANK: RCV_PAY_WAY_CD not selected by R024 (blank in production).
        // r[58] Payment term — BLANK: PAY_DUE_CD commented out in legacy JSP (blank in production).
        r[59] = m.getDueDt();                                    // Due date — raw yyyyMMdd (legacy emits raw)
        // r[60] Balance — BLANK: BAL not selected by R024 (blank in production).
        r[61] = m.getApprSeqNo();                                // Approval Ref. (null for scraped invoices)
        // r[62] Requestor — BLANK: REG_NM not selected by R024 (blank in production).
        return r;
    }

    /** Copy the master row and overlay the per-line-item cells (23-36). */
    private Object[] lineRow(Object[] base, BusaDocDetl d) {
        Object[] r = base.clone();
        boolean isDiscount = DISCOUNT_ITEM_NM.equals(d.getItemNm());
        r[23] = d.getDispOrd();              // Item No.
        r[24] = d.getItemNm();               // Nature
        r[25] = d.getItemCd();               // Item code
        r[26] = d.getEinvProductname();      // Item name
        r[27] = d.getItemSpec();             // UOM
        r[28] = d.getItemQunt();             // Quantity
        r[29] = d.getItemUnitAmt();          // Unit price
        r[30] = isDiscount ? toText(d.getItemUnitAmt()) : null;   // Discount ratio (text)
        r[31] = isDiscount ? null : d.getEinvTaxrate();           // Tax rate
        r[32] = isDiscount ? null : d.getSplyAmt();               // Supply amount
        r[33] = isDiscount ? d.getSplyAmt() : null;               // Discount amount
        r[34] = d.getEinvTaxrate();          // VAT rate
        r[35] = d.getSplyAmt();              // Total Amount by tax rate
        r[36] = computeVat(d.getSplyAmt(), d.getEinvTaxrate(), isDiscount); // VAT amount by tax rate
        return r;
    }

    private EinvFeeDetl firstFee(String bizKey, String docId) {
        List<EinvFeeDetl> fees = feeRepo.findByBizKeyAndDocIdOrderByDispOrdAsc(bizKey, docId);
        return fees.isEmpty() ? null : fees.get(0);   // legacy joins EFD where DISP_ORD='1'
    }

    /** VAT amount per line — {@code SPLY_AMT × parsed(EINV_TAXRATE%)}.
     *  Non-percentage rates (e.g. {@code KCT} — not subject to tax) → 0
     *  (matches production). Discount lines → null. Rounded HALF_UP
     *  (legacy varies rounding by invoice type; simplified per plan). */
    private static BigDecimal computeVat(BigDecimal splyAmt, String taxRate, boolean isDiscount) {
        if (isDiscount || splyAmt == null) return null;
        if (taxRate != null && taxRate.matches("[0-9]+(\\.[0-9]+)?%")) {
            BigDecimal pct = new BigDecimal(taxRate.replace("%", "")).movePointLeft(2);
            return splyAmt.multiply(pct).setScale(0, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /** Strip + split {@code EINV_ORIGINALINVOICE} into [type/form, serial,
     *  invoiceNo, date]. Mirrors the legacy JSP regex-replace + comma split. */
    private static String[] parseRelated(String orig) {
        String[] out = new String[4];
        if (orig == null || orig.isBlank()) return out;
        String cleaned = orig
                .replace(REL_P1, "").replace(REL_P5, "")
                .replace(REL_P2, "").replace(REL_P3, "").replace(REL_P4, "")
                .replace(" ", "");
        String[] arr = cleaned.split(",");
        for (int j = 0; j < arr.length && j < 4; j++) {
            out[j] = arr[j].isEmpty() ? null : arr[j];
        }
        return out;
    }

    private static String joinAddr(String dtl, String post, String city, String st) {
        if (post == null || post.isBlank()) return null;   // legacy only emits when POST present
        return nz(dtl) + nz(post) + ", " + nz(city) + ", " + nz(st);
    }

    private static String signDateTime(String date8, String time6) {
        String d = fmtDate(date8, ISO_DATE);
        String t = fmtTime(time6);
        if (d.isEmpty() && t.isEmpty()) return "";
        if (t.isEmpty()) return d;
        if (d.isEmpty()) return t;
        return d + " " + t;
    }

    private static String fmtDate(String raw, DateTimeFormatter out) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.length() < 8) return s;
        try {
            return LocalDate.parse(s.substring(0, 8), DATE8).format(out);
        } catch (RuntimeException e) {
            return s;
        }
    }

    private static String fmtTime(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.length() < 6) return s;
        return s.substring(0, 2) + ":" + s.substring(2, 4) + ":" + s.substring(4, 6);
    }

    private static String fmtRate(BigDecimal rate) {
        if (rate == null) return "";
        return rate.stripTrailingZeros().toPlainString();
    }

    private static String buildPeriod(String fromDt, String toDt) {
        String f = fmtDate(fromDt, DMY);
        String t = fmtDate(toDt, DMY);
        if (f.isEmpty() && t.isEmpty()) return "";
        return "Inquiry Period (" + f + " ~ " + t + ")";
    }

    private static String buildTotals(int vndCount, BigDecimal vndTotal) {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.ROOT);
        sym.setGroupingSeparator('.');
        String amt = new DecimalFormat("#,##0", sym).format(vndTotal == null ? BigDecimal.ZERO : vndTotal);
        return "Total (VND) " + vndCount + " transaction(s)/ " + amt + "₫";
    }

    private static boolean isVnd(String ccy) {
        return ccy == null || ccy.isBlank() || "VND".equalsIgnoreCase(ccy.trim());
    }

    private static String toText(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
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
