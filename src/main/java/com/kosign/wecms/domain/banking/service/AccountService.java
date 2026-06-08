package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.AcctTypeCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.security.LogMask;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.web.SortDirection;
import com.kosign.wecms.domain.banking.dto.AccountListResponse;
import com.kosign.wecms.domain.banking.dto.AccountLookupListResponse;
import com.kosign.wecms.domain.banking.dto.AccountLookupRow;
import com.kosign.wecms.domain.banking.dto.AccountRow;
import com.kosign.wecms.domain.banking.dto.BankOption;
import com.kosign.wecms.domain.banking.dto.CurrencyTotal;
import com.kosign.wecms.domain.banking.dto.OwnAccountListResponse;
import com.kosign.wecms.domain.banking.dto.TransactionsSyncRequest;
import com.kosign.wecms.domain.banking.dto.TransactionsSyncResponse;
import com.kosign.wecms.domain.banking.bankcredentials.repository.BankCertInfoRepository;
import com.kosign.wecms.domain.banking.entity.AcctMstr;
import com.kosign.wecms.domain.banking.entity.NapasBankInfo;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.repository.CcyAggregate;
import com.kosign.wecms.domain.banking.repository.NapasBankInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
@Slf4j
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
    private final NapasBankInfoRepository napasBankInfoRepo;
    private final BankCertInfoRepository bankCertInfoRepo;
    private final com.kosign.wecms.domain.banking.nxisas.BankTransactionSyncService bankSync;

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

        // HR-3: per-user account authority. Legacy fnsh_0001_001 restricts
        // a non-admin to accounts granted in ACCT_AUTH_INFO
        // (use_yn='Y', auth_type='V') via
        // QueryHelper.getPermittedBankAccountDynamic; admins see every
        // tenant account. Passing null disables the EXISTS predicate
        // (admin path); passing the user id enforces it. Sourced from the
        // signed JWT via TenantContext — never a request parameter.
        final String authUserId = TenantContext.currentIsAdmin()
                ? null
                : TenantContext.currentUserId();

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
                authUserId,
                effective);

        // Cert-connection lookup is per-tenant and small (a handful of
        // distinct bank codes). Load once per list call; rows resolve
        // their certConnYn against an in-memory set keyed by BANK_CD.
        Set<String> connectedBanks = loadConnectedBanks(bizKey);
        List<AccountRow> rows = page.getContent().stream()
                .map(a -> toRow(a, connectedBanks))
                .toList();

        List<CcyAggregate> aggregates = acctRepo.aggregateByCcy(
                bizKey,
                resolved.ccyEq,
                resolved.ccyNot,
                resolved.acctTypeCdIn,
                resolved.bankCd,
                resolved.keywordPattern,
                authUserId);

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
                .findDistinctBankCds(bizKey, resolved.ccyEq, resolved.ccyNot, resolved.acctTypeCdIn, authUserId)
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

        Set<String> connectedBanks = loadConnectedBanks(bizKey);
        List<AccountRow> mapped = rows.stream()
                .map(a -> toRow(a, connectedBanks))
                .toList();
        return new OwnAccountListResponse(
                mapped,
                mapped.size(),
                AmountFormatter.wire(balTotal),
                AmountFormatter.wire(availTotal)
        );
    }

    /**
     * Withdrawal-account autocomplete for the firm-banking-settings
     * Create dialog. Mirrors legacy
     * {@code comm_1004_001_act.jsp:56-66} — resolve the public NAPAS
     * code to the internal {@code BANK_CD} via {@code NAPAS_BANK_INFO},
     * then return every {@code ACCT_MSTR} row for the tenant under
     * that bank.
     *
     * <p>The flow exactly mirrors JEX:
     * <ol>
     *   <li>{@code napasBankInfoRepo.findById(napasBankCd)} →
     *       legacy {@code NapasBankInfoDAO.findByPk}.</li>
     *   <li>{@code acctRepo.findByBizKeyAndBankCdOrderByAcctNoAsc} →
     *       legacy {@code AcctMstrDAO.getRows} with the
     *       {@code biz_key = ? AND bank_cd = ?} {@code IDODynamic}.</li>
     * </ol>
     *
     * <p><b>No keyword filter on the wire.</b> JEX filters client-side
     * after a single load per bank (see legacy
     * {@code comm_0220_002.ts#searchAccounts}); the refactor mirrors
     * that — the autocomplete loads once per bank-change and the
     * frontend filters in-memory. Keeping the contract list-only
     * also means no debounce on the server side, no SQL {@code LIKE}
     * pattern handling.
     *
     * <p><b>No {@code ACTV_YN} predicate</b> — JEX
     * {@code comm_1004_001} doesn't filter on it either. Inactive
     * rows in the binding context are visible by design (legacy
     * tenants commonly bind firm-banking against accounts that are
     * temporarily flagged inactive in {@code ACCT_MSTR}).
     *
     * <p>Tenant scoping: {@code BIZ_KEY} comes from
     * {@link TenantContext#currentBizKey()}; the dictionary
     * resolution is global (no tenant column on
     * {@code NAPAS_BANK_INFO}). Cross-tenant lookups cannot succeed
     * because the {@code BIZ_KEY} predicate is part of the JPQL
     * {@code WHERE} chain.
     *
     * <p>Empty results (NAPAS code unknown, or no accounts under the
     * resolved bank for this tenant) return an empty list — never
     * an error. Mirrors JEX which puts {@code REC = null} (frontend
     * shows an empty dropdown) when the lookup misses.
     *
     * @param napasBankCd The public NAPAS bank code from the wire
     *                    (e.g. {@code "970424"}). Must not be null /
     *                    blank — controller's {@code @Pattern} guard
     *                    enforces format; {@code WBE_VALIDATION} on
     *                    misuse.
     */
    @Transactional(readOnly = true)
    public AccountLookupListResponse lookupForFirmBanking(String napasBankCd) {
        if (napasBankCd == null || napasBankCd.isBlank()) {
            throw new BusinessException("WBE_VALIDATION", "out_bank_cd: is required");
        }
        String safeNapasBankCd = napasBankCd.trim();
        String bizKey          = requireBizKey();

        NapasBankInfo napas = napasBankInfoRepo.findById(safeNapasBankCd).orElse(null);
        if (napas == null || napas.getBankCd() == null || napas.getBankCd().isBlank()) {
            // Unknown / unmapped NAPAS code — empty list, mirroring
            // legacy `comm_1004_001` which puts REC = null. Logged at
            // debug because the frontend's bounded-bank set should
            // make this branch unreachable in normal use; if it
            // fires, it's a config drift between the frontend
            // dropdown and V68's seed.
            log.debug("firm-banking-settings account lookup: NAPAS code {} not in NAPAS_BANK_INFO (or has null bank_cd) — returning empty list",
                    safeNapasBankCd);
            return new AccountLookupListResponse(List.of());
        }

        List<AcctMstr> rows = acctRepo
                .findByBizKeyAndBankCdOrderByAcctNoAsc(bizKey, napas.getBankCd());
        log.debug("firm-banking-settings account lookup returned {} rows for bizKey={} napasBankCd={} internalBankCd={}",
                rows.size(), bizKey, safeNapasBankCd, napas.getBankCd());

        return new AccountLookupListResponse(
                rows.stream().map(AccountService::toLookupRow).toList());
    }

    /* ----------------------------------------------------------------- */
    /*  Transaction Sync — Account Inquiry "Update" popup                */
    /* ----------------------------------------------------------------- */

    /**
     * Bumps the per-account "last successful login" + "last balance
     * inquiry" timestamps for every {@code (bankCd, acctNo)} in the
     * request — the same columns legacy {@code ACCT_MSTR_U002} writes
     * after a real NXISAS scrape ({@code fnsh_0001_001_c001_act.jsp:266-287}).
     *
     * <p>R&amp;D scope deliberately omits the bank-gateway leg (workspace
     * CLAUDE.md: <em>"no bank integration, no coexistence work"</em>),
     * so no {@code ACCT_TRNS_HSTR} rows are inserted, no balance change,
     * no audit-log entry. The frontend "Last Update" column visibly
     * moves because the real sync also stamps {@code ACCT_HIS_LST_INQ_DT}
     * (BankTransactionSyncService.processOneAccount:555) — the source the
     * column now displays per FCY-A02; that's the single user-visible
     * side-effect the scrape guarantees.
     *
     * <p>Fail-fast batch semantics — any {@code (bankCd, acctNo)} that
     * doesn't resolve inside the caller's tenant throws
     * {@code WBE_BANK_ACCOUNT_NOT_FOUND} and rolls the whole transaction
     * back (partial sync would hide a stale-cache bug between the
     * inquiry list and the request).
     */
    /**
     * Account Inquiry &gt; Update popup &rarr; real NXISAS scrape via
     * {@link com.kosign.wecms.domain.banking.nxisas.BankTransactionSyncService}.
     * The orchestrator owns the LOGIN &rarr; TRANSACTION &rarr; BALANCE
     * chain, the SHA-256 dedup, and the {@code ACCT_MSTR} balance
     * refresh; this method is a thin delegate so the existing
     * controller signature + auth path stay untouched.
     *
     * <p>Replaces the prior stub that only bumped
     * {@code LST_LGN_SUC_DTM} / {@code ACCT_BAL_LST_INQ_DTM}. Real
     * {@code credit_count} / {@code debit_count} are now returned;
     * the popup totals badge stops showing {@code 0}.
     */
    public TransactionsSyncResponse syncTransactions(TransactionsSyncRequest req) {
        return bankSync.start(req);
    }

    private static String requireBizKey() {
        String bizKey = TenantContext.currentBizKey();
        if (bizKey == null || bizKey.isBlank()) {
            // The JWT filter populates TenantContext on every authenticated
            // request — an empty context here means an unauthenticated path
            // leaked into the service layer (config bug).
            throw new BusinessException("WBE9999");
        }
        return bizKey;
    }

    /** Project an {@link AcctMstr} row to the narrow autocomplete
     *  shape. Server masks the account number once (via the shared
     *  {@link LogMask#acct}) so every consumer renders the same
     *  string — keeps log lines and the dropdown chip in sync. */
    private static AccountLookupRow toLookupRow(AcctMstr a) {
        return new AccountLookupRow(
                a.getAcctNo(),
                LogMask.acct(a.getAcctNo()),
                a.getCcyCd(),
                a.getAcctNickNm(),
                a.getAcctNm()
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

    /**
     * Tenant-scoped set of {@code BANK_CD} values that have at least one
     * non-blank credential row registered in {@code BANK_CERT_INFO}. An
     * {@code ACCT_MSTR} row is "connected" when its
     * {@link AcctMstr#getBankCd()} appears in this set; everything else
     * is "disconnected". Per-bank (not per-seq) so the answer survives
     * the legacy {@code BANK_CERT_SEQ} FK going stale after a user
     * add/delete on the Login Credentials popup. Loaded once per list
     * call — no N+1.
     */
    private Set<String> loadConnectedBanks(String bizKey) {
        return new HashSet<>(bankCertInfoRepo.findConnectedBankCds(bizKey));
    }

    private AccountRow toRow(AcctMstr a, Set<String> connectedBanks) {
        String bankNm = bankResolver.resolveBankNm(a.getBankCd());
        // Formatted nickname comes from legacy DB function
        // wb_format_acct_nick_nm via @Formula on AcctMstr (installed
        // by V44). Falls back to the raw column if the formula returns
        // null — defensive but unreachable in practice (the function
        // always wraps in COALESCE).
        String displayNick = a.getFormattedAcctNickNm() != null
                ? a.getFormattedAcctNickNm()
                : a.getAcctNickNm();
        // Disconnected-bank derivation. Legacy `fnsh_0005_001.ts:711`
        // tests blank `CERT_INFO_CON` + blank `CERT_INFO_DV` on the row,
        // but the legacy IDO selects those from `ACCT_MSTR` (where they
        // are never populated) instead of joining `BANK_CERT_INFO`,
        // which is where the real credential lives. The refactor
        // restores the intended semantics: a row is connected when its
        // `BANK_CD` has at least one non-blank credential in
        // `BANK_CERT_INFO`. Per-bank rather than per-seq so the result
        // stays correct after the user adds / deletes credentials on
        // the Login Credentials popup (which mints fresh SEQ_NOs that
        // diverge from the existing `ACCT_MSTR.BANK_CERT_SEQ` FKs).
        // Manual-registration accounts skip the cert check entirely
        // (handRegYn wins).
        String handRegYn = "Y".equalsIgnoreCase(a.getHandRegYn()) ? "Y" : "N";
        boolean certConnected = a.getBankCd() != null && connectedBanks.contains(a.getBankCd());
        String certConnYn = certConnected ? "Y" : "N";
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
                // FCY-A02 (PO decision 2026-06-06 — supersedes HR-1): the
                // "Last Update" column on fnsh_0005_001 / fnsh_0001_001
                // displays ACCT_HIS_LST_INQ_DT (the transaction-history
                // last-inquiry date), falling back to ACCT_BAL_LST_INQ_DTM
                // only for fixed-deposit-only banks — see resolveLastUpdate.
                // The Bank Transaction Sync orchestrator
                // (BankTransactionSyncService.processOneAccount:555) stamps
                // ACCT_HIS_LST_INQ_DT on every successful scrape, so the
                // displayed value still moves after an Update. The wire
                // field name (acct_bal_upd_dtm) is intentionally unchanged
                // so the frontend mapper + export provider contract stay
                // intact (same convention HR-1 used) — only the value
                // source changed.
                resolveLastUpdate(a),
                handRegYn,
                certConnYn
        );
    }

    /**
     * Legacy "Last Update" source resolution for Account Inquiry
     * (fnsh_0005_001 / fnsh_0001_001). Online accounts display
     * {@code ACCT_HIS_LST_INQ_DT} (the transaction-history last-inquiry
     * date); fixed-deposit-only banks ({@code ACCT_TYPE_CD '02'} other
     * than Sacombank {@code 11704004}) run no TRANSACTION job so the
     * history date is never stamped — those fall back to
     * {@code ACCT_BAL_LST_INQ_DTM}. Mirrors the legacy ViewSetting cell
     * branch at {@code fnsh_0005_001_api_osmc.ts:956-969}
     * ({@code (ACCT_TYPE_CD == "02" && BANK_CD != "11704004") ?
     * ACCT_BAL_LST_INQ_DTM : ACCT_HIS_LST_INQ_DT}).
     */
    private static String resolveLastUpdate(AcctMstr a) {
        boolean fixedDepositOnly = "02".equals(a.getAcctTypeCd())
                && !"11704004".equals(a.getBankCd());
        return fixedDepositOnly ? a.getAcctBalLstInqDtm() : a.getAcctHisLstInqDt();
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
