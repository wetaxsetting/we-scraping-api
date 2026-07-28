package com.kosign.wecms.domain.vatdeclaration.export;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclaration;
import com.kosign.wecms.domain.vatdeclaration.entity.VatDeclarationIndicator;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Builds the A4 PDF print of Form 01/GTGT (interface spec §2.14, OQ-VAT-8 →
 * OpenPDF). Renders the declaration header + the ct21..ct43 indicator table.
 *
 * <p><b>Layout is a legible summary, not a pixel-perfect replica</b> of the GDT
 * form (the official visual template is not in the repo) — reconcile against the
 * PO sample before UAT. <b>Vietnamese diacritics need an embedded Unicode
 * TTF</b>: the exporter loads the first available font from
 * {@code wecms.vat.pdf-font-paths} (Windows defaults) with {@code IDENTITY_H};
 * if none is found it falls back to Helvetica (Latin-1) and logs a warning —
 * diacritics degrade but a valid PDF is still produced.
 */
@Slf4j
@Component
public class VatPdfExporter {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final NumberFormat VND = NumberFormat.getIntegerInstance(new Locale("vi", "VN"));

    /** Windows-default Unicode TTFs, first hit wins. R&D runs on Windows. */
    private static final List<String> FONT_CANDIDATES = List.of(
            "C:/Windows/Fonts/times.ttf",
            "C:/Windows/Fonts/arial.ttf",
            "C:/Windows/Fonts/segoeui.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf");

