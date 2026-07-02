package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.i18n.RelativeTimeFormatter;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.dto.AccountListResponse;
import com.kosign.wecms.domain.banking.dto.AccountRow;
import com.kosign.wecms.domain.banking.dto.TransactionListResponse;
import com.kosign.wecms.domain.banking.dto.TransactionRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Banking &gt; Current &amp; Savings &mdash; Excel Download (the PO-requested
 * template-populated report). One shared service covering the <b>VND and FCY</b>
 * twins of the two independent screens:
 *
 * <ol>
 *   <li><b>Account Inquiry</b> &mdash; VND {@code fnsh_0001_001} / FCY {@code fnsh_0005_001}</li>
 *   <li><b>Transaction History</b> &mdash; VND {@code fnsh_0002_001} / FCY {@code fnsh_0006_001}</li>
 * </ol>
 *
 * <p><b>Architecture note.</b> The legacy Banking Excel download was a generic
 * client-grid &rarr; shared {@code downloadProc.jsp} &rarr; <em>from-scratch</em>
 * {@code HSSFWorkbook} export (no template). The PO nonetheless decided the
 * Banking downloads must switch to the <b>template-populate</b> architecture used
 * by Internal Transfer / Contacts, driven by the supplied {@code .xls} templates
 * &mdash; see {@code docs/audits/current-savings-vnd-excel-download-audit.md} and
 * {@code docs/audits/current-savings-fcy-excel-download-audit.md}. This service
 * therefore deliberately does <strong>NOT</strong> reuse the shared
 * {@code common/export/ExcelExportService} pipeline (which builds an empty
 * workbook from scratch and cannot preserve the template's merged title,
 * subtitle, baked column widths and styles). Instead it loads the styled locale
 * template from the backend classpath via {@link ClassPathResource} and writes
 * data into the pre-styled rows so all template formatting is preserved &mdash;
 * mirroring
 * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferDownloadService}
 * and {@link com.kosign.wecms.domain.setting.contacts.service.ContactsDownloadService}.
 *
 * <p><b>Scope handling.</b> {@link CurrentSavingsScope} bundles the per-currency
 * template folder, localized filenames, default Account-Inquiry currency filter,
 * and Account-Inquiry column count. Transaction History is a shared 10-column
 * layout across both scopes; Account Inquiry differs (VND 9 columns A&ndash;I,
 * FCY 6 columns A&ndash;F) so it has one row-writer per scope. The {@code build}
 * template-load / style-capture / write scaffolding is shared.
 *
 * <p><b>Template layout</b> (verified against the supplied {@code .xls} files,
 * identical structure across EN/KO/VI): merged title (row 0), a subtitle row
 * (index 2), the localized header row (index 3), data rows from index 4. Only
 * the data rows are written; the title / subtitle / header are preserved verbatim
 * from the template (same "populate data rows only" style as Internal Transfer /
 * Contacts).
 *
 * <p><b>Locale selection</b> mirrors legacy {@code LocaleHelper.getLanguage}:
 * {@code EN} / {@code KO} / {@code VI} each load their own template; {@code KM}
 * and any other value fall back to {@code EN}.
 *
 * <p>Data is sourced through the same list query path as the on-screen lists
 * ({@link AccountService#list} / {@link TransactionService#list}) so the download
 * and the screen never diverge. Tenant scope is enforced by those services via
 * {@link TenantContext#currentBizKey()}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrentSavingsDownloadService {

    /** Classpath root under which every scope's template folder lives. */
    static final String TEMPLATE_ROOT = "templates/";
    /** Header is Excel row 4 (1-based, index 3); data rows start at row 5 → index 4. */
    static final int FIRST_DATA_ROW_INDEX = 4;
    /** Transaction History — 10 columns A–J, identical layout for VND and FCY. */
    static final int TRANSACTION_HISTORY_COLUMN_COUNT = 10;

    private final AccountService accountService;
    private final TransactionService transactionService;
    /** Renders the Last-Update cell as a localized "X ago" string, matching
     *  the on-screen Account Inquiry grid + the generic export provider. */
    private final RelativeTimeFormatter relativeTimeFormatter;

    /** Generated report payload — workbook bytes + the download filename. */
    public record GeneratedReport(byte[] content, String fileName) { }

    /** Populates one sheet's data rows using captured template column styles. */
    @FunctionalInterface
    private interface LayoutHandler {
        void populate(Sheet sheet, CellStyle[] colStyle);
    }

    /* ================================================================
     *  Account Inquiry  (VND fnsh_0001_001 / FCY fnsh_0005_001)
     * ================================================================ */

    /**
     * Generate the Account Inquiry (VND) download report. Convenience wrapper
     * over {@link #generateAccountInquiry(CurrentSavingsScope, String, String)}
     * with the VND scope — kept so the VND controller endpoint is unchanged.
     */
    @Transactional(readOnly = true)
    public GeneratedReport generateAccountInquiry(String langParam) {
        return generateAccountInquiry(CurrentSavingsScope.VND, null, langParam);
    }

    /**
     * Generate the Account Inquiry download report for the given scope.
     *
     * @param scope         VND or FCY
     * @param currencyParam optional currency filter override; only honoured for
     *                      FCY (the on-screen currency-dropdown narrow — a
     *                      concrete CCY such as {@code USD}/{@code KRW}, or
     *                      {@code FCY} for all foreign currencies). Ignored for
     *                      VND (always {@code "VND"}). Blank → scope default.
     * @param langParam     optional UI-locale hint ({@code EN}/{@code KO}/{@code VI});
     *                      when blank the JWT {@code langCd} is used; KM/other → EN.
     */
    @Transactional(readOnly = true)
    public GeneratedReport generateAccountInquiry(
            CurrentSavingsScope scope, String currencyParam, String langParam) {
        String bizKey = requireTenant();
        String lang = resolveLang(langParam);
        String currency = resolveAccountInquiryCurrency(scope, currencyParam);

        // Same query path as the on-screen list — scope currency, no bank/keyword
        // narrowing, un-paginated (the whole result set, legacy TO_EXCEL parity).
        AccountListResponse data = accountService.list(
                new AccountFilter(currency, null, null), null, null, Pageable.unpaged());
        List<AccountRow> rows = data.accounts() != null ? data.accounts() : List.of();

        byte[] bytes = build(
                scope.templateDir() + "account-inquiry/account-inquiry_" + lang + ".xls",
                scope.accountInquiryColumnCount(),
                (sheet, colStyle) -> {
                    int idx = FIRST_DATA_ROW_INDEX;
                    for (AccountRow r : rows) {
                        if (scope == CurrentSavingsScope.FCY) {
                            writeAccountInquiryRowFcy(sheet, idx++, colStyle, r);
                        } else {
                            writeAccountInquiryRowVnd(sheet, idx++, colStyle, r);
                        }
                    }
                });

        String fileName = scope.accountInquiryFile(lang);
        log.info("feature=current-savings-{} account-inquiry report-download bizKey={} currency={} lang={} rows={}",
                scope.name().toLowerCase(), bizKey, currency, lang, rows.size());
        return new GeneratedReport(bytes, fileName);
    }

    /** VND Account Inquiry layout — 9 columns A–I (index 0..8). */
    private void writeAccountInquiryRowVnd(Sheet sheet, int idx, CellStyle[] colStyle, AccountRow r) {
        Row row = rowAt(sheet, idx);
        setText  (row, 0, colStyle, bankName(r));
        setText  (row, 1, colStyle, r.acctNo());
        setText  (row, 2, colStyle, r.acctTypeNm());
        setText  (row, 3, colStyle, r.acctNickNm());
        setAmount(row, 4, colStyle, r.bal());
        setAmount(row, 5, colStyle, r.realAmt());
        setText  (row, 6, colStyle, "");                       // VAS Account Name — not on AccountRow
        setText  (row, 7, colStyle, relativeTimeFormatter.humanize(r.acctBalUpdDtm()));
        setText  (row, 8, colStyle, r.acctBalInqStts());       // Results (last-inquiry status)
    }

    /**
     * FCY Account Inquiry layout — 6 columns A–F (index 0..5). The FCY template
     * has no dedicated Account No. / Account Type / Results columns, so the
     * single <b>Bank</b> cell carries the on-screen composite
     * (bank name · CCY type / account no.) to preserve account identity —
     * mirroring {@code AccountInquiryFcyExportProvider}'s BANK cell.
     */
    private void writeAccountInquiryRowFcy(Sheet sheet, int idx, CellStyle[] colStyle, AccountRow r) {
        Row row = rowAt(sheet, idx);
        setText  (row, 0, colStyle, composeBankCell(r));       // Bank (composite)
        setText  (row, 1, colStyle, r.acctNickNm());           // Account Nickname
        setAmount(row, 2, colStyle, r.bal());                  // Balance
        setAmount(row, 3, colStyle, r.realAmt());              // Available Balance
        setText  (row, 4, colStyle, "");                       // VAS Account Name — not on AccountRow
        setText  (row, 5, colStyle, relativeTimeFormatter.humanize(r.acctBalUpdDtm())); // Last Update
    }

    /** VND always {@code "VND"}; FCY honours an on-screen currency narrow, else
     *  falls back to the scope default ({@code "FCY"} → all foreign currencies). */
    private static String resolveAccountInquiryCurrency(CurrentSavingsScope scope, String currencyParam) {
        if (scope == CurrentSavingsScope.VND) {
            return "VND";
        }
        return (currencyParam != null && !currencyParam.isBlank())
                ? currencyParam.trim()
                : scope.defaultCurrencyFilter();
    }

    private static String bankName(AccountRow r) {
        String nm = r.bankNm();
        if (nm != null && !nm.isBlank()) return nm;
        return r.bankCd();
    }

    /** FCY Bank cell — "BankName  ·  CCY Type / acctNo" composite, matching the
     *  on-screen FCY grid + {@code AccountInquiryFcyExportProvider}. Uses raw
     *  legacy codes when display names are absent. */
    private static String composeBankCell(AccountRow r) {
        StringBuilder b = new StringBuilder();
        b.append(bankName(r) == null ? "" : bankName(r));
        String type = trim(r.acctTypeNm());
        String ccy = trim(r.ccyCd());
        if (!ccy.isEmpty() || !type.isEmpty()) {
            b.append("  ·  ");
            if (!ccy.isEmpty()) b.append(ccy);
            if (!ccy.isEmpty() && !type.isEmpty()) b.append(" ");
            if (!type.isEmpty()) b.append(type);
        }
        String acctNo = trim(r.acctNo());
        if (!acctNo.isEmpty()) {
            b.append(" / ").append(acctNo);
        }
        return b.toString();
    }

    /* ================================================================
     *  Transaction History  (VND fnsh_0002_001 / FCY fnsh_0006_001)
     * ================================================================ */

    /**
     * Generate the Transaction History (VND) download report. Convenience
     * wrapper over {@link #generateTransactionHistory(CurrentSavingsScope,
     * String, String, String, String, String, String, String)} with the VND
     * scope — kept so the VND controller endpoint is unchanged.
     */
    @Transactional(readOnly = true)
    public GeneratedReport generateTransactionHistory(
            String acctSeqNo, String fromDt, String toDt,
            String type, String cnfmYn, String keyword, String langParam) {
        return generateTransactionHistory(
                CurrentSavingsScope.VND, acctSeqNo, fromDt, toDt, type, cnfmYn, keyword, langParam);
    }

    /**
     * Generate the Transaction History download report for one account.
     * The row layout is identical for VND and FCY; the scope only selects the
     * locale template folder and the localized download filename.
     *
     * @param scope     VND or FCY
     * @param acctSeqNo synthetic {@code BANK_CD_ACCT_NO} account identifier
     * @param fromDt    optional {@code yyyyMMdd} range start (list filter)
     * @param toDt      optional {@code yyyyMMdd} range end
     * @param type      optional deposit/withdraw type filter
     * @param cnfmYn    optional confirmed filter ({@code Y}/{@code N})
     * @param keyword   optional keyword filter
     * @param langParam optional UI-locale hint; KM/other → EN.
     */
    @Transactional(readOnly = true)
    public GeneratedReport generateTransactionHistory(
            CurrentSavingsScope scope, String acctSeqNo, String fromDt, String toDt,
            String type, String cnfmYn, String keyword, String langParam) {
        String bizKey = requireTenant();
        String lang = resolveLang(langParam);

        // Same query path as the on-screen list — scoped to one account +
        // the current filter set (legacy TO_EXCEL skips paging → whole set).
        TransactionListResponse data = transactionService.list(
                acctSeqNo, fromDt, toDt, type, cnfmYn, keyword);
        List<TransactionRow> rows = data.rows() != null ? data.rows() : List.of();

        byte[] bytes = build(
                scope.templateDir() + "transaction-history/transaction-history_" + lang + ".xls",
                TRANSACTION_HISTORY_COLUMN_COUNT,
                (sheet, colStyle) -> {
                    int idx = FIRST_DATA_ROW_INDEX;
                    for (TransactionRow r : rows) {
                        writeTransactionHistoryRow(sheet, idx++, colStyle, r);
                    }
                });

        String fileName = scope.transactionHistoryFile(lang);
        log.info("feature=current-savings-{} transaction-history report-download bizKey={} acctSeqNo={} lang={} rows={}",
                scope.name().toLowerCase(), bizKey, acctSeqNo, lang, rows.size());
        return new GeneratedReport(bytes, fileName);
    }

    /** Transaction History layout — 10 columns A–J (index 0..9), shared VND/FCY. */
    private static void writeTransactionHistoryRow(Sheet sheet, int idx, CellStyle[] colStyle, TransactionRow r) {
        Row row = rowAt(sheet, idx);
        setText  (row, 0, colStyle, dateTime(r.trscDt(), r.trscTm()));
        setAmount(row, 1, colStyle, r.depositAmt());
        setAmount(row, 2, colStyle, r.withdrawAmt());
        setAmount(row, 3, colStyle, r.bal());
        setText  (row, 4, colStyle, r.rmrk());
        setText  (row, 5, colStyle, r.chartAcctCd());          // Reporting Account
        setText  (row, 6, colStyle, r.useUsagCd());            // Purpose
        setText  (row, 7, colStyle, r.vasAcctCd());            // VAS Account Name
        setText  (row, 8, colStyle, r.rcvPayTrcoNm());         // Contact Name
        setText  (row, 9, colStyle, r.confirmedBy());          // Confirmed by
    }

    /* ================================================================
     *  Shared scaffolding
     * ================================================================ */

    /**
     * Load the locale template, capture its per-column data-row styles, hand the
     * sheet to the layout handler, and serialize the workbook. The template is a
     * legacy {@code .xls} (BIFF/HSSF) — {@link WorkbookFactory} auto-detects the
     * format and returns an {@code HSSFWorkbook}, so the written report stays
     * {@code .xls} (matching the legacy download).
     */
    private byte[] build(String relativePath, int columnCount, LayoutHandler handler) {
        String resourcePath = TEMPLATE_ROOT + relativePath;
        try (InputStream in = new ClassPathResource(resourcePath).getInputStream();
             Workbook wb = WorkbookFactory.create(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.getSheetAt(0);
            CellStyle[] colStyle = captureColStyles(sheet, columnCount);
            handler.populate(sheet, colStyle);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            // Template missing from the jar, or write failure — surfaces as
            // WBE9999 via the global handler with the stack logged.
            throw new UncheckedIOException(
                    "Failed to generate Current & Savings download report (" + relativePath + ")", e);
        }
    }

    /**
     * Capture the per-column cell style from the template's first data row
     * (index 4). Reusing these shared {@link CellStyle} objects for every
     * written cell preserves the template's borders, alignment, fill and number
     * formats on all rows, and avoids the 64k-style limit (same approach as
     * Internal Transfer / Contacts).
     */
    private static CellStyle[] captureColStyles(Sheet sheet, int columnCount) {
        CellStyle[] colStyle = new CellStyle[columnCount];
        Row templateRow = sheet.getRow(FIRST_DATA_ROW_INDEX);
        if (templateRow != null) {
            for (int c = 0; c < columnCount; c++) {
                Cell cell = templateRow.getCell(c);
                if (cell != null) colStyle[c] = cell.getCellStyle();
            }
        }
        return colStyle;
    }

    /**
     * Resolve the template locale. Priority: explicit {@code langParam} (the UI
     * locale the frontend passes) → JWT {@code langCd} → EN. Only EN / KO / VI
     * have a template; KM and anything else fall back to EN (legacy
     * {@code LocaleHelper} parity — no KM template).
     */
    private static String resolveLang(String langParam) {
        LangCd lc = (langParam != null && !langParam.isBlank())
                ? LangCd.fromCodeOrDefault(langParam)
                : TenantContext.currentLangCd();
        if (lc == null) return "EN";
        return switch (lc) {
            case KO -> "KO";
            case VI -> "VI";
            default -> "EN"; // EN + KM (no template) + any unmapped
        };
    }

    private static Row rowAt(Sheet sheet, int idx) {
        Row row = sheet.getRow(idx);
        return row != null ? row : sheet.createRow(idx);
    }

    private static void setText(Row row, int col, CellStyle[] colStyle, String value) {
        Cell cell = cellAt(row, col, colStyle);
        cell.setCellValue(value == null ? "" : value);
    }

    /**
     * Write a numeric amount. The DTOs carry the amount as a raw decimal String;
     * parse to a double so the template's number cell style renders it as a
     * number (re-summable in Excel). Blank / unparseable → text.
     */
    private static void setAmount(Row row, int col, CellStyle[] colStyle, String value) {
        Cell cell = cellAt(row, col, colStyle);
        if (value == null || value.isBlank()) {
            cell.setCellValue("");
            return;
        }
        try {
            cell.setCellValue(Double.parseDouble(value.trim()));
        } catch (NumberFormatException ex) {
            cell.setCellValue(value.trim());
        }
    }

    private static Cell cellAt(Row row, int col, CellStyle[] colStyle) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            cell = row.createCell(col);
            // Apply the captured template column style only to cells the
            // template didn't pre-create, so pre-styled cells keep their own.
            if (colStyle != null && col < colStyle.length && colStyle[col] != null) {
                cell.setCellStyle(colStyle[col]);
            }
        }
        return cell;
    }

    /** {@code yyyyMMdd} + {@code HHmmss} → {@code dd-MM-yyyy HH:mm:ss};
     *  date-only when time is blank; passthrough of odd-length values. */
    static String dateTime(String dt, String tm) {
        String d = trim(dt);
        String t = trim(tm);
        StringBuilder b = new StringBuilder();
        if (d.length() == 8) {
            b.append(d, 6, 8).append('-').append(d, 4, 6).append('-').append(d, 0, 4);
        } else {
            b.append(d);
        }
        if (t.length() == 6) {
            if (b.length() > 0) b.append(' ');
            b.append(t, 0, 2).append(':').append(t, 2, 4).append(':').append(t, 4, 6);
        } else if (!t.isEmpty()) {
            if (b.length() > 0) b.append(' ');
            b.append(t);
        }
        return b.toString();
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String requireTenant() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null) {
            throw new IllegalStateException("tenant context not populated");
        }
        return bizKey;
    }
}
