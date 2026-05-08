package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.AcctTypeCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.banking.dto.AccountListResponse;
import com.kosign.wecms.domain.banking.dto.AccountRow;
import com.kosign.wecms.domain.banking.dto.BankOption;
import com.kosign.wecms.domain.banking.dto.CurrencyTotal;
import com.kosign.wecms.domain.banking.dto.OwnAccountListResponse;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.CcyAggregate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Account Inquiry (VND/FCY) service. Mirrors the legacy
 * {@code fnsh_0001_001_r001} / {@code fnsh_0005_001_r001} read path
 * for the Sprint 1 column subset. The "engine status" indicator the
 * static UI shows (e.g. red error tag) is derived purely from
 * ACCT_BAL_INQ_STTS — Sprint 1 does not invoke any live engine.
 *
 * <p>Sprint 2: server-side pagination + bank/keyword filtering
 * (mirrors JEX {@code BANK_CD}, {@code SRCH_WD}, {@code PAGE_NO},
 * {@code PAGE_PER_CNT} inputs) + ACCT_NICK_NM formatting via the
 * legacy {@code wb_format_acct_nick_nm} PL/pgSQL function migrated to
 * the refactor DB in V44 and exposed through {@code @Formula} on
 * {@link com.kosign.wecms.domain.banking.entity.AcctMstr}.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    /**
     * Largest {@code size} the controller will honour. Matches the
     * frontend {@code PAGE_SIZE_OPTIONS} maximum ({@code 100}). Requests
     * exceeding it are clamped down rather than rejected so an
     * accidental large value still returns a usable response.
     */
    public static final int MAX_PAGE_SIZE = 100;

    /** Default page size when the caller omits {@code size}. Matches
     *  the frontend {@code PAGE_SIZE_OPTIONS[0] = 10} so the dropdown
     *  selector and the initial fetch agree on the same value. */
    public static final int DEFAULT_PAGE_SIZE = 10;

    private final AcctMstrRepository acctRepo;
    private final BankCdResolver bankResolver;

    /**
     * ACCT_TYPE_CD whitelists per CCY scope. Mirrors legacy
     * {@code FnshHelper.fnsh_0001_001_r001_dynamic0} predicates plus
     * the refactor's multi-CCY FCY enhancement. Codes resolved through
     * {@link AcctTypeCd}; {@code 01} (LOAN) is intentionally excluded
     * on every page.
     *
     * <ul>
     *   <li>{@link #VND_TYPE_CODES} — DEPOSIT (02), CURRENT_KHR (03),
     *       SAVINGS_KHR (05) — legacy {@code fnsh_0001_001_r001} VND set.</li>
     *   <li>{@link #USD_TYPE_CODES} — DEPOSIT (02), CURRENT_USD (04),
     *       SAVINGS_USD (06) — legacy {@code fnsh_0005_001_r001} USD set.</li>
     *   <li>{@link #KRW_TYPE_CODES} — DEPOSIT (02), CURRENT_KRW (07),
     *       SAVINGS_KRW (08) — refactor (no JEX twin).</li>
     *   <li>{@link #FCY_TYPE_CODES} — union of USD + KRW + DEPOSIT.
     *       Refactor enhancement; no JEX twin.</li>
     *   <li>{@code ALL} or any other concrete CCY (EUR, JPY, …) ⇒
     *       {@code null} (no type-code restriction; relies on the
     *       {@code ccy_cd} predicate alone).</li>
     * </ul>
     */
    private static final List<String> VND_TYPE_CODES = List.of(
            AcctTypeCd.DEPOSIT.code(),
            AcctTypeCd.CURRENT_KHR.code(),
            AcctTypeCd.SAVINGS_KHR.code());
    private static final List<String> USD_TYPE_CODES = List.of(
            AcctTypeCd.DEPOSIT.code(),
            AcctTypeCd.CURRENT_USD.code(),
            AcctTypeCd.SAVINGS_USD.code());
    private static final List<String> KRW_TYPE_CODES = List.of(
            AcctTypeCd.DEPOSIT.code(),
            AcctTypeCd.CURRENT_KRW.code(),
            AcctTypeCd.SAVINGS_KRW.code());
    private static final List<String> FCY_TYPE_CODES = List.of(
            AcctTypeCd.DEPOSIT.code(),
            AcctTypeCd.CURRENT_USD.code(),
            AcctTypeCd.SAVINGS_USD.code(),
            AcctTypeCd.CURRENT_KRW.code(),
            AcctTypeCd.SAVINGS_KRW.code());

    /**
     * Default ORDER BY mirrors the legacy {@code fnsh_0001_001} listing:
     * {@code PRN_SEQ ASC, BANK_CD ASC, ACCT_NO ASC}. {@code PRN_SEQ} is
     * the manually-assigned print/display order; {@code BANK_CD, ACCT_NO}
     * follow as deterministic tiebreakers. PostgreSQL sorts NULL last on
     * ASC, so rows with an unassigned {@code PRN_SEQ} fall through to the
     * {@code BANK_CD / ACCT_NO} order.
     */
    private static final Sort DEFAULT_SORT =
            Sort.by(Sort.Order.asc("prnSeq"), Sort.Order.asc("bankCd"), Sort.Order.asc("acctNo"));

    /* ------------------------------------------------------------------
     *  Public list overloads — kept for backwards compatibility with
     *  every existing caller (controller export path, OwnAccount stub,
     *  the legacy `list(currency)` form). All converge on
     *  {@link #list(AccountFilter, String, String, Pageable)}.
     * ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public AccountListResponse list(String currency) {
        return list(AccountFilter.currencyOnly(currency), null, null, Pageable.unpaged());
    }

    @Transactional(readOnly = true)
    public AccountListResponse list(String currency, String sortField, String sortDirection) {
        return list(AccountFilter.currencyOnly(currency), sortField, sortDirection, Pageable.unpaged());
    }

    /**
     * Filtered + paginated list. Builds the {@link Pageable} from caller
     * primitives so the controller layer doesn't need to know about
     * Spring Data types. {@code page} is zero-based; {@code size} is
     * clamped to {@link #MAX_PAGE_SIZE} and defaults to {@link
     * #DEFAULT_PAGE_SIZE} when {@code size <= 0}.
     */
    @Transactional(readOnly = true)
    public AccountListResponse list(
            AccountFilter filter,
            String sortField,
            String sortDirection,
            int page,
            int size) {
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int safePage = Math.max(0, page);
        Sort sort = buildSort(AccountSortField.parse(sortField), SortDirection.parse(sortDirection));
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);
        return list(filter, sortField, sortDirection, pageable);
    }

    /**
     * Core list method — every other overload funnels here. Pass {@link
     * Pageable#unpaged()} for the export path and any caller that needs
     * the full filtered set.
     */
    @Transactional(readOnly = true)
    public AccountListResponse list(
            AccountFilter filter,
            String sortField,
            String sortDirection,
            Pageable pageable) {
        final String bizKey = TenantContext.currentBizKey();
        final ResolvedFilter resolved = resolveFilter(filter == null ? AccountFilter.currencyOnly(null) : filter);

        // Apply DEFAULT_SORT only when the caller's Pageable carries no
        // sort of its own (export path passes Sort.unsorted()). When the
        // caller already built a Sort (controller's PageRequest above
        // already wove DEFAULT_SORT in via buildSort), we must not double
        // it.
        Pageable effective = pageable.getSort().isSorted()
                ? pageable
                : applyDefaultSortIfNeeded(pageable, sortField, sortDirection);

        Page<AcctMstr> page = acctRepo.findFiltered(
                bizKey,
                resolved.ccyEq,
                resolved.ccyNot,
                resolved.acctTypeCdIn,
                resolved.bankCd,
                resolved.keywordPattern,
                effective);

        List<AccountRow> rows = page.getContent().stream().map(this::toRow).toList();

        List<CcyAggregate> aggregates = acctRepo.aggregateByCcy(
                bizKey,
                resolved.ccyEq,
                resolved.ccyNot,
                resolved.acctTypeCdIn,
                resolved.bankCd,
                resolved.keywordPattern);

        BigDecimal balTotal = BigDecimal.ZERO;
        BigDecimal availTotal = BigDecimal.ZERO;
        for (CcyAggregate a : aggregates) {
            if (a.balance() != null)      balTotal   = balTotal.add(a.balance());
            if (a.availableBal() != null) availTotal = availTotal.add(a.availableBal());
        }

        List<CurrencyTotal> totalsByCcy = aggregates.stream()
                .map(a -> new CurrencyTotal(
                        a.ccyCd() == null ? "" : a.ccyCd().trim(),
                        Math.toIntExact(a.count()),
                        AmountFormatter.wire(a.balance()),
                        AmountFormatter.wire(a.availableBal())))
                .toList();

        // Distinct bank list for the dropdown — scoped to tenant +
        // currency only, NOT narrowed by bankCd / keyword. The dropdown
        // must stay stable as the user filters: selecting one bank
        // should not remove the others from the option set.
        List<BankOption> banksAvailable = acctRepo
                .findDistinctBankCds(bizKey, resolved.ccyEq, resolved.ccyNot, resolved.acctTypeCdIn)
                .stream()
                .map(cd -> new BankOption(cd, bankResolver.resolveBankNm(cd)))
                .toList();

        // Page.getSize() and getTotalPages() already handle the unpaged
        // case: Spring Data's Chunk#getSize() returns content.size() when
        // the underlying Pageable is unpaged, and PageImpl#getTotalPages()
        // collapses to 1 in that case. No conditional needed.
        return new AccountListResponse(
                rows,
                page.getTotalElements(),
                AmountFormatter.wire(availTotal),
                AmountFormatter.wire(balTotal),
                totalsByCcy,
                page.getNumber(),
                page.getSize(),
                page.getTotalPages(),
                banksAvailable
        );
    }

    @Transactional(readOnly = true)
    public OwnAccountListResponse ownAccounts() {
        // Sprint 1 STUB: Transfer Own Accounts shares the Account Inquiry
        // shape. Real legacy `trns_0001_001` is a 10-step transfer wizard;
        // Sprint 1 does not implement transfer execution.
        // TODO: Sprint 2 — implement POST /transfers/own + wizard state.
        final String bizKey = TenantContext.currentBizKey();
        List<AcctMstr> rows = acctRepo.findByBizKeyAndActvYnOrderByBankCdAscAcctNoAsc(bizKey, "Y");

        BigDecimal balTotal = sum(rows, AcctMstr::getBal);
        BigDecimal availTotal = sum(rows, AcctMstr::getRealAmt);

        List<AccountRow> mapped = rows.stream().map(this::toRow).toList();
        return new OwnAccountListResponse(
                mapped,
                mapped.size(),
                AmountFormatter.wire(balTotal),
                AmountFormatter.wire(availTotal)
        );
    }

    /* ------------------------------------------------------------------
     *  Filter resolution + sort plumbing
     * ------------------------------------------------------------------ */

    /** Translates the wire {@link AccountFilter} into the JPQL parameter
     *  shape {@code AcctMstrRepository#findFiltered} expects. */
    private static ResolvedFilter resolveFilter(AccountFilter filter) {
        String ccyRaw = filter.currency();
        String ccyEq = null;
        String ccyNot = null;
        List<String> acctTypeCdIn = null;
        if (ccyRaw != null && !ccyRaw.isBlank() && !"ALL".equalsIgnoreCase(ccyRaw)) {
            String upper = ccyRaw.toUpperCase(Locale.ROOT);
            switch (upper) {
                case "FCY" -> {
                    ccyNot = "VND";
                    acctTypeCdIn = FCY_TYPE_CODES;
                }
                case "VND" -> {
                    ccyEq = upper;
                    acctTypeCdIn = VND_TYPE_CODES;
                }
                case "USD" -> {
                    ccyEq = upper;
                    acctTypeCdIn = USD_TYPE_CODES;
                }
                case "KRW" -> {
                    ccyEq = upper;
                    acctTypeCdIn = KRW_TYPE_CODES;
                }
                default -> ccyEq = upper; // EUR/JPY/other concrete CCY — no type-code restriction
            }
        }
        String bankCd = blankToNull(filter.bankCd());
        String keywordPattern = buildKeywordPattern(filter.keyword());
        return new ResolvedFilter(ccyEq, ccyNot, acctTypeCdIn, bankCd, keywordPattern);
    }

    /**
     * Lower-case + escape LIKE meta-chars + wrap with {@code %…%}.
     * Uses {@code !} as the escape character so the JPQL pattern stays
     * portable across PostgreSQL's {@code standard_conforming_strings}
     * settings (a backslash literal inside a JPA string requires Java
     * double-escape AND DB-side configuration, which {@code !} sidesteps
     * entirely). The matching {@code ESCAPE '!'} clause in
     * {@link AcctMstrRepository#findFiltered} pairs with this escape
     * character.
     *
     * <p>Returns {@code null} when the keyword is blank so the JPQL
     * guard skips the predicate entirely.
     */
    private static String buildKeywordPattern(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        StringBuilder b = new StringBuilder(trimmed.length() + 2).append('%');
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '%' || c == '_' || c == '!') b.append('!');
            b.append(c);
        }
        b.append('%');
        return b.toString().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Build a Spring Data {@link Sort} from the validated whitelist enum +
     * direction. The user's primary sort is always followed by the default
     * {@code BANK_CD, ACCT_NO} secondary order so equal-keyed rows keep a
     * deterministic order across requests.
     */
    private static Sort buildSort(AccountSortField field, SortDirection direction) {
        if (field == null || direction == null) {
            return DEFAULT_SORT;
        }
        Sort.Order primary = direction == SortDirection.ASC
                ? Sort.Order.asc(field.entityProperty())
                : Sort.Order.desc(field.entityProperty());
        return Sort.by(primary).and(DEFAULT_SORT);
    }

    /** When a caller passes a {@link Pageable} with no sort, weave the
     *  user-driven sort (or DEFAULT_SORT) in. Used by the export path
     *  which builds {@code Pageable.unpaged()} without a Sort. */
    private static Pageable applyDefaultSortIfNeeded(
            Pageable pageable, String sortField, String sortDirection) {
        Sort sort = buildSort(AccountSortField.parse(sortField), SortDirection.parse(sortDirection));
        if (pageable.isUnpaged()) {
            return Pageable.unpaged(sort);
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    /* ------------------------------------------------------------------
     *  Row mapping
     * ------------------------------------------------------------------ */

    private AccountRow toRow(AcctMstr a) {
        String bankNm = bankResolver.resolveBankNm(a.getBankCd());
        // Formatted nickname comes from legacy DB function
        // wb_format_acct_nick_nm via @Formula on AcctMstr (installed
        // by V44). Falls back to the raw column if the formula returns
        // null — defensive but unreachable in practice (the function
        // always wraps in COALESCE).
        String displayNick = a.getFormattedAcctNickNm() != null
                ? a.getFormattedAcctNickNm()
                : a.getAcctNickNm();
        return new AccountRow(
                a.getBankCd() + "_" + a.getAcctNo(),
                a.getBankCd(),
                bankNm,
                a.getAcctNo(),
                a.getAcctTypeCd(),
                bankResolver.resolveAcctTypeNm(a.getAcctTypeCd()),
                bankResolver.resolveAcctTypeKey(a.getAcctTypeCd()),
                a.getCcyCd().trim(),
                displayNick,
                AmountFormatter.wire(a.getBal()),
                AmountFormatter.wire(a.getRealAmt()),
                a.getAcctBalInqStts(),
                a.getAcctBalUpdDtm()
        );
    }

    private static BigDecimal sum(List<AcctMstr> rows, java.util.function.Function<AcctMstr, BigDecimal> pick) {
        BigDecimal acc = BigDecimal.ZERO;
        for (AcctMstr a : rows) {
            BigDecimal v = pick.apply(a);
            if (v != null) acc = acc.add(v);
        }
        return acc;
    }

    /** Internal carrier for the resolved JPQL predicate parameters. */
    private record ResolvedFilter(
            String ccyEq,
            String ccyNot,
            List<String> acctTypeCdIn,
            String bankCd,
            String keywordPattern) { }
}
