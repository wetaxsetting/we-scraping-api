package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.domain.payment.dispatch.constant.BulkTransferConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M-4 — file-round ({@code FILE_SEQ}) wrap, ported from legacy
 * {@code IDO.TB_FIRM_FILE_MST_R002.xml:34-49}.
 *
 * <p>Legacy clamps the next round per bank: <b>1..99 for Woori ({@code 970457})</b>, <b>1..999 for
 * every other bank</b>, resetting to {@code 1} past the ceiling. The refactor previously used a bare
 * {@code MAX+1}, so the 100th Woori file of a day carried {@code FILE_SEQ="100"} — outside the round
 * range the bank accepts — and past 999 {@code String.format("%03d", …)} emitted a <b>4-character</b>
 * value into a 3-byte header field, changing the record length away from 1000 bytes.
 */
class BulkReceptionFileRoundWrapTest {

    private static final String WOORI = BulkTransferConstants.BANK_CD_WOORI;   // 970457
    private static final String SHINHAN = BulkTransferConstants.BANK_CD_SHINHAN; // 970424

    /** The production formatting step, so the assertions exercise the real invariant. */
    private static String formatted(int round, String bankCd) {
        return String.format("%03d", BulkReceptionService.wrapFileRound(round, bankCd));
    }

    @Test
    @DisplayName("Woori: the 100th file of a day wraps to 001, not 100")
    void woori_hundredthFileOfTheDay_wrapsToOne() {
        // Rounds 1..99 pass through untouched.
        assertThat(formatted(1, WOORI)).isEqualTo("001");
        assertThat(formatted(99, WOORI)).isEqualTo("099");
        // The 100th crosses the ceiling and resets — legacy: MAX+1 > 99 THEN 1.
        assertThat(formatted(100, WOORI)).isEqualTo("001");
        assertThat(formatted(101, WOORI)).isEqualTo("001");
    }

    @Test
    @DisplayName("Non-Woori: ceiling is 999, so 100 is a legal round and 1000 wraps")
    void otherBanks_useThe999Ceiling() {
        // The Woori ceiling must NOT be applied to other banks — 100 is legal for Shinhan.
        assertThat(formatted(100, SHINHAN)).isEqualTo("100");
        assertThat(formatted(999, SHINHAN)).isEqualTo("999");
        assertThat(formatted(1000, SHINHAN)).isEqualTo("001");
        // An unknown / null bank code takes the default branch (legacy ELSE), never the Woori one.
        assertThat(formatted(100, "970436")).isEqualTo("100");
        assertThat(formatted(1000, null)).isEqualTo("001");
    }

    @Test
    @DisplayName("Hard invariant: no round can ever format to more than 3 characters")
    void roundNeverExceedsThreeCharacters() {
        // FILE_SEQ occupies exactly 3 bytes of the H record and one segment of FIRM_FILE_NM.
        // A 4th character shifts every following header field and breaks the 1000-byte record.
        for (String bankCd : new String[]{WOORI, SHINHAN, "970436", null}) {
            for (int round = 1; round <= 2_000; round++) {
                String s = formatted(round, bankCd);
                assertThat(s.length())
                        .as("round %d for bank %s formatted to '%s'", round, bankCd, s)
                        .isEqualTo(3);
                int value = Integer.parseInt(s);
                assertThat(value)
                        .as("round %d for bank %s must stay inside the bank's legal range", round, bankCd)
                        .isBetween(1, WOORI.equals(bankCd)
                                ? BulkTransferConstants.FILE_ROUND_MAX_WOORI
                                : BulkTransferConstants.FILE_ROUND_MAX_DEFAULT);
            }
        }
    }

    @Test
    @DisplayName("Split files are clamped individually, not just the base round")
    void eachSplitFileIsWrappedIndependently() {
        // A 4-way split starting at base 98 for Woori: legacy re-runs R002 per file, so files 3 and 4
        // land past the ceiling and reset. Clamping only the base would have produced 100 and 101.
        int base = 98;
        String[] actual = new String[4];
        for (int i = 0; i < 4; i++) {
            actual[i] = formatted(base + i, WOORI);
        }
        assertThat(actual).containsExactly("098", "099", "001", "001");
    }
}
