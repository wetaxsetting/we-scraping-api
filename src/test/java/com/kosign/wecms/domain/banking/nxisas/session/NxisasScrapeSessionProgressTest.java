package com.kosign.wecms.domain.banking.nxisas.session;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Progress-formula unit tests for {@link NxisasScrapeSession} (Phase 2):
 * {@code floor(((completedAccounts + phaseFraction) / totalAccounts) * 100)},
 * clamped [0,100], monotonic. Covers the exact one-account milestone flow
 * (0 → 25 → 35 → 50 → 100) and multi-account monotonicity.
 */
class NxisasScrapeSessionProgressTest {

    private NxisasScrapeSession session(int totalAccounts) {
        NxisasScrapeSession s = new NxisasScrapeSession(
                "tr", "VN_VNM0004_000001", "tester", BigDecimal.ZERO, "", null);
        s.setTotalAccounts(totalAccounts);
        return s;
    }

    @Test
    void oneAccount_flowsThrough_0_25_35_50_100() {
        NxisasScrapeSession s = session(1);
        assertThat(s.getProgress()).isZero();

        s.enterPhase(NxisasScrapeSession.Phase.SCRAPING_TRANSACTIONS, 0.00);
        assertThat(s.getProgress()).isEqualTo(0);

        s.enterPhase(NxisasScrapeSession.Phase.PERSISTING_TRANSACTIONS, 0.25);
        assertThat(s.getProgress()).isEqualTo(25);

        s.enterPhase(NxisasScrapeSession.Phase.PERSISTING_TRANSACTIONS, 0.35);
        assertThat(s.getProgress()).isEqualTo(35);

        s.enterPhase(NxisasScrapeSession.Phase.REFRESHING_BALANCE, 0.50);
        assertThat(s.getProgress()).isEqualTo(50);

        s.completeAccount(true);
        assertThat(s.getProgress()).isEqualTo(100);
        assertThat(s.getCompletedAccounts()).isEqualTo(1);
        assertThat(s.getSuccessAccounts()).isEqualTo(1);
        assertThat(s.getFailedAccounts()).isZero();
    }

    @Test
    void multiAccount_isMonotonic_andSeparatesSuccessFromFailure() {
        NxisasScrapeSession s = session(3);

        s.enterPhase(NxisasScrapeSession.Phase.PERSISTING_TRANSACTIONS, 0.25); // (0+0.25)/3 → 8
        assertThat(s.getProgress()).isEqualTo(8);
        s.completeAccount(true);   // 1/3 → 33
        assertThat(s.getProgress()).isEqualTo(33);

        s.enterPhase(NxisasScrapeSession.Phase.PERSISTING_TRANSACTIONS, 0.35); // (1+0.35)/3 → 45
        assertThat(s.getProgress()).isEqualTo(45);
        s.completeAccount(false);  // 2/3 → 66, failed
        assertThat(s.getProgress()).isEqualTo(66);

        s.completeAccount(true);   // 3/3 → 100
        assertThat(s.getProgress()).isEqualTo(100);

        assertThat(s.getCompletedAccounts()).isEqualTo(3);
        assertThat(s.getSuccessAccounts()).isEqualTo(2);
        assertThat(s.getFailedAccounts()).isEqualTo(1);
    }

    @Test
    void progressNeverDecreases() {
        NxisasScrapeSession s = session(1);
        s.enterPhase(NxisasScrapeSession.Phase.REFRESHING_BALANCE, 0.50);
        assertThat(s.getProgress()).isEqualTo(50);
        // A lower fraction must not roll the bar backward.
        s.enterPhase(NxisasScrapeSession.Phase.SCRAPING_TRANSACTIONS, 0.10);
        assertThat(s.getProgress()).isEqualTo(50);
    }
}
