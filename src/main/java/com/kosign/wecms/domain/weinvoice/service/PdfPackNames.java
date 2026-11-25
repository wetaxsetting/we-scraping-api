package com.kosign.wecms.domain.weinvoice.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * File and folder names of the bulk PDF pack (UI Spec v1.1 file 02 §3.12). Pure functions, unit-tested.
 *
 * <ul>
 *   <li>ZIP: {@code <MST>_<MUAVAO|BANRA>_<dd-mm-yyyy>-<dd-mm-yyyy><suffix>.zip}</li>
 *   <li>PDF: {@code yyyy-mm-dd-<number>-<PARTNER, ASCII, UPPER, NO SPACES>.pdf} (XML of mode {@code pdf_xml}: same name)</li>
 *   <li>Replacement XML: {@code yyyy-mm-dd-<number>-<template><symbol>-<seller MST>.xml}</li>
 * </ul>
 */
public final class PdfPackNames {

    /** Modes of the "Tải PDF ▾" menu, in menu order. */
    public static final List<String> MODES = List.of("merge", "flat", "rendered", "pdf_xml", "by_company", "by_month");

    private static final Map<String, String> SUFFIX = Map.of(
            "merge", "_NOI-PDF",
            "flat", "",
            "rendered", "_BAN-THE-HIEN",
            "pdf_xml", "_PDF-XML",
            "by_company", "_THEO-CONG-TY",
            "by_month", "_THEO-THANG");

    /** Folder of the rendered copies of invoices without an original PDF. */
    public static final String FALLBACK_DIR = "_ban_the_hien_khong_co_pdf_goc/";
    /** Folder of the XML that stands in for a missing original PDF. */
    public static final String XML_DIR = "_thay_bang_xml/";

    private PdfPackNames() {
    }

    public static String normalizeMode(String mode) {
        String m = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        if (!MODES.contains(m)) {
            throw new IllegalArgumentException("mode: must be one of " + String.join(", ", MODES));
        }
        return m;
    }

    /**
     * ZIP base name without {@code .zip}. {@code engineDirection} {@code mua} / {@code ban}; anything else (a selection
     * across both directions) gives {@code MUAVAO-BANRA}. Dates are ISO {@code yyyy-MM-dd}.
     */
    public static String zipBase(String mst, String engineDirection, String fromIso, String toIso, String mode) {
        String dir = "mua".equals(engineDirection) ? "MUAVAO" : "ban".equals(engineDirection) ? "BANRA" : "MUAVAO-BANRA";
        return mst + "_" + dir + "_" + dmy(fromIso) + "-" + dmy(toIso) + SUFFIX.get(normalizeMode(mode));
    }

    /** {@code 2026-10-31} → {@code 31-10-2026}; blank stays blank. */
    public static String dmy(String iso) {
        if (iso == null || iso.length() < 10) return "";
        return iso.substring(8, 10) + "-" + iso.substring(5, 7) + "-" + iso.substring(0, 4);
    }

    /** PDF base name (no extension). */
    public static String pdfBase(String issueDate, String number, String partnerName) {
        String name = asciiName(partnerName);
        return (issueDate == null ? "0000-00-00" : issueDate) + "-" + number + (name.isEmpty() ? "" : "-" + name);
    }

    /** Replacement XML file name of an invoice without original PDF. */
    public static String xmlAltName(String issueDate, String number, String templateNo, String symbol, String sellerTaxId) {
        return (issueDate == null ? "0000-00-00" : issueDate) + "-" + number + "-" + nz(templateNo) + nz(symbol) + "-"
                + nz(sellerTaxId) + ".xml";
    }

    /** Vietnamese name → ASCII upper case letters and digits only, at most 80 characters. */
    public static String asciiName(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
        return n.length() > 80 ? n.substring(0, 80) : n;
    }

    /** Folder name that keeps Vietnamese letters (mode {@code by_company}); illegal path characters become spaces. */
    public static String safeDir(String s, String fallback) {
        String n = s == null ? "" : s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", " ").replaceAll("\\s+", " ").trim();
        if (n.length() > 60) n = n.substring(0, 60).trim();
        n = n.replaceAll("[. ]+$", "");
        return n.isEmpty() ? fallback : n;
    }

    /** Month folder of mode {@code by_month}: {@code yyyy-mm/mua_vao/} or {@code yyyy-mm/ban_ra/}. */
    public static String monthDir(String issueDate, String engineDirection) {
        String ym = issueDate == null || issueDate.length() < 7 ? "khong-ro-ngay" : issueDate.substring(0, 7);
        return ym + "/" + ("ban".equals(engineDirection) ? "ban_ra" : "mua_vao") + "/";
    }

    /** {@code name} if unused, else {@code name (2)}, {@code name (3)} … before the extension. */
    public static String unique(java.util.Set<String> used, String path) {
        if (used.add(path)) return path;
        int dot = path.lastIndexOf('.');
        String stem = dot > path.lastIndexOf('/') ? path.substring(0, dot) : path;
        String ext = dot > path.lastIndexOf('/') ? path.substring(dot) : "";
        for (int i = 2; ; i++) {
            String p = stem + " (" + i + ")" + ext;
            if (used.add(p)) return p;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
