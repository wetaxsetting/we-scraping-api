package com.kosign.wecms.domain.banking.nxisas.session;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Terminal-precedence guarantees for {@link NxisasScrapeSession#tryMarkTerminal}
 * (audit §15 fix D3/D4). The FIRST accepted terminal state wins; a later,
 * different terminal transition is a no-op that returns {@code false} and does
 * not change {@link NxisasScrapeSession#getStatus()} or {@code terminalAt}. This
 * is the core of the cancel-vs-completion race fix: a late Cancel cannot clobber
 * a real COMPLETED/FAILED, and a worker finalizing COMPLETED cannot clobber a
 * CANCELLED a concurrent cancel already committed.
 */
class NxisasScrapeSessionTerminalTest {

    private NxisasScrapeSession session() {
        NxisasScrapeSession s = new NxisasScrapeSession(
                "tr", "VN_VNM0004_000001", "tester", BigDecimal.ZERO, "GLOBAL/GBL/BANK_Shinhan", null);
        s.setMode(NxisasScrapeSession.Mode.SYNC);
        s.setStatus(NxisasScrapeSession.Status.RUNNING);
        return s;
    }

    @Test
    void firstTerminalIsAccepted() {
        NxisasScrapeSession s = session();
        assertThat(s.getTerminalAt()).isNull();

        boolean accepted = s.tryMarkTerminal(NxisasScrapeSession.Status.COMPLETED);

        assertThat(accepted).isTrue();
        assertThat(s.getStatus()).isEqualTo(NxisasScrapeSession.Status.COMPLETED);
        assertThat(s.getTerminalAt()).isNotNull();
    }

    @Test
    void completedCannotBeOverwrittenByCancelled() {
        NxisasScrapeSession s = session();
        s.tryMarkTerminal(NxisasScrapeSession.Status.COMPLETED);

        boolean accepted = s.tryMarkTerminal(NxisasScrapeSession.Status.CANCELLED);

        assertThat(accepted).isFalse();
        assertThat(s.getStatus()).isEqualTo(NxisasScrapeSession.Status.COMPLETED);
    }

    @Test
    void failedCannotBeOverwrittenByCancelled() {
        NxisasScrapeSession s = session();
        s.tryMarkTerminal(NxisasScrapeSession.Status.FAILED);

        boolean accepted = s.tryMarkTerminal(NxisasScrapeSession.Status.CANCELLED);

        assertThat(accepted).isFalse();
        assertThat(s.getStatus()).isEqualTo(NxisasScrapeSession.Status.FAILED);
    }

    @Test
    void cancelledCannotBeOverwrittenByCompleted() {
        // The worker-finalizes-after-cancel case: a committed CANCELLED must
        // survive a later COMPLETED attempt from a worker that raced past its
        // last checkpoint.
        NxisasScrapeSession s = session();
        s.tryMarkTerminal(NxisasScrapeSession.Status.CANCELLED);

        boolean accepted = s.tryMarkTerminal(NxisasScrapeSession.Status.COMPLETED);

        assertThat(accepted).isFalse();
        assertThat(s.getStatus()).isEqualTo(NxisasScrapeSession.Status.CANCELLED);
    }

    @Test
    void repeatedCancelledIsIdempotent() {
        NxisasScrapeSession s = session();
        assertThat(s.tryMarkTerminal(NxisasScrapeSession.Status.CANCELLED)).isTrue();
        assertThat(s.tryMarkTerminal(NxisasScrapeSession.Status.CANCELLED)).isFalse();
        assertThat(s.getStatus()).isEqualTo(NxisasScrapeSession.Status.CANCELLED);
    }

    @Test
    void terminalAtIsStampedExactlyOnce() {
        NxisasScrapeSession s = session();
        s.tryMarkTerminal(NxisasScrapeSession.Status.COMPLETED);
        Instant first = s.getTerminalAt();

        // A rejected later transition must not re-stamp terminalAt.
        s.tryMarkTerminal(NxisasScrapeSession.Status.CANCELLED);

        assertThat(s.getTerminalAt()).isEqualTo(first);
    }

    @Test
    void markTerminalDelegatesToOnceOnlyTransition() {
        // The void markTerminal(...) must inherit the once-only guard so every
        // existing caller (finalize / fail / cancel) respects precedence.
        NxisasScrapeSession s = session();
        s.markTerminal(NxisasScrapeSession.Status.COMPLETED);
        s.markTerminal(NxisasScrapeSession.Status.CANCELLED);

        assertThat(s.getStatus()).isEqualTo(NxisasScrapeSession.Status.COMPLETED);
    }
}
