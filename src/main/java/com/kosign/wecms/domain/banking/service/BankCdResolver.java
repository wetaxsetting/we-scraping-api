package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.enums.AcctTypeCd;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.banking.entity.BankInfm;
import com.kosign.wecms.domain.banking.repository.BankInfmRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves BANK_CD → display name and ACCT_TYPE_CD → display label /
 * i18n key.
 *
 * <p>Bank-name lookup goes through {@link BankInfmRepository} per the
 * 2026-04-29 audit — no static map of bank codes is allowed.
 *
 * <p>Account-type code resolution routes through {@link AcctTypeCd}
 * ({@code common/enums/}) — the single source of truth for the legacy
 * {@code ACCT_TYPE_CD} value set. The English labels in
 * {@link #ACCT_TYPE_FALLBACK_LABELS} mirror the pre-refactor static
 * map verbatim and are kept as a staging surface for the
 * {@code acct_type_nm} wire field while frontend consumers migrate to
 * the new {@code acct_type_key}-driven render. Localised
 * Korean / Vietnamese strings live in the frontend locale files —
 * Java side never holds non-English text.
 *
 * <p>Code coverage notes are kept in sync with {@link AcctTypeCd}'s
 * Javadoc — the enum is the canonical reference.
 */
@Component
@RequiredArgsConstructor
public class BankCdResolver {

    /**
     * Pre-refactor English labels — kept as a fallback surface until
     * every consumer migrates to the {@code acct_type_key} field. Each
     * value matches the legacy {@code BankCdResolver.ACCT_TYPES} entry
     * byte-for-byte. New consumers should NOT add entries here; extend
     * {@link AcctTypeCd} with a new enum constant + a new
     * {@code bank.acct_type.<code>} locale entry instead.
     */
    private static final Map<String, String> ACCT_TYPE_FALLBACK_LABELS = Map.ofEntries(
            Map.entry("02", "Fixed"),             // DEPOSIT — Time Deposit
            Map.entry("03", "Current"),           // CURRENT_KHR — VND Current
            Map.entry("04", "Foreign Current"),   // CURRENT_USD — USD Current
            Map.entry("05", "Savings"),           // SAVINGS_KHR — VND Savings
            Map.entry("06", "Foreign Savings"),   // SAVINGS_USD — USD Savings
            Map.entry("07", "Current Accounts"),  // CURRENT_KRW — KRW Current
            Map.entry("08", "Saving Accounts")    // SAVINGS_KRW — KRW Savings
    );

    private final BankInfmRepository bankInfmRepo;
    private final LegacyFunctionRepository legacyFunctions;

    /**
     * In-memory cache keyed by BANK_CD. The Vietnamese bank registry has
     * fewer than 30 rows and changes rarely — loading once at startup
     * keeps the per-request lookup O(1) without N+1 queries.
     *
     * If a future Sprint 2 feature needs cache invalidation (e.g. bank
     * registration tooling), expose an explicit refresh method —
     * Sprint 1 has no such write path.
     */
    private final Map<String, String> nameCache = new ConcurrentHashMap<>();

    @PostConstruct
    @Transactional(readOnly = true)
    void warmCache() {
        // Cache the legacy SHORT display name (wb_get_bank_nm(cd,'s') with the
        // formal RPRS_BANK_NM as COALESCE fallback) for every registry bank in
        // one query — matches the legacy grid binding (BANK_NM = short name).
        nameCache.putAll(legacyFunctions.resolveBankNmShortAll());
    }

    /**
     * BANK_CD → displayed bank name. Returns the legacy SHORT name
     * ({@code wb_get_bank_nm(bank_cd, 's')}, from the date-effective
     * {@code BANK_INFM_DETL} registry) — e.g. {@code "Shinhan VN"}, not the
     * formal {@code "Shinhan(Vietnam)"}. Fallback chain mirrors legacy:
     * short → formal ({@code wb_get_bank_nm(bank_cd)} = {@code RPRS_BANK_NM})
     * → raw {@code bankCd}. The short→formal step is done inside the funnel's
     * COALESCE; the resolver adds the final raw-code fallback for codes absent
     * from the registry.
     */
    public String resolveBankNm(String bankCd) {
        if (bankCd == null) return "";
        String cached = nameCache.get(bankCd);
        if (cached != null) return cached;
        // Cache miss — resolve via the funnel (a brand-new bank could be added
        // without an app restart). The funnel already falls back short → formal.
        String resolved = legacyFunctions.resolveBankNmShort(bankCd);
        if (resolved == null || resolved.isBlank()) {
            // Final fallback: formal name then the raw code. (Reached only when
            // the code is unknown to the registry / functions return null.)
            resolved = bankInfmRepo.findById(bankCd)
                    .map(BankInfm::getRprsBankNm)
                    .filter(nm -> !nm.isBlank())
                    .orElse(null);
        }
        if (resolved == null || resolved.isBlank()) {
            return bankCd; // unknown bank — not cached, retried next time
        }
        nameCache.put(bankCd, resolved);
        return resolved;
    }

    /**
     * Legacy English label for an {@code ACCT_TYPE_CD}. Continues to
     * power the {@code acct_type_nm} wire field for one MR cycle so
     * the frontend can stage its switch to {@code acct_type_key}.
     * Falls back to the raw code when the enum / fallback map has no
     * entry — never throws, never returns null.
     */
    public String resolveAcctTypeNm(String acctTypeCd) {
        if (acctTypeCd == null) return "";
        String trimmed = acctTypeCd.trim();
        return ACCT_TYPE_FALLBACK_LABELS.getOrDefault(trimmed, trimmed);
    }

    /**
     * Frontend i18n key for an {@code ACCT_TYPE_CD} —
     * {@code bank.acct_type.<code>}. Returns {@code null} for unknown
     * codes (frontend falls back to {@code acct_type_nm}) and for the
     * intentionally-unmapped {@code 01} (LOAN). Mirrors
     * {@link AcctTypeCd#i18nKey()}; the resolver routes through the
     * enum so a future code addition lands in one place.
     */
    public String resolveAcctTypeKey(String acctTypeCd) {
        return AcctTypeCd.fromCode(acctTypeCd)
                .map(AcctTypeCd::i18nKey)
                .orElse(null);
    }
}