    public VatExportFile build(VatDeclaration d, VatDeclarationIndicator i) {
        BaseFont base = loadUnicodeBaseFont();
        Font title = new Font(base, 14, Font.BOLD);
        Font h = new Font(base, 10, Font.BOLD);
        Font normal = new Font(base, 10, Font.NORMAL);

        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        Document doc = new Document(PageSize.A4, 40, 40, 48, 40);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph t = new Paragraph("TỜ KHAI THUẾ GIÁ TRỊ GIA TĂNG", title);
            t.setAlignment(Element.ALIGN_CENTER);
            doc.add(t);
            Paragraph sub = new Paragraph("(Mẫu số 01/GTGT — Thông tư 80/2021/TT-BTC)", normal);
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(14);
            doc.add(sub);

            doc.add(info("Người nộp thuế: ", d.getTpName(), h, normal));
            doc.add(info("Mã số thuế: ", d.getTpTaxCode(), h, normal));
            doc.add(info("Kỳ tính thuế: ", d.getPeriod(), h, normal));
            doc.add(info("Từ ngày ", fmt(d.getFromDt()) + "  đến ngày " + fmt(d.getToDt()), h, normal));
            doc.add(info("Loại tờ khai: ", "C".equals(d.getDeclType()) ? "Chính thức" : "Bổ sung", h, normal));
            Paragraph gap = new Paragraph(" ", normal);
            gap.setSpacingAfter(6);
            doc.add(gap);

            doc.add(indicatorTable(i, h, normal));

            doc.close();
        } catch (RuntimeException e) {
            log.error("VAT declaration PDF build failed for sid {}", d.getSid(), e);
            throw new BusinessException("WBE_VAT_DECL_EXPORT_FAILED");
        }
        return new VatExportFile(fileName(d), "application/pdf", out.toByteArray());
    }

    private String fileName(VatDeclaration d) {
        boolean monthly = "month".equalsIgnoreCase(d.getPeriodType());
        String period = monthly
                ? String.format("M%02d%d", nz(d.getPeriodValue()), nz(d.getPeriodYear()))
                : String.format("Q%d%d", nz(d.getPeriodValue()), nz(d.getPeriodYear()));
        return VatXmlExporter.pad13(d.getTpTaxCode()) + "-01_GTGT_TT80-" + period + ".pdf";
    }

    private PdfPTable indicatorTable(VatDeclarationIndicator i, Font h, Font normal) {
        PdfPTable table = new PdfPTable(new float[]{7f, 1.4f, 3f});
        table.setWidthPercentage(100);
        headerCell(table, "Chỉ tiêu", h);
        headerCell(table, "Mã số", h);
        headerCell(table, "Giá trị (VND)", h);

        row(table, "Không phát sinh hoạt động mua/bán trong kỳ", "[21]", i::getNoActivity, normal);
        row(table, "Thuế GTGT còn được khấu trừ kỳ trước chuyển sang", "[22]", i::getPrevCredit, normal);
        row(table, "Giá trị HHDV mua vào", "[23]", i::getPurchAmt, normal);
        row(table, "Thuế GTGT của HHDV mua vào", "[24]", i::getPurchAmtVat, normal);
        row(table, "Giá trị hàng nhập khẩu", "[23a]", i::getImpAmt, normal);
        row(table, "Thuế GTGT hàng nhập khẩu", "[24a]", i::getImpAmtVat, normal);
        row(table, "Thuế GTGT được khấu trừ kỳ này", "[25]", i::getDeductVatAmt, normal);
        row(table, "HHDV bán ra không chịu thuế GTGT", "[26]", i::getSalesAmtNondecl, normal);
        row(table, "HHDV bán ra chịu thuế GTGT", "[27]", i::getTolSalesAmt, normal);
        row(table, "Thuế GTGT của HHDV bán ra", "[28]", i::getTotSalesAmtVat, normal);
        row(table, "HHDV bán ra chịu thuế suất 0%", "[29]", i::getSalesAmt0, normal);
        row(table, "HHDV bán ra chịu thuế suất 5%", "[30]", i::getSalesAmt5, normal);
        row(table, "Thuế GTGT (5%)", "[31]", i::getSalesAmtVat5, normal);
        row(table, "HHDV bán ra chịu thuế suất 10%", "[32]", i::getSalesAmt10, normal);
        row(table, "Thuế GTGT (10%)", "[33]", i::getSalesAmtVat10, normal);
        row(table, "HHDV không phải kê khai, nộp thuế", "[32a]", i::getSalesAmtNontax, normal);
        row(table, "Thuế GTGT của [32a]", "[33a]", i::getSalesVatOther, normal);
        row(table, "Tổng doanh thu HHDV bán ra", "[34]", i::getTotalSalesAmt, normal);
        row(table, "Tổng thuế GTGT của HHDV bán ra", "[35]", i::getTotalSalesAmtVat, normal);
        row(table, "Thuế GTGT phát sinh trong kỳ", "[36]", i::getVatIncurred, normal);
        row(table, "Điều chỉnh giảm", "[37]", i::getAdjDecrease, normal);
        row(table, "Điều chỉnh tăng", "[38]", i::getAdjIncrease, normal);
        row(table, "Thuế GTGT nhận bàn giao được khấu trừ", "[39a]", i::getTransfVat, normal);
        row(table, "Thuế GTGT phải nộp của hoạt động SXKD", "[40a]", i::getVatPayableActivity, normal);
        row(table, "Thuế GTGT mua vào của dự án đầu tư được bù trừ", "[40b]", i::getInvestOffsetVatAmt, normal);
        row(table, "Thuế GTGT còn phải nộp", "[40]", i::getVatPayableNet, normal);
        row(table, "Thuế GTGT chưa khấu trừ hết kỳ này", "[41]", i::getVatNotDeducted, normal);
        row(table, "Thuế GTGT đề nghị hoàn", "[42]", i::getVatRefundReq, normal);
        row(table, "Thuế GTGT còn được khấu trừ chuyển kỳ sau", "[43]", i::getCreditNext, normal);
        return table;
    }

    private static void headerCell(PdfPTable table, String text, Font h) {
        PdfPCell c = new PdfPCell(new Phrase(text, h));
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setPadding(4);
        table.addCell(c);
    }

    private static void row(PdfPTable table, String label, String code,
                            Supplier<BigDecimal> value, Font normal) {
        PdfPCell l = new PdfPCell(new Phrase(label, normal));
        l.setPadding(3);
        table.addCell(l);
        PdfPCell c = new PdfPCell(new Phrase(code, normal));
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setPadding(3);
        table.addCell(c);
        PdfPCell v = new PdfPCell(new Phrase(money(value.get()), normal));
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPadding(3);
        table.addCell(v);
    }

    private static Paragraph info(String label, String value, Font h, Font normal) {
        Paragraph p = new Paragraph();
        p.add(new Phrase(label, h));
        p.add(new Phrase(value == null ? "" : value, normal));
        p.setSpacingAfter(2);
        return p;
    }

    private static String money(BigDecimal v) {
        BigDecimal n = v == null ? BigDecimal.ZERO : v.setScale(0, java.math.RoundingMode.DOWN);
        return VND.format(n.longValueExact());
    }

    private static String fmt(LocalDate d) {
        return d == null ? "" : d.format(DMY);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * First available Unicode TTF (embedded, IDENTITY_H) so Vietnamese
     * diacritics render. Falls back to Helvetica when no candidate exists —
     * the PDF is still valid, diacritics degrade to base letters.
     */
    private BaseFont loadUnicodeBaseFont() {
        for (String path : FONT_CANDIDATES) {
            try {
                if (Files.isReadable(Path.of(path))) {
                    return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                }
            } catch (Exception e) {
                log.debug("VAT PDF font candidate {} unusable: {}", path, e.getMessage());
            }
        }
        log.warn("No Unicode TTF found for VAT PDF export — falling back to Helvetica; "
                + "Vietnamese diacritics may not render. Configure a font path if needed.");
        try {
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            throw new BusinessException("WBE_VAT_DECL_EXPORT_FAILED");
        }
    }
}
