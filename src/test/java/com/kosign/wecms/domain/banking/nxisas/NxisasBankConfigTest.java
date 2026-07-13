package com.kosign.wecms.domain.banking.nxisas;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link NxisasBankConfig}, focused on the VP Bank
 * (11704102) default-profile defect fix.
 *
 * <p>Defect (docs/audits/bank-profile-enterprise-audit.md §13): VP Bank's
 * {@code ENTRIES} default was {@code PERSONAL} with a redundant
 * {@code 11704102|0 → CORPORATE} sub-type override, so {@code find(cd, 0)}
 * (LOGIN) resolved CORPORATE while {@code find(cd, null)} (sync
 * TRANSACTION/BALANCE) resolved PERSONAL. The fix flips the default to
 * CORPORATE, removes the {@code |0} override, and adds an explicit
 * {@code 11704102|1 → PERSONAL} override so the Personal path is preserved.
 */
class NxisasBankConfigTest {

    private static final String VP = "11704102";
    private static final String VP_MODULE = "GLOBAL/VNM/BANK_VP";

    private final NxisasBankConfig config = new NxisasBankConfig();

    // ── VP Bank default-profile fix ──────────────────────────────────

    @Test
    void vpDefaultResolvesCorporate() {
        NxisasBankConfig.Entry e = config.find(VP).orElseThrow();
        assertThat(e.module()).isEqualTo(VP_MODULE);
        assertThat(e.category()).isEqualTo("CORPORATE");
    }

    @Test
    void vpSeqZeroResolvesCorporate() {
        NxisasBankConfig.Entry e = config.find(VP, 0).orElseThrow();
        assertThat(e.module()).isEqualTo(VP_MODULE);
        assertThat(e.category()).isEqualTo("CORPORATE");
    }

    @Test
    void vpSeqNullResolvesCorporate() {
        NxisasBankConfig.Entry e = config.find(VP, null).orElseThrow();
        assertThat(e.module()).isEqualTo(VP_MODULE);
        assertThat(e.category()).isEqualTo("CORPORATE");
    }

    @Test
    void vpSeqOneFallsBackToCorporate() {
        // PERSONAL profiles were removed (enterprise-only, audit §16).
        // VP seq=1 no longer has an override → falls back to the
        // enterprise CORPORATE default.
        NxisasBankConfig.Entry e = config.find(VP, 1).orElseThrow();
        assertThat(e.module()).isEqualTo(VP_MODULE);
        assertThat(e.category()).isEqualTo("CORPORATE");
    }

    @Test
    void vpLoginTransactionBalanceResolveSameClass() {
        // The core consistency guarantee: LOGIN (seq from credential, 0)
        // and the sync TRANSACTION/BALANCE steps (which now thread the
        // same seq) must resolve the SAME Class. With the |0 override
        // gone, find(VP,0) == find(VP,null) == find(VP) == CORPORATE.
        String login = config.find(VP, 0).orElseThrow().category();
        String txOrBalWithSameSeq = config.find(VP, 0).orElseThrow().category();
        String legacyNullPath = config.find(VP, null).orElseThrow().category();
        assertThat(login).isEqualTo("CORPORATE");
        assertThat(txOrBalWithSameSeq).isEqualTo(login);
        assertThat(legacyNullPath).isEqualTo(login);
    }

    // ── Structural invariant: no SEQ_NO=0 override diverges from default ─

    @Test
    void noConfiguredBankHasSeqZeroOverrideDivergence() {
        // After the VP fix, no bank carries a "<cd>|0" sub-type override,
        // so find(cd, 0) must equal find(cd, null) for every configured
        // bank. This is what makes threading the session's primitive-int
        // seqNo (0 when the credential seqNo is null) safe.
        for (String bankCd : new String[] {
                "11704001", "11704002", "11704003", "11704004", "11704007",
                "11704008", "11704011", "11704012", "11704013", "11704014",
                "11704020", "11704080", "11704088", "11740099", "11704100",
                "11704101", "11704102", "11704103", "11704104", "11704105",
                "11704106", "11704108", "11704112" }) {
            Optional<NxisasBankConfig.Entry> withZero = config.find(bankCd, 0);
            Optional<NxisasBankConfig.Entry> withNull = config.find(bankCd, null);
            assertThat(withZero).as("find(%s, 0) present", bankCd).isPresent();
            assertThat(withZero.get().category())
                    .as("find(%s, 0) == find(%s, null)", bankCd, bankCd)
                    .isEqualTo(withNull.orElseThrow().category());
            assertThat(withZero.get().module()).isEqualTo(withNull.orElseThrow().module());
        }
    }

    // ── Regression: other sub-typed banks still resolve correctly ─────

    @Test
    void removedPersonalSubTypesFallBackToEnterpriseDefault() {
        // The ten PERSONAL sub-type overrides were removed (audit §16).
        // A seq=1/2 lookup for those banks now resolves the enterprise
        // SEQ_NO=0 default — never PERSONAL.
        assertThat(config.find("11704001", 1).orElseThrow().category()).isEqualTo("CORPORATE");        // Vietin
        assertThat(config.find("11704002", 1).orElseThrow().category()).isEqualTo("CORPORATE");        // Techcom
        assertThat(config.find("11704007", 1).orElseThrow().category()).isEqualTo("CORPORATE");        // MB
        assertThat(config.find("11704008", 1).orElseThrow().category()).isEqualTo("CORPORATE_IBANK");  // BIDV
        assertThat(config.find("11704088", 1).orElseThrow().category()).isEqualTo("COMMON");           // Shinhan
        assertThat(config.find("11704100", 1).orElseThrow().category()).isEqualTo("CORPORATE");        // Agribank
        assertThat(config.find("11704101", 1).orElseThrow().category()).isEqualTo("CORPORATE");        // HD
        assertThat(config.find("11704102", 1).orElseThrow().category()).isEqualTo("CORPORATE");        // VP
        assertThat(config.find("11704103", 1).orElseThrow().category()).isEqualTo("CORPORATE");        // TP
    }

