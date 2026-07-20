package com.kosign.wecms.domain.banking.nxisas;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService;
import com.kosign.wecms.domain.banking.dto.SyncStatusResponse;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitChallengeRequest;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLogoutJobInput;
import com.kosign.wecms.domain.banking.nxisas.session.NxisasScrapeSession;
import com.kosign.wecms.domain.banking.nxisas.session.NxisasSessionRegistry;
import com.kosign.wecms.domain.banking.nxisas.session.ScrapeChallengeKind;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.banking.synchistory.service.SyncHistoryWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cancellation coverage for {@link BankTransactionSyncService#cancel}
 * (Phase 1, universal in Phase 2). A live session → CANCELLED + cooperative
 * flag + best-effort gateway LOGOUT; the session is <b>retained</b> (not
 * discarded — the frontend's final poll must read the CANCELLED snapshot, the
 * registry evicts it later). Unknown / wrong-tenant / non-SYNC → idempotent
 * no-op. Cancel while AWAITING_CHALLENGE blocks a later resume ({@code WBE2060}).
 */
class BankTransactionSyncServiceCancelTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String USER_ID = "tester";
    private static final String SHINHAN_MODULE = "GLOBAL/GBL/BANK_Shinhan";

    private NxisasGatewayClient gateway;
    private NxisasSessionRegistry registry;
    private BankTransactionSyncService service;

    @BeforeEach
    void setUp() {
        BankCredentialService credentialService = mock(BankCredentialService.class);
        gateway = mock(NxisasGatewayClient.class);
        AcctMstrRepository acctRepo = mock(AcctMstrRepository.class);
        registry = mock(NxisasSessionRegistry.class);
        SyncHistoryWriter syncHistoryWriter = mock(SyncHistoryWriter.class);
        AcctMstrStatusUpdater statusUpdater = mock(AcctMstrStatusUpdater.class);
        BankTransactionSyncPersistence persistence = mock(BankTransactionSyncPersistence.class);

        service = new BankTransactionSyncService(
                credentialService, gateway, new NxisasBankConfig(), acctRepo,
                syncHistoryWriter, statusUpdater,
                mock(com.kosign.wecms.domain.banking.service.AutoMatchService.class),
                registry, persistence, Runnable::run);

        TenantContext.set(USER_ID, BIZ_KEY, LangCd.EN);
        when(gateway.newTrSeq()).thenReturn("20260714000000");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private NxisasScrapeSession liveSyncSession() {
        NxisasScrapeSession s = new NxisasScrapeSession(
                "20260714000000", BIZ_KEY, USER_ID, BigDecimal.ZERO, SHINHAN_MODULE, null);
        s.setMode(NxisasScrapeSession.Mode.SYNC);
        s.setSasSessionID("sas-live"); // live continuation → LOGOUT fires
        return s;
    }

    @Test
    void cancel_liveSession_setsFlag_logsOut_retains_returnsCancelled() {
        NxisasScrapeSession session = liveSyncSession();
        when(registry.find(eq(session.getSessionId()), eq(BIZ_KEY), eq(USER_ID)))
                .thenReturn(Optional.of(session));

        SyncStatusResponse resp = service.cancel(session.getSessionId());

        assertThat(resp.status()).isEqualTo("CANCELLED");
        assertThat(session.isCancelled()).isTrue();
        assertThat(session.getStatus()).isEqualTo(NxisasScrapeSession.Status.CANCELLED);
        verify(gateway, times(1)).logout(eq(SHINHAN_MODULE), any(), any(),
                any(NxisasLogoutJobInput.class), any());
        // Retained (not discarded) so the final poll can read the terminal state.
        verify(registry, never()).discard(any());
    }

    @Test
    void cancel_unknownSession_isIdempotent_noGatewayCall() {
        UUID unknown = UUID.randomUUID();
        when(registry.find(eq(unknown), eq(BIZ_KEY), eq(USER_ID))).thenReturn(Optional.empty());

        SyncStatusResponse resp = service.cancel(unknown);

        assertThat(resp.status()).isEqualTo("CANCELLED");
        assertThat(resp.sessionId()).isEqualTo(unknown);
        verify(gateway, never()).logout(any(), any(), any(), any(), any());
        verify(registry, never()).discard(any());
    }

    @Test
    void cancel_noLiveContinuation_skipsLogout_stillCancels() {
        NxisasScrapeSession session = liveSyncSession();
        session.setSasSessionID("");
        when(registry.find(eq(session.getSessionId()), eq(BIZ_KEY), eq(USER_ID)))
                .thenReturn(Optional.of(session));

        SyncStatusResponse resp = service.cancel(session.getSessionId());

        assertThat(resp.status()).isEqualTo("CANCELLED");
        assertThat(session.isCancelled()).isTrue();
        verify(gateway, never()).logout(any(), any(), any(), any(), any());
    }

    @Test
    void cancelWhileAwaitingChallenge_thenResume_isRefused() {
        NxisasScrapeSession session = liveSyncSession();
        session.setStatus(NxisasScrapeSession.Status.AWAITING_CHALLENGE);
        session.setChallengeKind(ScrapeChallengeKind.OTP);
        when(registry.find(eq(session.getSessionId()), eq(BIZ_KEY), eq(USER_ID)))
                .thenReturn(Optional.of(session));

        SyncStatusResponse cancelled = service.cancel(session.getSessionId());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(session.isCancelled()).isTrue();

        // Late OTP submit for a cancelled session is refused — cannot resume.
        assertThatThrownBy(() ->
                service.submitOtp(session.getSessionId(), new SubmitChallengeRequest("123456")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "WBE2060");
    }

    @Test
    void cancel_afterCompleted_returnsCompletedUnchanged_noLogout_noOverwrite() {
        NxisasScrapeSession session = liveSyncSession();
        session.markTerminal(NxisasScrapeSession.Status.COMPLETED); // finished before the cancel arrives
        when(registry.find(eq(session.getSessionId()), eq(BIZ_KEY), eq(USER_ID)))
                .thenReturn(Optional.of(session));

        SyncStatusResponse resp = service.cancel(session.getSessionId());

        // COMPLETED wins — cancel is an idempotent no-op that returns the
        // existing terminal snapshot unchanged and fires no gateway LOGOUT.
        assertThat(resp.status()).isEqualTo("COMPLETED");
        assertThat(session.getStatus()).isEqualTo(NxisasScrapeSession.Status.COMPLETED);
        assertThat(session.isCancelled()).isFalse();
        verify(gateway, never()).logout(any(), any(), any(), any(), any());
        verify(registry, never()).discard(any());
    }

    @Test
    void cancel_afterFailed_returnsFailedUnchanged_noLogout() {
        NxisasScrapeSession session = liveSyncSession();
        session.setErrorCode("WBE2095");
        session.markTerminal(NxisasScrapeSession.Status.FAILED);
        when(registry.find(eq(session.getSessionId()), eq(BIZ_KEY), eq(USER_ID)))
                .thenReturn(Optional.of(session));

        SyncStatusResponse resp = service.cancel(session.getSessionId());

        assertThat(resp.status()).isEqualTo("FAILED");
        assertThat(resp.errorCode()).isEqualTo("WBE2095");
        assertThat(session.getStatus()).isEqualTo(NxisasScrapeSession.Status.FAILED);
        assertThat(session.isCancelled()).isFalse();
        verify(gateway, never()).logout(any(), any(), any(), any(), any());
    }

    @Test
    void cancel_repeated_isIdempotent_logsOutExactlyOnce() {
        NxisasScrapeSession session = liveSyncSession();
        when(registry.find(eq(session.getSessionId()), eq(BIZ_KEY), eq(USER_ID)))
                .thenReturn(Optional.of(session));

        SyncStatusResponse first = service.cancel(session.getSessionId());
        SyncStatusResponse second = service.cancel(session.getSessionId());

        assertThat(first.status()).isEqualTo("CANCELLED");
        assertThat(second.status()).isEqualTo("CANCELLED");
        // The second cancel sees an already-terminal session → no second LOGOUT.
        verify(gateway, times(1)).logout(eq(SHINHAN_MODULE), any(), any(),
                any(NxisasLogoutJobInput.class), any());
    }

    @Test
    void cancel_nonSyncSession_isNoOpCancelled() {
        NxisasScrapeSession setupSession = new NxisasScrapeSession(
                "20260714000000", BIZ_KEY, USER_ID, BigDecimal.ZERO, SHINHAN_MODULE, null);
        setupSession.setMode(NxisasScrapeSession.Mode.SETUP);
        setupSession.setSasSessionID("sas-live");
        when(registry.find(eq(setupSession.getSessionId()), eq(BIZ_KEY), eq(USER_ID)))
                .thenReturn(Optional.of(setupSession));

        SyncStatusResponse resp = service.cancel(setupSession.getSessionId());

        assertThat(resp.status()).isEqualTo("CANCELLED");
        assertThat(setupSession.isCancelled()).isFalse();
        verify(gateway, never()).logout(any(), any(), any(), any(), any());
        verify(registry, never()).discard(any());
    }
}
