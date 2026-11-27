package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.domain.weinvoice.repository.InvoiceDetailRepository.ItemRow;
import com.kosign.wecms.domain.weinvoice.repository.PackRepository.PackRow;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;

/**
 * "Bản thể hiện" of an invoice without an original PDF (UI Spec v1.1 §3.12): one common template built from the GDT
 * data WeInvoice holds (list row + XML lines when the XML is there), with a bronze double frame. Used when the engine
 * has no GDT print of the invoice ({@code invoice_xml.gdt_pdf_path}, which carries the GDT QR) — typically invoices
 * without code that GDT keeps no XML for. No QR code: the build has no QR library (record: Known Limitations).
 */
public final class RenderedInvoicePdf {

    private static final List<String> FONT_CANDIDATES = List.of(
            "C:/Windows/Fonts/times.ttf",
            "C:/Windows/Fonts/arial.ttf",
            "C:/Windows/Fonts/segoeui.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");
    private static final Color BRONZE = new Color(0xA0, 0x7A, 0x3F);
    private static volatile BaseFont cachedFont;

    private RenderedInvoicePdf() {
    }

    public static byte[] render(PackRow r, List<ItemRow> items) {
        BaseFont base = font();
        Font title = new Font(base, 15, Font.BOLD, BRONZE);
        Font h = new Font(base, 9.5f, Font.BOLD);
        Font n = new Font(base, 9.5f, Font.NORMAL);
        Font small = new Font(base, 8, Font.ITALIC, Color.DARK_GRAY);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 52, 52);
        try {
            PdfWriter w = PdfWriter.getInstance(doc, out);
            w.setPageEvent(new Frame());
            doc.addTitle(r.templateNo() + r.symbol() + " " + r.number());
            doc.addCreator("WeInvoice");
            doc.open();

            Paragraph t = new Paragraph(r.templateNo() == 2 ? "HOÁ ĐƠN BÁN HÀNG" : "HOÁ ĐƠN GIÁ TRỊ GIA TĂNG", title);
            t.setAlignment(Element.ALIGN_CENTER);
            doc.add(t);
            Paragraph sub = new Paragraph("BẢN THỂ HIỆN — không phải PDF gốc của bên phát hành", small);
            sub.setAlignment(Element.ALIGN_CENTER);
            doc.add(sub);
            Paragraph meta = new Paragraph();
            meta.setAlignment(Element.ALIGN_CENTER);
            meta.add(new Chunk("Ký hiệu mẫu số: ", h));
            meta.add(new Chunk(String.valueOf(r.templateNo()), n));
            meta.add(new Chunk("    Ký hiệu: ", h));
            meta.add(new Chunk(nz(r.symbol()), n));
            meta.add(new Chunk("    Số: ", h));
            meta.add(new Chunk(String.valueOf(r.number()), n));
            meta.add(new Chunk("    Ngày lập: ", h));
            meta.add(new Chunk(PdfPackNames.dmy(r.issueDate()).replace('-', '/'), n));
            meta.setSpacingBefore(6);
            doc.add(meta);
            if (r.cqtCode() != null && !r.cqtCode().isBlank()) {
                Paragraph cqt = new Paragraph();
                cqt.setAlignment(Element.ALIGN_CENTER);
                cqt.add(new Chunk("Mã của cơ quan thuế: ", h));
                cqt.add(new Chunk(r.cqtCode(), n));
                doc.add(cqt);
            }

            PdfPTable parties = new PdfPTable(1);
            parties.setWidthPercentage(100);
            parties.setSpacingBefore(10);
            parties.addCell(party("Đơn vị bán hàng", r.sellerName(), r.sellerTaxId(), r.sellerAddress(), h, n));
            parties.addCell(party("Người mua hàng", r.buyerName(), r.buyerTaxId(), r.buyerAddress(), h, n));
            doc.add(parties);

            PdfPTable table = new PdfPTable(new float[]{5, 40, 9, 10, 14, 16, 9});
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);
            table.setHeaderRows(1);
            for (String head : List.of("STT", "Tên hàng hoá, dịch vụ", "ĐVT", "Số lượng", "Đơn giá", "Thành tiền", "Thuế suất")) {
                PdfPCell c = new PdfPCell(new Phrase(head, h));
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                c.setBackgroundColor(new Color(0xF4, 0xEC, 0xDD));
                c.setPadding(4);
                table.addCell(c);
            }
            if (items == null || items.isEmpty()) {
                PdfPCell c = new PdfPCell(new Phrase(InvoiceWorkbook.NO_LINES, small));
                c.setColspan(7);
                c.setPadding(6);
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(c);
            } else {
                int i = 0;
                for (ItemRow it : items) {
                    boolean discount = it.lineKind() != null && it.lineKind() == 3;
                    table.addCell(cell(String.valueOf(++i), n, Element.ALIGN_CENTER));
                    table.addCell(cell(nz(it.name()) + (discount ? " (chiết khấu)" : ""), n, Element.ALIGN_LEFT));
                    table.addCell(cell(nz(it.unit()), n, Element.ALIGN_CENTER));
                    table.addCell(cell(money(it.qty()), n, Element.ALIGN_RIGHT));
                    table.addCell(cell(money(it.price()), n, Element.ALIGN_RIGHT));
                    BigDecimal amount = it.amount() == null ? null : discount ? it.amount().abs().negate() : it.amount();
                    table.addCell(cell(money(amount), n, Element.ALIGN_RIGHT));
                    table.addCell(cell(InvoiceWorkbook.rateLabel(it.vatRate()), n, Element.ALIGN_CENTER));
                }
            }
            doc.add(table);

