package com.kosign.wecms.domain.banking.nxisas;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Per-{@code BANK_CD} Coocon adapter config — supplies the
 * NXISAS {@code Module} path and {@code Class} (CORPORATE / COMMON)
 * that the gateway requires on every thread.
 *
 * <p>Source of truth (legacy): hardcoded in
 * {@code EXCH_0030_001.java:74-75}. Dynamic banks beyond these two
 * resolve via OSMC API config ({@code BNK_BNK001_{BANK_CD}}) — out
 * of refactor-spike scope. When a future MR ports the OSMC config
 * table, this map becomes the local-DB fallback and the lookup
 * routes through that table first.
 */
@Component
public class NxisasBankConfig {

    public record Entry(String module, String category) { }

    private static final Map<String, Entry> ENTRIES = Map.of(
            "11704020", new Entry("GLOBAL/GBL/BANK_Woori",   "CORPORATE"),
            "11704088", new Entry("GLOBAL/GBL/BANK_Shinhan", "COMMON")
    );

    public Optional<Entry> find(String bankCd) {
        return Optional.ofNullable(ENTRIES.get(bankCd));
    }
}