    @Test
    void vietcomDigibizKept_personalRemoved() {
        // DIGIBIZ (enterprise SME portal) is the one surviving sub-type.
        assertThat(config.find("11704004").orElseThrow().category()).isEqualTo("CORPORATE");
        assertThat(config.find("11704004", 1).orElseThrow().category()).isEqualTo("DIGIBIZ");
        // seq=2 PERSONAL was removed → falls back to CORPORATE, NOT PERSONAL.
        assertThat(config.find("11704004", 2).orElseThrow().category()).isEqualTo("CORPORATE");
    }

    @Test
    void noPersonalClassResolvesForAnyBankOrSeq() {
        // Behavioural guarantee that no PERSONAL Class remains reachable:
        // sweep every configured bank across seq 0/1/2 and assert the
        // resolved Class is never "PERSONAL".
        for (String bankCd : new String[] {
                "11704001", "11704002", "11704003", "11704004", "11704007",
                "11704008", "11704011", "11704012", "11704013", "11704014",
                "11704020", "11704080", "11704088", "11740099", "11704100",
                "11704101", "11704102", "11704103", "11704104", "11704105",
                "11704106", "11704108", "11704112" }) {
            for (Integer seq : new Integer[] { null, 0, 1, 2 }) {
                config.find(bankCd, seq).ifPresent(e ->
                        assertThat(e.category())
                                .as("bank %s seq %s must not be PERSONAL", bankCd, seq)
                                .isNotEqualTo("PERSONAL"));
            }
        }
    }

    @Test
    void nonSubtypedDefaultsUnchanged() {
        assertThat(config.find("11704003").orElseThrow().category()).isEqualTo("CORPORATE");       // IBK
        assertThat(config.find("11704008").orElseThrow().category()).isEqualTo("CORPORATE_IBANK"); // BIDV
        assertThat(config.find("11704013").orElseThrow().category()).isEqualTo("COMMON");          // KB
        assertThat(config.find("11704104").orElseThrow().category()).isEqualTo("CORPORATE_S2B");   // SC
        assertThat(config.find("11704106").orElseThrow().category()).isEqualTo("COMMON");          // ABBANK
    }

    @Test
    void unknownBankResolvesEmpty() {
        assertThat(config.find("99999999")).isEmpty();
        assertThat(config.find("99999999", 0)).isEmpty();
        assertThat(config.find("99999999", 1)).isEmpty();
    }

    @Test
    void vietnamGdtIsNotAConfiguredCooconBank() {
        // GDT is a LOCAL NXiSAS.exe institution, NOT a Coocon server-gateway
        // bank. VIETNAM_GDT must never carry a NxisasBankConfig Module/Class
        // (audit: gdt-login-credentials-nxisas-legacy-audit.md). Its absence
        // here is intentional — a lookup would (correctly) return empty and
        // any caller routing GDT through the gateway would get WBE2093. The
        // fix removed that miscall from LoginCredentialsService; this test
        // guards against re-adding a GDT entry to the bank config.
        assertThat(config.find("VIETNAM_GDT")).isEmpty();
        assertThat(config.find("VIETNAM_GDT", 0)).isEmpty();
    }

    // ── defaultLoginType (B-1 ABBANK parity) ─────────────────────────

    @Test
    void abbankBlankLoginTypeResolvesEmptyString() {
        assertThat(NxisasBankConfig.defaultLoginType(NxisasBankConfig.AB_BANK_MODULE, null)).isEqualTo("");
        assertThat(NxisasBankConfig.defaultLoginType(NxisasBankConfig.AB_BANK_MODULE, "")).isEqualTo("");
        assertThat(NxisasBankConfig.defaultLoginType(NxisasBankConfig.AB_BANK_MODULE, "   ")).isEqualTo("");
        // module match is case-insensitive (legacy equalsIgnoreCase)
        assertThat(NxisasBankConfig.defaultLoginType("global/vnm/bank_ab", null)).isEqualTo("");
    }

    @Test
    void nonAbbankBlankLoginTypeResolvesId() {
        assertThat(NxisasBankConfig.defaultLoginType("GLOBAL/VNM/BANK_Vietin", null)).isEqualTo("ID");
        assertThat(NxisasBankConfig.defaultLoginType("GLOBAL/GBL/BANK_Shinhan", "")).isEqualTo("ID");
        assertThat(NxisasBankConfig.defaultLoginType(null, null)).isEqualTo("ID");
    }

    @Test
    void explicitLoginTypeIsPreserved() {
        // explicit value wins regardless of bank — including ABBANK
        assertThat(NxisasBankConfig.defaultLoginType(NxisasBankConfig.AB_BANK_MODULE, "CORP")).isEqualTo("CORP");
        assertThat(NxisasBankConfig.defaultLoginType("GLOBAL/VNM/BANK_Vietin", "CERT")).isEqualTo("CERT");
    }

    @Test
    void negativeSeqNoFallsBackToDefault() {
        // find(cd, seqNo) only consults the override map when seqNo >= 0;
        // a negative value degrades to the default entry.
        assertThat(config.find(VP, -1).orElseThrow().category()).isEqualTo("CORPORATE");
    }
}