            PdfPTable totals = new PdfPTable(new float[]{70, 30});
            totals.setWidthPercentage(100);
            totals.setSpacingBefore(6);
            totalRow(totals, "Cộng tiền hàng (" + r.currency() + "):", r.preTax(), h, n);
            totalRow(totals, "Tiền thuế GTGT:", r.tax(), h, n);
            totalRow(totals, "Tổng tiền thanh toán:", r.total(), h, h);
            doc.add(totals);

            Paragraph foot = new Paragraph();
            foot.setSpacingBefore(14);
            foot.add(new Chunk("Bản thể hiện do WeInvoice dựng từ dữ liệu hoá đơn điện tử của Cục Thuế để tra cứu, lưu trữ "
                    + "và đối chiếu; không thay thế hoá đơn gốc của bên phát hành.", small));
            if (r.providerName() != null || r.lookupUrl() != null || r.lookupCode() != null) {
                foot.add(Chunk.NEWLINE);
                StringBuilder s = new StringBuilder("Tra cứu bản gốc");
                if (r.providerName() != null) s.append(" — ").append(r.providerName());
                if (r.lookupUrl() != null) s.append(": ").append(r.lookupUrl());
                if (r.lookupCode() != null) s.append("  Mã tra cứu: ").append(r.lookupCode());
                foot.add(new Chunk(s.toString(), small));
            }
            doc.add(foot);
            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("rendered invoice PDF: " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    private static PdfPCell party(String label, String name, String taxId, String address, Font h, Font n) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + ": ", h));
        p.add(new Chunk(nz(name), n));
        p.add(Chunk.NEWLINE);
        p.add(new Chunk("Mã số thuế: ", h));
        p.add(new Chunk(nz(taxId), n));
        if (address != null && !address.isBlank()) {
            p.add(Chunk.NEWLINE);
            p.add(new Chunk("Địa chỉ: ", h));
            p.add(new Chunk(address, n));
        }
        PdfPCell c = new PdfPCell(p);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(new Color(0xD8, 0xC8, 0xA8));
        c.setPaddingBottom(6);
        c.setPaddingTop(4);
        return c;
    }

    private static PdfPCell cell(String text, Font f, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setHorizontalAlignment(align);
        c.setPadding(3);
        return c;
    }

    private static void totalRow(PdfPTable t, String label, BigDecimal value, Font h, Font v) {
        PdfPCell l = new PdfPCell(new Phrase(label, h));
        l.setBorder(Rectangle.NO_BORDER);
        l.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell r = new PdfPCell(new Phrase(money(value), v));
        r.setBorder(Rectangle.NO_BORDER);
        r.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(l);
        t.addCell(r);
    }

    static String money(BigDecimal v) {
        if (v == null) return "";
        DecimalFormatSymbols sy = new DecimalFormatSymbols();
        sy.setGroupingSeparator('.');
        sy.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.##", sy).format(v);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Bronze double frame on every page. */
    private static final class Frame extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContentUnder();
            Rectangle page = document.getPageSize();
            cb.saveState();
            cb.setColorStroke(BRONZE);
            cb.setLineWidth(1.6f);
            cb.rectangle(22, 22, page.getWidth() - 44, page.getHeight() - 44);
            cb.stroke();
            cb.setLineWidth(0.6f);
            cb.rectangle(27, 27, page.getWidth() - 54, page.getHeight() - 54);
            cb.stroke();
            cb.restoreState();
        }
    }

    private static BaseFont font() {
        BaseFont f = cachedFont;
        if (f != null) return f;
        synchronized (RenderedInvoicePdf.class) {
            if (cachedFont != null) return cachedFont;
            for (String path : FONT_CANDIDATES) {
                if (new File(path).isFile()) {
                    try {
                        cachedFont = BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                        return cachedFont;
                    } catch (Exception ignored) {
                        // try the next candidate
                    }
                }
            }
            try {
                cachedFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (Exception e) {
                throw new IllegalStateException("no PDF font available", e);
            }
            return cachedFont;
        }
    }
}
