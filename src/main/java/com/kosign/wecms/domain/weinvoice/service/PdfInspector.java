package com.kosign.wecms.domain.weinvoice.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Checks behind "Nạp PDF" (UI Spec 3.5): the bytes must be a real PDF, and the backend tries to
 * prove the PDF belongs to the invoice — by the file name (symbol + number) or by the text of
 * the first pages (number + seller MST, CQT code or lookup code). When neither proves it, the
 * user is asked to confirm; text extraction failing (scanned / protected PDF) only means "not
 * proven", never an error.
 */
@Slf4j
final class PdfInspector {

    private static final int HEADER_WINDOW = 1024;
    private static final int MAX_PAGES = 3;

    private PdfInspector() {
    }

    /** {@code %PDF-} within the first kilobyte, as the PDF specification allows. */
    static boolean isPdf(byte[] bytes) {
        if (bytes == null || bytes.length < 5) return false;
        String head = new String(bytes, 0, Math.min(bytes.length, HEADER_WINDOW), StandardCharsets.ISO_8859_1);
        return head.contains("%PDF-");
    }

    /**
     * @param fileName    original file name
     * @param symbol      invoice symbol (e.g. {@code C26TAA})
     * @param number      invoice number without leading zeros
     * @param sellerTaxId seller MST
     * @param cqtCode     CQT code or {@code null}
     * @param lookupCode  lookup code or {@code null}
     */
    static boolean belongsTo(byte[] pdf, String fileName, String symbol, String number, String sellerTaxId,
                             String cqtCode, String lookupCode) {
        String name = fileName == null ? "" : fileName.toUpperCase(Locale.ROOT);
        if (symbol != null && name.contains(symbol.toUpperCase(Locale.ROOT)) && name.contains(number)) {
            return true;
        }
        String text = compact(extractText(pdf));
        if (text.isEmpty()) return false;
        if (cqtCode != null && !cqtCode.isBlank() && text.contains(compact(cqtCode))) return true;
        if (lookupCode != null && lookupCode.length() >= 6 && text.contains(compact(lookupCode))) return true;
        return sellerTaxId != null && text.contains(compact(sellerTaxId)) && text.contains(number);
    }

    private static String extractText(byte[] pdf) {
        PdfReader reader = null;
        try {
            reader = new PdfReader(pdf);
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder sb = new StringBuilder();
            int pages = Math.min(reader.getNumberOfPages(), MAX_PAGES);
            for (int p = 1; p <= pages; p++) {
                sb.append(extractor.getTextFromPage(p)).append('\n');
            }
            return sb.toString();
        } catch (Exception | LinkageError e) {
            log.debug("PDF text extraction failed: {}", e.toString());
            return "";
        } finally {
            if (reader != null) reader.close();
        }
    }

    private static String compact(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}
