package com.kosign.wecms.domain.payment.dispatch.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the shared {@code ANT_DIV_CD} vocabulary.
 *
 * <p>{@link AntDivChannel#toStorageCode(String)} is a port of legacy
 * {@code wabooks.enums.AntDivCdT.parse(String)}: it accepts the 1-char code or
 * the channel name, is case-insensitive, and returns {@code null} for anything
 * unrecognised. The one addition over legacy is trimming, which is required
 * because every source column is {@code bpchar}.
 */
class AntDivChannelTest {

    /* ── toStorageCode — name → 1-char (the F-1 narrowing direction) ── */

    @Test
    void names_narrowToOneCharCodes() {
        assertThat(AntDivChannel.toStorageCode("CITAD")).isEqualTo("C");
        assertThat(AntDivChannel.toStorageCode("NAPAS")).isEqualTo("N");
        assertThat(AntDivChannel.toStorageCode("EBANK")).isEqualTo("E");
        assertThat(AntDivChannel.toStorageCode("VCB")).isEqualTo("V");
        assertThat(AntDivChannel.toStorageCode("SWIFT")).isEqualTo("S");
    }

    @Test
    void oneCharCodes_areIdempotent() {
        assertThat(AntDivChannel.toStorageCode("C")).isEqualTo("C");
        assertThat(AntDivChannel.toStorageCode("N")).isEqualTo("N");
        assertThat(AntDivChannel.toStorageCode("E")).isEqualTo("E");
        assertThat(AntDivChannel.toStorageCode("V")).isEqualTo("V");
        assertThat(AntDivChannel.toStorageCode("S")).isEqualTo("S");
    }

    @Test
    void bpcharPadding_isTrimmed() {
        // bpchar(5) reads: a stored 'C' comes back as "C    ", "VCB" as "VCB  ".
        assertThat(AntDivChannel.toStorageCode("C    ")).isEqualTo("C");
        assertThat(AntDivChannel.toStorageCode("CITAD")).isEqualTo("C");
        assertThat(AntDivChannel.toStorageCode("VCB  ")).isEqualTo("V");
        assertThat(AntDivChannel.toStorageCode(" NAPAS ")).isEqualTo("N");
    }

    @Test
    void caseIsIgnored() {
        assertThat(AntDivChannel.toStorageCode("citad")).isEqualTo("C");
        assertThat(AntDivChannel.toStorageCode("Napas")).isEqualTo("N");
        assertThat(AntDivChannel.toStorageCode("s")).isEqualTo("S");
    }

    @Test
    void unrecognised_returnsNull_neverGuessed() {
        assertThat(AntDivChannel.toStorageCode("T")).isNull();          // HAENGSUNG raw code
        assertThat(AntDivChannel.toStorageCode("CITADL")).isNull();     // near-miss
        assertThat(AntDivChannel.toStorageCode("XX")).isNull();
    }

    @Test
    void inter_isNotAFileChannel() {
        // Legacy AntDivCdT has no INTER constant — it is an own-bank marker.
        assertThat(AntDivChannel.toStorageCode("INTER")).isNull();
        // …but it stays a recognised value for the realtime gate's short-circuit.
        assertThat(AntDivChannel.canonical("INTER")).isEqualTo(AntDivChannel.INTER);
    }

    @Test
    void nullAndBlank_returnNull() {
        assertThat(AntDivChannel.toStorageCode(null)).isNull();
        assertThat(AntDivChannel.toStorageCode("")).isNull();
        assertThat(AntDivChannel.toStorageCode("   ")).isNull();
    }

    /* ── fromStorageCode — 1-char → name (the bulk-gate direction) ── */

    @Test
    void fromStorageCode_isStrict() {
        assertThat(AntDivChannel.fromStorageCode("E")).isEqualTo(AntDivChannel.EBANK);
        assertThat(AntDivChannel.fromStorageCode("V")).isEqualTo(AntDivChannel.VCB);
        assertThat(AntDivChannel.fromStorageCode("C")).isEqualTo(AntDivChannel.CITAD);
        assertThat(AntDivChannel.fromStorageCode("N")).isEqualTo(AntDivChannel.NAPAS);
        assertThat(AntDivChannel.fromStorageCode("S")).isEqualTo(AntDivChannel.SWIFT);
        // Anything else is legacy ETC — deliberately NOT operating-hours gated.
        assertThat(AntDivChannel.fromStorageCode("CITAD")).isNull();
        assertThat(AntDivChannel.fromStorageCode("T")).isNull();
        assertThat(AntDivChannel.fromStorageCode(null)).isNull();
        assertThat(AntDivChannel.fromStorageCode("  ")).isNull();
    }

    /* ── toStorageCodeStrict — legacy AntDivCdT.fromValue semantics (F-4 / Option A) ── */

    @Test
    void strict_oneCharCodes_arePreserved() {
        assertThat(AntDivChannel.toStorageCodeStrict("C")).isEqualTo("C");
        assertThat(AntDivChannel.toStorageCodeStrict("N")).isEqualTo("N");
        assertThat(AntDivChannel.toStorageCodeStrict("E")).isEqualTo("E");
        assertThat(AntDivChannel.toStorageCodeStrict("V")).isEqualTo("V");
        assertThat(AntDivChannel.toStorageCodeStrict("S")).isEqualTo("S");
    }

    @Test
    void strict_longFormNames_becomeNull() {
        // THE defining difference from toStorageCode: legacy fromValue rejects the long form.
        assertThat(AntDivChannel.toStorageCodeStrict("CITAD")).isNull();
        assertThat(AntDivChannel.toStorageCodeStrict("NAPAS")).isNull();
        assertThat(AntDivChannel.toStorageCodeStrict("EBANK")).isNull();
        assertThat(AntDivChannel.toStorageCodeStrict("VCB")).isNull();
        assertThat(AntDivChannel.toStorageCodeStrict("SWIFT")).isNull();
    }

    @Test
    void strict_paddedOneCharValues_areAccepted() {
        // bpchar(5) source columns pad: a stored 'C' reads back as "C    ".
        assertThat(AntDivChannel.toStorageCodeStrict("C    ")).isEqualTo("C");
        assertThat(AntDivChannel.toStorageCodeStrict(" N ")).isEqualTo("N");
    }

    @Test
    void strict_caseIsIgnored() {
        assertThat(AntDivChannel.toStorageCodeStrict("c")).isEqualTo("C");
        assertThat(AntDivChannel.toStorageCodeStrict("n")).isEqualTo("N");
    }

    @Test
    void strict_unknownAndInterAndBlank_becomeNull() {
        assertThat(AntDivChannel.toStorageCodeStrict("T")).isNull();       // HAENGSUNG raw
        assertThat(AntDivChannel.toStorageCodeStrict("INTER")).isNull();   // no AntDivCdT constant
        assertThat(AntDivChannel.toStorageCodeStrict("XX")).isNull();
        assertThat(AntDivChannel.toStorageCodeStrict(null)).isNull();
        assertThat(AntDivChannel.toStorageCodeStrict("")).isNull();
        assertThat(AntDivChannel.toStorageCodeStrict("   ")).isNull();
    }

    @Test
    void strict_and_lenient_divergeExactlyOnTheLongForm() {
        // The contract that keeps Payroll (parse) and Vendor/Expense/IT (fromValue) apart.
        assertThat(AntDivChannel.toStorageCode("CITAD")).isEqualTo("C");   // Payroll — parse
        assertThat(AntDivChannel.toStorageCodeStrict("CITAD")).isNull();   // others — fromValue
        // …and agree on everything that is already a 1-char code.
        assertThat(AntDivChannel.toStorageCode("C"))
                .isEqualTo(AntDivChannel.toStorageCodeStrict("C"));
    }

    /* ── round-trip: the two directions must agree ── */

    @Test
    void roundTrip_nameToCodeToName() {
        for (String name : new String[] {
                AntDivChannel.EBANK, AntDivChannel.VCB, AntDivChannel.CITAD,
                AntDivChannel.NAPAS, AntDivChannel.SWIFT }) {
            String code = AntDivChannel.toStorageCode(name);
            assertThat(code).as("code for %s", name).isNotNull();
            assertThat(AntDivChannel.fromStorageCode(code)).as("round-trip %s", name).isEqualTo(name);
        }
    }
}
