package com.kosign.wecms.domain.banking.nxisas.session;

import com.kosign.wecms.domain.banking.dto.CurrencySummary;
import com.kosign.wecms.domain.banking.nxisas.session.NxisasScrapeSession.CurrencyTally;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the per-currency "Debit Matched" plumbing added for the Bank
 * Transaction Sync legacy-parity gap: {@link CurrencyTally#addAutoMatched}
 * accumulates independently per currency bucket, and the value surfaces on
 * {@link CurrencySummary#autoMatchedCount()} (the numerator of the popup's
 * "Debit Matched {matched}/{debit} Transaction(s)" row).
 *
 * <p>The auto-match decision logic itself (env-off / 0 / 1 / multiple
 * candidates) is covered by {@code AutoMatchServiceTest} — this test only
 * guards the aggregation + wire-projection the sync service performs.
 */
class NxisasScrapeSessionAutoMatchTallyTest {

    @Test
    void addAutoMatched_accumulatesPerCurrencyIndependently() {
        NxisasScrapeSession session = newSession();

        // VND: two accounts, 1 + 2 debits auto-matched.
        session.currencyTally("VND").addAccount(3, 4, bd("100"), bd("200"));
        session.currencyTally("VND").addAutoMatched(1);
        session.currencyTally("VND").addAccount(0, 2, BigDecimal.ZERO, bd("50"));
        session.currencyTally("VND").addAutoMatched(2);

        // USD: one account, 0 matched (enabled but nothing matched).
        session.currencyTally("USD").addAccount(1, 1, bd("10"), bd("10"));
        session.currencyTally("USD").addAutoMatched(0);

        assertThat(session.currencyTally("VND").autoMatchedCount()).isEqualTo(3);
        assertThat(session.currencyTally("USD").autoMatchedCount()).isZero();
        // The debit counter it is the numerator against stays independent.
        assertThat(session.currencyTally("VND").debitCount()).isEqualTo(6);
        assertThat(session.currencyTally("USD").debitCount()).isEqualTo(1);
    }

    @Test
    void currencySummary_carriesAutoMatchedCount() {
        // Mirrors BankTransactionSyncService.currencySummariesOf projection.
        CurrencyTally t = new CurrencyTally();
        t.addAccount(0, 5, BigDecimal.ZERO, bd("500"));
        t.addAutoMatched(2);

        CurrencySummary summary = new CurrencySummary(
                "VND", t.successCount(), 0,
                t.creditCount(), t.creditTotal().toPlainString(),
                t.debitCount(), t.debitTotal().toPlainString(),
                t.autoMatchedCount());

        assertThat(summary.autoMatchedCount()).isEqualTo(2);
        assertThat(summary.debitCount()).isEqualTo(5);
    }

    private static NxisasScrapeSession newSession() {
        // Minimal constructor args — this test exercises only the currency
        // tally accumulator, none of the gateway/session flow.
        return new NxisasScrapeSession(
                "trSeq", "VN_VNM0004_000001", "tester", null, "", null);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
