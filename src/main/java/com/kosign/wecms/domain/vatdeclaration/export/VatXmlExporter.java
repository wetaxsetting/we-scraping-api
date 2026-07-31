package com.kosign.wecms.domain.vatdeclaration.export;

import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclaration;
import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclarationIndicator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

/**
 * Builds the HTKK XML export for Form 01/GTGT (Circular 80/2021/TT-BTC).
 * Spec: docs/vat-declaration/5. VAT_Declaration_TechSpecs_EN.md §3.
 *
 * <p><b>Indicator mapping (§3.2) is authoritative</b> — the {@code ct21..ct43}
 * values under {@code <CTieuTKhaiChinh>} come verbatim from
 * {@link VatDeclarationIndicator} as integer VND (no decimals, HTKK parity).
 * The surrounding envelope tag names ({@code <HSoThueDTu>} / {@code <TTinChung>}
 * / {@code <TKhaiThue>} …) follow the standard TT80 structure but should be
 * reconciled against the PO golden sample before UAT (checklist item in the
 * interface spec §5). The digital-signature block ({@code <CKyDTu>}) is Phase 2
 * and is intentionally omitted.
 */
@Component
public class VatXmlExporter {

    private static final String MA_TKHAI_DEFAULT = "842";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Build the {@code .xml} file for a locked declaration. */
    public VatExportFile build(VatDeclaration d, VatDeclarationIndicator i) {
        String xml = body(d, i);
        return new VatExportFile(fileName(d), "application/xml",
                xml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Filename rule (§3.1): {@code [MST_13]-01_GTGT_TT80-[PERIOD]-L[RR].xml}.
     * Tax code padded to 13 digits (10-digit → append {@code 000}); period is
     * {@code M{MM}{YYYY}} (monthly) or {@code Q{Q}{YYYY}} (quarterly); revision
     * two digits.
     */
    String fileName(VatDeclaration d) {
        return pad13(d.getTpTaxCode()) + "-01_GTGT_TT80-" + periodToken(d)
                + "-L" + String.format("%02d", nz(d.getSupplNo())) + ".xml";
    }

    private static String periodToken(VatDeclaration d) {
        boolean monthly = "month".equalsIgnoreCase(d.getPeriodType());
        int v = nz(d.getPeriodValue());
        int y = nz(d.getPeriodYear());
        return monthly ? String.format("M%02d%d", v, y) : String.format("Q%d%d", v, y);
    }

    static String pad13(String taxCode) {
        String digits = taxCode == null ? "" : taxCode.replaceAll("\\D", "");
        if (digits.length() == 10) {
            return digits + "000";
        }
        return digits;
    }

    private String body(VatDeclaration d, VatDeclarationIndicator i) {
        StringBuilder b = new StringBuilder(4096);
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        b.append("<HSoThueDTu>\n");
        b.append("  <HSoKhaiThue>\n");
        b.append("    <TTinChungTKhai>\n");
        b.append("      <TKhaiThue>\n");
        tag(b, 8, "maTKhai", orDefault(d.getFormCode(), MA_TKHAI_DEFAULT));
        tag(b, 8, "pbanTKhaiXML", d.getXmlVer());
        tag(b, 8, "maCQTNoiNop", d.getOfficeCode());
        tag(b, 8, "tenCQTNoiNop", d.getOfficeName());
        b.append("        <kyKKhaiThue>\n");
        tag(b, 10, "kieuKy", "month".equalsIgnoreCase(d.getPeriodType()) ? "M" : "Q");
        tag(b, 10, "kyKKhai", d.getPeriod());
        tag(b, 10, "kyKKhaiTuNgay", fmtDate(d.getFromDt()));
        tag(b, 10, "kyKKhaiDenNgay", fmtDate(d.getToDt()));
        b.append("        </kyKKhaiThue>\n");
        tag(b, 8, "loaiTKhai", orDefault(d.getDeclType(), "C"));
        tag(b, 8, "soLan", String.valueOf(nz(d.getSupplNo())));
        tag(b, 8, "ngayLapTKhai", fmtDate(d.getDeclDt()));
        tag(b, 8, "ma_NganhNghe", d.getActivityCode());
        tag(b, 8, "tieuMucHachToan", d.getAcctItem());
        b.append("        <NNT>\n");
        tag(b, 10, "mst", d.getTpTaxCode());
        tag(b, 10, "tenNNT", d.getTpName());
        tag(b, 10, "dchiNNT", d.getTpAddr());
        b.append("        </NNT>\n");
        b.append("      </TKhaiThue>\n");
        b.append("    </TTinChungTKhai>\n");
        b.append("    <CTieuTKhaiChinh>\n");
        ct(b, "ct21", i::getNoActivity);
        ct(b, "ct22", i::getPrevCredit);
        b.append("      <GiaTriVaThueGTGTHHDVMuaVao>\n");
        ct2(b, "ct23", i::getPurchAmt);
        ct2(b, "ct24", i::getPurchAmtVat);
        ct2(b, "ct23a", i::getImpAmt);
        ct2(b, "ct24a", i::getImpAmtVat);
        b.append("      </GiaTriVaThueGTGTHHDVMuaVao>\n");
        ct(b, "ct25", i::getDeductVatAmt);
        ct(b, "ct26", i::getSalesAmtNondecl);
        ct(b, "ct27", i::getTolSalesAmt);
        ct(b, "ct28", i::getTotSalesAmtVat);
        ct(b, "ct29", i::getSalesAmt0);
        ct(b, "ct30", i::getSalesAmt5);
        ct(b, "ct31", i::getSalesAmtVat5);
        ct(b, "ct32", i::getSalesAmt10);
        ct(b, "ct33", i::getSalesAmtVat10);
        ct(b, "ct32a", i::getSalesAmtNontax);
        ct(b, "ct33a", i::getSalesVatOther);
        ct(b, "ct34", i::getTotalSalesAmt);
        ct(b, "ct35", i::getTotalSalesAmtVat);
        ct(b, "ct36", i::getVatIncurred);
        ct(b, "ct37", i::getAdjDecrease);
        ct(b, "ct38", i::getAdjIncrease);
        ct(b, "ct39a", i::getTransfVat);
        ct(b, "ct40a", i::getVatPayableActivity);
        ct(b, "ct40b", i::getInvestOffsetVatAmt);
        ct(b, "ct40", i::getVatPayableNet);
        ct(b, "ct41", i::getVatNotDeducted);
        ct(b, "ct42", i::getVatRefundReq);
        ct(b, "ct42a", i::getVatRefundReq1331);
        ct(b, "ct42b", i::getVatRefundReq1332);
        ct(b, "ct43", i::getCreditNext);
        b.append("    </CTieuTKhaiChinh>\n");
        b.append("  </HSoKhaiThue>\n");
        b.append("</HSoThueDTu>\n");
        return b.toString();
    }

    /** Emit a ct tag at 6-space indent (direct child of CTieuTKhaiChinh). */
    private static void ct(StringBuilder b, String tag, Supplier<BigDecimal> v) {
        tag(b, 6, tag, intVnd(v.get()));
    }

    /** Emit a ct tag at 8-space indent (inside a group node). */
    private static void ct2(StringBuilder b, String tag, Supplier<BigDecimal> v) {
        tag(b, 8, tag, intVnd(v.get()));
    }

    private static void tag(StringBuilder b, int indent, String name, String value) {
        b.append(" ".repeat(indent)).append('<').append(name).append('>')
                .append(escape(value)).append("</").append(name).append(">\n");
    }

    /** Integer VND text — HTKK carries whole dong, no decimals; null → 0. */
    static String intVnd(BigDecimal v) {
        return v == null ? "0" : v.setScale(0, java.math.RoundingMode.DOWN).toPlainString();
    }

    private static String fmtDate(LocalDate d) {
        return d == null ? "" : d.format(DMY);
    }

    private static String orDefault(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    static String escape(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&'  -> out.append("&amp;");
                case '<'  -> out.append("&lt;");
                case '>'  -> out.append("&gt;");
                case '"'  -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default   -> out.append(c);
            }
        }
        return out.toString();
    }
}
