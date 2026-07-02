package com.kosign.wecms.domain.banking.export;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelSortSpec;
import com.kosign.wecms.common.export.ExportFetch;
import com.kosign.wecms.common.export.ExportRowProvider;
import com.kosign.wecms.common.i18n.RelativeTimeFormatter;
import com.kosign.wecms.domain.banking.dto.AccountListResponse;
import com.kosign.wecms.domain.banking.dto.AccountRow;
import com.kosign.wecms.domain.banking.service.AccountFilter;
import com.kosign.wecms.domain.banking.service.AccountService;
import com.kosign.wecms.domain.banking.service.AmountFormatter;
import com.kosign.wecms.domain.setting.erptransactionhistory.service.ErpTrnsHistoryWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Common Excel export logic for Banking &gt; Account Inquiry (VND/FCY).
 * Reuses {@link AccountService#list} so the export and the on-screen
 * list draw from one query path — no second source of truth, no risk
 * of divergence.
 *
 * <p>Subclasses bind the concrete {@link #screenId()} +
 * {@link #defaultCurrency()} and may override
 * {@link #composeSubHeader(int, AccountListResponse, String)} when the
 * default {@code count + Balance + Available Balance} format is not
 * appropriate (e.g. FCY in mixed-currency mode emits count-only).
 *
 * <p>Filter handling (Sprint 2 — server-side parity with JEX):
 * <ul>
 *   <li>{@code currency} — passed through to {@link AccountService}
 *       (resolves to the equivalent JPQL predicate). When the request
 *       omits the key the subclass {@link #defaultCurrency()} kicks in
 *       so each screen remains scoped even if the frontend forgets it.</li>
 *   <li>{@code bankCd} — exact-match {@code BANK_CD}, applied at the DB
 *       layer. Mirrors JEX's {@code BANK_CD} input. The frontend filter
 *       state stores the legacy code, not the display name; we accept
 *       only the code in the wire payload.</li>
 *   <li>{@code keyword} — case-insensitive partial match on
 *       {@code ACCT_NO}, {@code ACCT_NICK_NM}, {@code ACCT_NM}, applied
 *       at the DB layer. Mirrors JEX's {@code SRCH_WD} input.</li>
 * </ul>
 *
 * <p>Export does NOT paginate — the entire filtered result is materialised
 * via {@code Pageable.unpaged()} so the spreadsheet contains every
 * matching row regardless of which page the user is currently viewing.
 *
 * <p>Subheader is computed server-side per CLAUDE.md decision 5 — the
 * frontend never ships a count + totals string for this screen.
 */
@RequiredArgsConstructor
public abstract class AccountInquiryExportProvider implements ExportRowProvider {

    /** FIELD_IDs allowed in the Excel export — matches the V32–V34
     *  {@code banking-account-inquiry-vnd} seed minus the special
     *  {@code CHECKBOX} row (which is filtered out by the writer
     *  regardless via {@code ScreenOptionService.SPECIAL_FIELD_IDS}). */
    private static final Set<String> ALLOWED_FIELD_IDS = Set.of(
            "BANK",
            "ACCOUNT_NICKNAME",
            "BALANCE",
            "AVAILABLE_BALANCE",
            "VAS_ACCOUNT",
            "LAST_UPDATE"
    );

    protected final AccountService accountService;
    /** Renders the LAST_UPDATE cell as a localised "X ago" string —
     *  legacy `fnsh_0001_001.formatExcel` parity (its Excel showed the
     *  humanized value, not the raw timestamp). Server-side mirror of
     *  the frontend `humanizeDatetime`. */
    protected final RelativeTimeFormatter relativeTimeFormatter;

    /**
     * Shared ERP_TRNS_HSTR writer for the WeCMS&rarr;ERP 'W' export audit.
     * Field-injected (not constructor) so the VND / FCY subclasses keep
     * their lean {@code super(accountService, relativeTimeFormatter)}
     * constructors unchanged — same precedent as
     * {@code PaymentResultExportProvider}. Spring injects it into the
     * concrete bean after construction.
     */
    @Autowired
    private ErpTrnsHistoryWriter erpHistoryWriter;

    /** Subclass binds the concrete screenId — must match a row set in
     *  {@code SYS_SCR_OPTION_WECMS} or the registry rejects the export
     *  with {@code WBE2010}. */
    @Override
    public abstract String screenId();

    /** Default {@code filters.currency} when the request omits the key.
     *  VND subclass returns {@code "VND"}; FCY subclass returns
     *  {@code "FCY"} (resolved server-side to {@code ccy_cd <> 'VND'}). */
    protected abstract String defaultCurrency();

    /** The 2-char {@code ERP_TRNS_HSTR.TRNS_TYPE_CD} for this screen's
     *  WeCMS&rarr;ERP export audit row — {@code "09"} Account Inquiry (VND) /
     *  {@code "10"} Account Inquiry (FCY), legacy {@code downloadProc.jsp}
     *  {@code fnsh_0001_001} / {@code fnsh_0005_001} branches. */
    protected abstract String erpExportTrnsTypeCd();

    /** Legacy {@code downloadProc.jsp} writes a WeCMS&rarr;ERP 'W' audit row for
     *  every Account Inquiry Excel download. */
    @Override
    public boolean auditsErpExport() {
        return true;
    }

    /** Persist the WeCMS&rarr;ERP 'W' row by copying the generated workbook
     *  into the ERP-history tenant subtree. Non-blocking — the writer is
     *  {@code REQUIRES_NEW} + swallow-and-log, matching legacy. */
    @Override
    public void recordErpExport(Path workbookFile, String orgFileNm) {
        erpHistoryWriter.recordExportedFile(erpExportTrnsTypeCd(), workbookFile, orgFileNm);
    }

    @Override
    public Set<String> allowedFieldIds() {
        return ALLOWED_FIELD_IDS;
    }

    @Override
    public ExportFetch fetch(ExcelExportRequest request) {
        Map<String, Object> filters = request.filters() != null ? request.filters() : Map.of();
        String currency = stringOrDefault(filters.get("currency"), defaultCurrency());
        // `bankCd` is the legacy code (e.g. "11704001"). Older clients
        // may still send `bankName` (display string); we accept it as
        // a fallback for backwards compatibility but recommend
        // migrating the frontend to send `bankCd` exclusively.
        String bankCd = stringOrNull(filters.get("bankCd"));
        if (bankCd == null) {
            bankCd = stringOrNull(filters.get("bankName")); // legacy alias — drop after frontend cutover
        }
        String keyword  = stringOrNull(filters.get("keyword"));

        ExcelSortSpec sort = request.sort();
        String sortField = sort != null ? sort.field() : null;
        String sortDirection = sort != null ? sort.direction() : null;

        // Server-side filter chain — bank + keyword now hit the DB
        // through the new `findFiltered` query rather than being
        // re-applied in-memory after fetch (Sprint 2 parity work).
        AccountListResponse data = accountService.list(
                new AccountFilter(currency, bankCd, keyword),
                sortField,
                sortDirection,
                Pageable.unpaged());

        List<AccountRow> rows = data.accounts() != null ? data.accounts() : List.of();
        List<Map<String, Object>> mapped = new ArrayList<>(rows.size());
        for (AccountRow r : rows) {
            mapped.add(toFieldMap(r));   // instance method — uses relativeTimeFormatter
        }

        String subHeader = composeSubHeader(mapped.size(), data, currency);

        return ExportFetch.of(mapped, subHeader);
    }

    /**
     * Compose the subheader string. Default = legacy VND format
     * ({@code count + Balance + Available Balance}), with both totals
     * formatted via {@link AmountFormatter#forDisplay} so the Excel
     * header reads identically to the on-screen summary tile and the
     * print popup (locale-aware thousand separators + currency symbol
     * per CLAUDE.md "Excel export" rule).
     *
     * <p>Subclasses may override when the default isn't appropriate.
     * The FCY provider drops the totals when wire {@code currency=FCY}
     * (mixed currencies — sums are not meaningful) and uses the parent
     * format when a concrete CCY is selected.
     *
     * <p>e.g. VND output:
     * {@code "20 account(s)  ·  Balance: 19.886.030.287₫  ·  Available Balance: 8.417.734.439₫"}
     *
     * <p>Plain-text placeholders for now — Sprint 2 may push the labels
     * through {@code MessageResolver} for full i18n.
     */
    protected String composeSubHeader(int rowCount, AccountListResponse data, String currency) {
        return String.format(
                Locale.ROOT,
                "%d account(s)  ·  Balance: %s  ·  Available Balance: %s",
                rowCount,
                AmountFormatter.forDisplay(data.balance(), currency),
                AmountFormatter.forDisplay(data.availableBal(), currency)
        );
    }

    /**
     * Translate an {@link AccountRow} into a {@code FIELD_ID}-keyed map.
     * Fields the View Setting popup allows the user to see but the
     * legacy DTO doesn't carry directly (e.g. {@code VAS_ACCOUNT}) emit
     * an empty string so the cell renders blank rather than missing.
     */
    private Map<String, Object> toFieldMap(AccountRow r) {
        Map<String, Object> m = new HashMap<>();
        m.put("BANK", composeBankCell(r));
        m.put("ACCOUNT_NICKNAME", nullToEmpty(r.acctNickNm()));
        m.put("BALANCE", r.bal()); // raw wire decimal — writer formats #,##0.##
        m.put("AVAILABLE_BALANCE", r.realAmt());
        m.put("VAS_ACCOUNT", "");
        // Humanized "X ago" (UTC-aware, localised) — matches the on-screen
        // cell + legacy formatExcel, not the raw YYYYMMDDHHmmss. Blank when
        // the account has never synced (mirrors the empty screen cell).
        m.put("LAST_UPDATE", relativeTimeFormatter.humanize(r.acctBalUpdDtm()));
        return m;
    }

    /** Bank cell mirrors the on-screen "BankName · CCY • Type / acctNo"
     *  composite: name plus currency + type plus account number on a
     *  single line so the spreadsheet user can identify the row at a
     *  glance. Uses raw legacy codes when display names are absent. */
    private static String composeBankCell(AccountRow r) {
        StringBuilder b = new StringBuilder();
        b.append(safeBankName(r));
        String type = nullToEmpty(r.acctTypeNm()).trim();
        String ccy = nullToEmpty(r.ccyCd()).trim();
        if (!ccy.isEmpty() || !type.isEmpty()) {
            b.append("  ·  ");
            if (!ccy.isEmpty()) b.append(ccy);
            if (!ccy.isEmpty() && !type.isEmpty()) b.append(" ");
            if (!type.isEmpty()) b.append(type);
        }
        String acctNo = nullToEmpty(r.acctNo()).trim();
        if (!acctNo.isEmpty()) {
            b.append(" / ").append(acctNo);
        }
        return b.toString();
    }

    private static String safeBankName(AccountRow r) {
        String nm = r.bankNm();
        if (nm != null && !nm.isBlank()) return nm;
        return nullToEmpty(r.bankCd());
    }

    private static String stringOrNull(Object raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String stringOrDefault(Object raw, String defaultValue) {
        String s = stringOrNull(raw);
        return s != null ? s : defaultValue;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
