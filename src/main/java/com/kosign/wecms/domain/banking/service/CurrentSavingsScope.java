package com.kosign.wecms.domain.banking.service;

import java.util.Map;

/**
 * Currency scope for the Banking &gt; Current &amp; Savings template-populated
 * Excel Download (Account Inquiry + Transaction History). Bundles everything
 * the shared {@link CurrentSavingsDownloadService} needs to differ between the
 * VND and FCY twins: the classpath template folder, the localized download
 * filenames, the default Account-Inquiry currency filter, and the
 * Account-Inquiry column count (VND ships a 9-column layout, FCY a 6-column
 * layout — Transaction History is a shared 10-column layout for both).
 *
 * <ul>
 *   <li><b>VND</b> — legacy {@code fnsh_0001_001} / {@code fnsh_0002_001}.</li>
 *   <li><b>FCY</b> — legacy {@code fnsh_0005_001} / {@code fnsh_0006_001}.</li>
 * </ul>
 *
 * <p>Filenames are the PO-required localized strings. As with VND, the
 * Transaction-History KO/VI filenames carry the fuller {@code 거래내역조회} /
 * {@code Tra cứu giao dịch} wording, which differs from the source template
 * basenames ({@code 거래내역} / {@code Lịch sử giao dịch}) — the template
 * <em>content</em> is preserved verbatim; only the download <em>filename</em>
 * uses the PO wording, keeping the two twins consistent.
 */
public enum CurrentSavingsScope {

    VND(
            "VND",
            "current-savings-vnd/",
            9,
            Map.of(
                    "EN", "Account Inquiry (VND).xls",
                    "KO", "계좌조회 (VND).xls",
                    "VI", "Tra cứu tài khoản (VND).xls"),
            Map.of(
                    "EN", "Transaction History (VND).xls",
                    "KO", "거래내역조회 (VND).xls",
                    "VI", "Tra cứu giao dịch (VND).xls")),

    FCY(
            "FCY",
            "current-savings-fcy/",
            6,
            Map.of(
                    "EN", "Account Inquiry (FCY).xls",
                    "KO", "계좌조회 (FCY).xls",
                    "VI", "Tra cứu tài khoản (FCY).xls"),
            Map.of(
                    "EN", "Transaction History (FCY).xls",
                    "KO", "거래내역조회 (FCY).xls",
                    "VI", "Tra cứu giao dịch (FCY).xls"));

    private final String defaultCurrencyFilter;
    private final String templateDir;
    private final int accountInquiryColumnCount;
    private final Map<String, String> accountInquiryFiles;
    private final Map<String, String> transactionHistoryFiles;

    CurrentSavingsScope(String defaultCurrencyFilter,
                        String templateDir,
                        int accountInquiryColumnCount,
                        Map<String, String> accountInquiryFiles,
                        Map<String, String> transactionHistoryFiles) {
        this.defaultCurrencyFilter = defaultCurrencyFilter;
        this.templateDir = templateDir;
        this.accountInquiryColumnCount = accountInquiryColumnCount;
        this.accountInquiryFiles = accountInquiryFiles;
        this.transactionHistoryFiles = transactionHistoryFiles;
    }

    /** {@code AccountFilter} currency used for the Account-Inquiry query when
     *  the caller doesn't override it ({@code "VND"} / {@code "FCY"}). */
    public String defaultCurrencyFilter() {
        return defaultCurrencyFilter;
    }

    /** Classpath folder under {@code templates/} holding this scope's six
     *  locale templates (e.g. {@code current-savings-fcy/}). */
    public String templateDir() {
        return templateDir;
    }

    /** Account-Inquiry column count — VND 9 (A–I), FCY 6 (A–F). */
    public int accountInquiryColumnCount() {
        return accountInquiryColumnCount;
    }

    /** Localized Account-Inquiry download filename keyed by {@code EN}/{@code KO}/{@code VI}. */
    public String accountInquiryFile(String lang) {
        return accountInquiryFiles.get(lang);
    }

    /** Localized Transaction-History download filename keyed by {@code EN}/{@code KO}/{@code VI}. */
    public String transactionHistoryFile(String lang) {
        return transactionHistoryFiles.get(lang);
    }
}
