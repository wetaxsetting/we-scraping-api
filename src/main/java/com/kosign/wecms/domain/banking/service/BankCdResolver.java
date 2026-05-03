package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.domain.banking.entity.BankInfm;
import com.kosign.wecms.domain.banking.repository.BankInfmRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Resolves BANK_CD → display name and ACCT_TYPE_CD → display label.
 *
 * Bank-name lookup goes through {@link BankInfmRepository} per the
 * 2026-04-29 audit — no static map of bank codes is allowed.
 *
 * Account-type code map is small and stable enough to keep in code;
 * legacy JEX had no dedicated dictionary table. The legacy enum
 * {@code wabooks.enums.AcctTypeCd} is the source of truth — labels here
 * mirror its {@code getLabel()} (with Korean labels for {@code 04} /
 * {@code 06} translated to English; English labels for {@code 07} /
 * {@code 08} kept verbatim). The asymmetric prefix
 * ({@code Foreign Current} for {@code 04} vs. {@code Current Accounts}
 * for {@code 07}) is intentional and preserves the legacy distinction.
 *
 * <p>Code coverage:
 * <ul>
 *   <li>{@code 01} (LOAN) — intentionally unmapped; loans never appear on
 *       Account Inquiry. The legacy {@code FnshHelper.fnsh_0001_001_r001}
 *       filter excludes them on both VND and FCY pages.</li>
 *   <li>{@code 02}–{@code 08} — mapped. Active tenant data
 *       ({@code VN_VNM0004_000001}, 2026-05-03 snapshot) exercises
 *       {@code 02, 03, 04, 05, 07}; {@code 06} / {@code 08} are
 *       forward-looking — defined by the legacy enum but not currently
 *       used by this tenant.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
class BankCdResolver {

    private static final Map<String, String> ACCT_TYPES = Map.ofEntries(
            Map.entry("02", "Fixed"),             // DEPOSIT — Time Deposit
            Map.entry("03", "Current"),           // CURRENT_KHR — VND Current
            Map.entry("04", "Foreign Current"),   // CURRENT_USD — USD Current
            Map.entry("05", "Savings"),           // SAVINGS_KHR — VND Savings
            Map.entry("06", "Foreign Savings"),   // SAVINGS_USD — USD Savings
            Map.entry("07", "Current Accounts"),  // CURRENT_KRW — KRW Current
            Map.entry("08", "Saving Accounts")    // SAVINGS_KRW — KRW Savings
    );

    private final BankInfmRepository bankInfmRepo;

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
        nameCache.putAll(bankInfmRepo.findAll().stream()
                .collect(Collectors.toMap(BankInfm::getBankCd, BankInfm::getRprsBankNm,
                        (a, b) -> a)));
    }

    String resolveBankNm(String bankCd) {
        if (bankCd == null) return "";
        String cached = nameCache.get(bankCd);
        if (cached != null) return cached;
        // Cache miss — fall back to the DB (a brand-new bank could be
        // added without the app being restarted). Memoise the answer.
        return bankInfmRepo.findById(bankCd)
                .map(BankInfm::getRprsBankNm)
                .map(nm -> {
                    nameCache.put(bankCd, nm);
                    return nm;
                })
                .orElse(bankCd);
    }

    String resolveAcctTypeNm(String acctTypeCd) {
        if (acctTypeCd == null) return "";
        return ACCT_TYPES.getOrDefault(acctTypeCd.trim(), acctTypeCd.trim());
    }
}
