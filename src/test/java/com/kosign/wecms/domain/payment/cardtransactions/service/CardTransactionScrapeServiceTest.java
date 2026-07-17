package com.kosign.wecms.domain.payment.cardtransactions.service;

import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService.DecryptedCredential;
import com.kosign.wecms.domain.banking.nxisas.NxisasBankConfig;
import com.kosign.wecms.domain.banking.nxisas.NxisasGatewayClient;
import com.kosign.wecms.domain.banking.nxisas.NxisasGatewayProperties;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitQnaRequest;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasApiOut;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardUsingCardArr;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardUsingEntry;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardUsingJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardUsingJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCommon;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobResult;
import com.kosign.wecms.domain.banking.nxisas.session.NxisasSessionRegistry;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardSyncStatusResponse;
import com.kosign.wecms.domain.payment.cardtransactions.dto.SyncCardTransactionsRequest;
import com.kosign.wecms.domain.payment.cardtransactions.entity.CardMstr;
import com.kosign.wecms.domain.payment.cardtransactions.entity.CardTrnsHstr;
import com.kosign.wecms.domain.payment.cardtransactions.repository.CardMstrRepository;
import com.kosign.wecms.domain.payment.cardtransactions.repository.CardTrnsHstrRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage for the async (Phase 2) session/challenge-aware Card Transactions
 * Update flow. The service now starts a background scrape segment on an
 * executor and returns a RUNNING snapshot immediately; here the executor is
 * <b>inline</b> ({@code Runnable::run}) so each segment runs synchronously and
 * the test reads the settled state via {@link CardTransactionScrapeService#status}.
 *
 * <p>Verifies the audit-fix behaviour: Shinhan's QNA challenge is pumped
 * through the shared session framework, then {@code CARD_USING} runs and rows
 * persist; a wrong answer fails the batch with no rows; not resuming (frontend
 * cancel) writes nothing; a non-challenge bank (Woori) completes directly; the
 * Auto / manual date window matches Bank Transaction Sync; a universal
 * {@code /cancel} stops before the next card.
 *
 * <p>Uses a REAL {@link NxisasSessionRegistry} (so a challenge → resume
 * round-trip finds the same session), a REAL {@link CardTransactionPersister}
 * over mocked repos (so the D5 composite-key dedup is exercised), and a REAL
 * {@link CardTransactionScrapePersistence} boundary. The gateway is mocked at
 * its {@code firstResult} / {@code firstOutput} / {@code firstCardUsingResult}
 * seams so the test never touches Coocon.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CardTransactionScrapeServiceTest {

    private static final String TENANT     = "VN_VNM0004_000001";
    private static final String USER       = "tester";
    private static final String SHINHAN_VN = "11704088";
    private static final String WOORI_VN   = "11704020";
    private static final String CARD_NO    = "407465******5559";
    private static final BigDecimal CERT_SEQ = new BigDecimal("5");
    private static final String OK = "00000000";

    @Mock private CardMstrRepository     cardMstrRepo;
    @Mock private CardTrnsHstrRepository cardTrnsRepo;
    @Mock private LegacyFunctionRepository legacyFns;
    @Mock private BankCredentialService  credentialService;
    @Mock private NxisasGatewayClient    gateway;
    @Mock private NxisasBankConfig       bankConfig;
    @Mock private AuditLogService        auditLogService;
    @Mock private CardBankNameResolver   bankNameResolver;

    /** Inline executor — runs each scrape segment synchronously so the test can
     *  read the settled state immediately after start / submit. */
    private static final Executor INLINE = Runnable::run;

    private NxisasSessionRegistry registry;
    private CardTransactionScrapeService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(USER, TENANT, LangCd.EN);
        registry = new NxisasSessionRegistry(new NxisasGatewayProperties(60, 300));
        CardTransactionPersister persister = new CardTransactionPersister(cardTrnsRepo, legacyFns);
        CardTransactionScrapePersistence persistence =
                new CardTransactionScrapePersistence(persister, cardMstrRepo);
        service = new CardTransactionScrapeService(
                cardMstrRepo, credentialService, gateway, bankConfig, registry, persistence,
                auditLogService, bankNameResolver, INLINE);
        // Short bank-name resolver for the challenge dialog header — card
        // feature shows "Shinhan VN" (not the long "Shinhan(Vietnam)").
        when(bankNameResolver.resolve(SHINHAN_VN)).thenReturn("Shinhan VN");

        when(gateway.newTrSeq()).thenReturn("TRSEQ");
        when(credentialService.loadForScraping(CERT_SEQ)).thenReturn(
                new DecryptedCredential(SHINHAN_VN, "loginid", "loginpwd", null, null, null));
        when(bankConfig.find(any(), any()))
                .thenReturn(Optional.of(new NxisasBankConfig.Entry("BANK_Shinhan", "COMMON")));
        when(gateway.firstOutput(any())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void start_returnsRunningSnapshotImmediately() {
        wireCleanWooriScrape("20260619120000");
        CardSyncStatusResponse start = service.start(
                new SyncCardTransactionsRequest("20251001", "20251031", false));
        // The initial snapshot is captured BEFORE the worker runs — RUNNING with
        // the total-card count already known so the popup can show "0 / N".
        assertThat(start.status()).isEqualTo("RUNNING");
        assertThat(start.sessionId()).isNotNull();
        assertThat(start.totalCards()).isEqualTo(1);
        assertThat(start.progress()).isZero();
    }

    @Test
    void shinhanQna_pumpsQnaThenCardUsingInserts() {
        when(cardMstrRepo.findActiveCardsForSync(TENANT)).thenReturn(List.of(card(SHINHAN_VN)));

        // 1) LOGIN returns a QNA challenge.
        NxisasApiOut<NxisasLoginJobResult> loginOut = okOut();
        NxisasLoginJobResult qnaChallenge = loginResult("setQNA");
        when(gateway.login(any(), any(), any(), any())).thenReturn(loginOut);
        when(gateway.firstResult(loginOut)).thenReturn(qnaChallenge);

        CardSyncStatusResponse start = service.start(
                new SyncCardTransactionsRequest("20251001", "20251031", false));
        UUID sessionId = start.sessionId();

        // The inline worker already ran to the QNA boundary — read it via status.
        CardSyncStatusResponse awaiting = service.status(sessionId);
        assertThat(awaiting.status()).isEqualTo("QNA_REQUIRED");
        assertThat(awaiting.challenge()).isNotNull();
        assertThat(awaiting.challenge().bankName()).isEqualTo("Shinhan VN");
        verify(cardTrnsRepo, never()).saveAndFlush(any());

        // 2) Submit the answer → login clears → CARD_USING → insert.
        NxisasApiOut<NxisasLoginJobResult> qnaOut = okOut();
        NxisasLoginJobResult cleanLogin = loginResult(null); // no further challenge
        when(gateway.submitQna(any(), any(), any(), any(), any())).thenReturn(qnaOut);
        when(gateway.firstResult(qnaOut)).thenReturn(cleanLogin);

        NxisasApiOut<NxisasCardUsingJobResult> useOut = okOut();
        NxisasCardUsingJobResult useResult = cardUsingResult(scrapedEntry());
        when(gateway.cardUsing(any(), any(), any(), any(), any())).thenReturn(useOut);
        when(gateway.firstCardUsingResult(useOut)).thenReturn(useResult);
        when(cardTrnsRepo.existsLegacyScrapeDuplicate(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(false);
        when(legacyFns.nextCardSeqNo(TENANT)).thenReturn("900");

        CardSyncStatusResponse done = service.submitQna(
                sessionId, new SubmitQnaRequest("hidden-1", "What city?", "Hanoi"));

        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.cardsSynced()).isEqualTo(1);
        assertThat(done.insertedCount()).isEqualTo(1);
        assertThat(done.skippedCount()).isZero();
        assertThat(done.progress()).isEqualTo(100);
        assertThat(done.completedCards()).isEqualTo(1);

        // D5 dedup probe + insert ran through the shared persister.
        verify(cardTrnsRepo).existsLegacyScrapeDuplicate(
                eq(TENANT), eq(CARD_NO), eq("20251019"), eq("114140"),
                eq("VND"), eq(new BigDecimal("799200")), eq("GOLDEN LOTUS"), eq("AUTH1"));
        verify(cardTrnsRepo).saveAndFlush(any(CardTrnsHstr.class));
    }

    @Test
    void wrongQna_failsBatch_noRowsInserted() {
        when(cardMstrRepo.findActiveCardsForSync(TENANT)).thenReturn(List.of(card(SHINHAN_VN)));
        NxisasApiOut<NxisasLoginJobResult> loginOut = okOut();
        NxisasLoginJobResult qnaChallenge = loginResult("setQNA");
        when(gateway.login(any(), any(), any(), any())).thenReturn(loginOut);
        when(gateway.firstResult(loginOut)).thenReturn(qnaChallenge);

        CardSyncStatusResponse start = service.start(
                new SyncCardTransactionsRequest("20251001", "20251031", false));
        assertThat(service.status(start.sessionId()).status()).isEqualTo("QNA_REQUIRED");

        // Wrong answer → gateway rejects (non-OK result code) → FAILED.
        NxisasApiOut<NxisasLoginJobResult> qnaOut = errOut("80010011", "wrong answer");
        when(gateway.submitQna(any(), any(), any(), any(), any())).thenReturn(qnaOut);

        CardSyncStatusResponse res = service.submitQna(
                start.sessionId(), new SubmitQnaRequest("hidden-1", "What city?", "WRONG"));

        assertThat(res.status()).isEqualTo("FAILED");
        assertThat(res.errorCode()).isEqualTo("80010011");
        verify(gateway, never()).cardUsing(any(), any(), any(), any(), any());
        verify(cardTrnsRepo, never()).saveAndFlush(any());
    }

    @Test
    void cancelWhileAwaitingQna_marksCancelled_writesNothing() {
        when(cardMstrRepo.findActiveCardsForSync(TENANT)).thenReturn(List.of(card(SHINHAN_VN)));
        NxisasApiOut<NxisasLoginJobResult> loginOut = okOut();
        NxisasLoginJobResult qnaChallenge = loginResult("setQNA");
        when(gateway.login(any(), any(), any(), any())).thenReturn(loginOut);
        when(gateway.firstResult(loginOut)).thenReturn(qnaChallenge);

        CardSyncStatusResponse start = service.start(
                new SyncCardTransactionsRequest("20251001", "20251031", false));
        UUID sessionId = start.sessionId();
        assertThat(service.status(sessionId).status()).isEqualTo("QNA_REQUIRED");

        // Universal cancel — marks CANCELLED, retained so the final poll reads it.
        CardSyncStatusResponse cancelled = service.cancel(sessionId);
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(service.status(sessionId).status()).isEqualTo("CANCELLED");

        // A resume after cancel is refused (WBE2060) and nothing is scraped.
        assertThatThrownBy(() -> service.submitQna(
                sessionId, new SubmitQnaRequest("hidden-1", "What city?", "Hanoi")))
                .isInstanceOf(BusinessException.class)
                .extracting(b -> ((BusinessException) b).getCode())
                .isEqualTo("WBE2060");
        verify(gateway, never()).cardUsing(any(), any(), any(), any(), any());
        verify(cardTrnsRepo, never()).saveAndFlush(any());
    }

    @Test
    void woori_noChallenge_cardUsingInsertsDirectly() {
        wireCleanWooriScrape(null);
        when(legacyFns.nextCardSeqNo(TENANT)).thenReturn("901");

        CardSyncStatusResponse start = service.start(
                new SyncCardTransactionsRequest("20251001", "20251031", false));
        // Inline worker ran to COMPLETED — read the terminal snapshot.
        CardSyncStatusResponse res = service.status(start.sessionId());

        assertThat(res.status()).isEqualTo("COMPLETED");
        assertThat(res.insertedCount()).isEqualTo(1);
        assertThat(res.progress()).isEqualTo(100);
        verify(cardTrnsRepo).saveAndFlush(any(CardTrnsHstr.class));
        // After a non-empty scrape the last-success cursor advances so the
        // next Update popup opens at "latest updated date ~ today".
        verify(cardMstrRepo).patchTrnLstSucDtm(
                eq(TENANT), eq(WOORI_VN), eq(CARD_NO), any(), eq(USER), any());
    }

    /* ── Date-range (Auto / manual) — parity with Bank Transaction Sync ── */

    @Test
    void auto_startDate_isMinLastSuccessUpdate() {
        wireCleanWooriScrape("20260619120000"); // last successful update
        service.start(new SyncCardTransactionsRequest("20260101", "20260601", true));

        ArgumentCaptor<NxisasCardUsingJobInput> cap = ArgumentCaptor.forClass(NxisasCardUsingJobInput.class);
        verify(gateway).cardUsing(any(), any(), any(), cap.capture(), any());
        assertThat(cap.getValue().getStartDate()).isEqualTo("20260619");
        assertThat(cap.getValue().getEndDate()).isEqualTo(todayYyyymmdd());
    }

    @Test
    void auto_noLastSuccess_fallsBackTo90Days() {
        wireCleanWooriScrape(null); // never synced
        service.start(new SyncCardTransactionsRequest("20260101", "20260601", true));

        ArgumentCaptor<NxisasCardUsingJobInput> cap = ArgumentCaptor.forClass(NxisasCardUsingJobInput.class);
        verify(gateway).cardUsing(any(), any(), any(), cap.capture(), any());
        assertThat(cap.getValue().getStartDate()).isEqualTo(minus90DaysYyyymmdd());
        assertThat(cap.getValue().getEndDate()).isEqualTo(todayYyyymmdd());
    }

    @Test
    void manual_usesProvidedRange_notLastSuccess() {
        wireCleanWooriScrape("20260619120000"); // present, but manual mode ignores it
        service.start(new SyncCardTransactionsRequest("20251001", "20251031", false));

        ArgumentCaptor<NxisasCardUsingJobInput> cap = ArgumentCaptor.forClass(NxisasCardUsingJobInput.class);
        verify(gateway).cardUsing(any(), any(), any(), cap.capture(), any());
        assertThat(cap.getValue().getStartDate()).isEqualTo("20251001");
        assertThat(cap.getValue().getEndDate()).isEqualTo("20251031");
    }

    @Test
    void noCredential_returnsClearError() {
        CardMstr orphan = card(SHINHAN_VN);
        orphan.setBankCertSeq(null);
        when(cardMstrRepo.findActiveCardsForSync(TENANT)).thenReturn(List.of(orphan));

        assertThatThrownBy(() -> service.start(
                new SyncCardTransactionsRequest("20251001", "20251031", false)))
                .isInstanceOf(BusinessException.class)
                .extracting(b -> ((BusinessException) b).getCode())
                .isEqualTo("WBE_CARD_SYNC_NO_CREDENTIAL");
        verify(gateway, never()).login(any(), any(), any(), any());
    }

    @Test
    void invalidCredential_returnsClearError_noScrape() {
        when(cardMstrRepo.findActiveCardsForSync(TENANT)).thenReturn(List.of(card(SHINHAN_VN)));
        when(credentialService.loadForScraping(CERT_SEQ))
                .thenThrow(new BusinessException("WBE2050"));

        assertThatThrownBy(() -> service.start(
                new SyncCardTransactionsRequest("20251001", "20251031", false)))
                .isInstanceOf(BusinessException.class)
                .extracting(b -> ((BusinessException) b).getCode())
                .isEqualTo("WBE_CARD_SYNC_NO_CREDENTIAL");
        verify(gateway, never()).login(any(), any(), any(), any());
        verify(cardTrnsRepo, never()).saveAndFlush(any());
    }

    @Test
    void status_unknownSession_isWBE2060() {
        assertThatThrownBy(() -> service.status(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(b -> ((BusinessException) b).getCode())
                .isEqualTo("WBE2060");
    }

    @Test
    void cancel_unknownSession_isIdempotentCancelled() {
        CardSyncStatusResponse res = service.cancel(UUID.randomUUID());
        assertThat(res.status()).isEqualTo("CANCELLED");
    }

    /* ---------------------------------------------------------------- */

    /** Wire a clean-login (no challenge) Woori scrape that returns one
     *  transaction, with the card's last-success set to {@code lastSuccess}
     *  ({@code yyyyMMddHHmmss} or null). Used by the date-range tests. */
    private void wireCleanWooriScrape(String lastSuccess) {
        CardMstr c = card(WOORI_VN);
        c.setTrnLstSucDtm(lastSuccess);
        when(cardMstrRepo.findActiveCardsForSync(TENANT)).thenReturn(List.of(c));
        when(credentialService.loadForScraping(CERT_SEQ)).thenReturn(
                new DecryptedCredential(WOORI_VN, "loginid", "loginpwd", null, null, null));
        NxisasApiOut<NxisasLoginJobResult> loginOut = okOut();
        NxisasLoginJobResult cleanLogin = loginResult(null);
        when(gateway.login(any(), any(), any(), any())).thenReturn(loginOut);
        when(gateway.firstResult(loginOut)).thenReturn(cleanLogin);
        NxisasApiOut<NxisasCardUsingJobResult> useOut = okOut();
        NxisasCardUsingJobResult useResult = cardUsingResult(scrapedEntry());
        when(gateway.cardUsing(any(), any(), any(), any(), any())).thenReturn(useOut);
        when(gateway.firstCardUsingResult(useOut)).thenReturn(useResult);
        when(cardTrnsRepo.existsLegacyScrapeDuplicate(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(false);
        when(legacyFns.nextCardSeqNo(TENANT)).thenReturn("901");
    }

    private static String todayYyyymmdd() {
        return java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private static String minus90DaysYyyymmdd() {
        return java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(90)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private static CardMstr card(String bankCd) {
        CardMstr c = new CardMstr();
        c.setBizKey(TENANT);
        c.setBankCd(bankCd);
        c.setCardNo(CARD_NO);
        c.setCardTypeCd("1 ");
        c.setCardNm("Card");
        c.setCardHldrNm("HONG GILDONG");
        c.setActvYn("Y");
        c.setBankCertSeq(CERT_SEQ);
        c.setRegId(USER);
        c.setRegDtm("20260101000000");
        c.setModId(USER);
        c.setModDtm("20260101000000");
        return c;
    }

    private static NxisasCardUsingEntry scrapedEntry() {
        NxisasCardUsingEntry e = new NxisasCardUsingEntry();
        e.setCardNum(CARD_NO);
        e.setCardName("Card");
        e.setUsingDate("20251019");
        e.setUsingTime("114140");
        e.setFranchise("GOLDEN LOTUS");
        e.setUsingAmt("799200");
        e.setCurrency("VND");
        e.setAuthCode("AUTH1");
        e.setCity("HANOI");
        e.setPurchaseStatus("Giao dịch được duyệt");
        e.setUsingType("ONLINE");
        return e;
    }

    @SuppressWarnings("unchecked")
    private static <T> NxisasApiOut<T> okOut() {
        NxisasApiOut<T> out = mock(NxisasApiOut.class);
        NxisasCommon common = mock(NxisasCommon.class);
        when(common.getResultCd()).thenReturn(OK);
        when(out.getCommon()).thenReturn(common);
        when(out.getSasSessionID()).thenReturn("SAS");
        return out;
    }

    @SuppressWarnings("unchecked")
    private static <T> NxisasApiOut<T> errOut(String code, String msg) {
        NxisasApiOut<T> out = mock(NxisasApiOut.class);
        NxisasCommon common = mock(NxisasCommon.class);
        when(common.getResultCd()).thenReturn(code);
        when(common.getResultMsg()).thenReturn(msg);
        when(out.getCommon()).thenReturn(common);
        when(out.getSasSessionID()).thenReturn("SAS");
        return out;
    }

    private static NxisasLoginJobResult loginResult(String callBackFunc) {
        NxisasLoginJobResult r = mock(NxisasLoginJobResult.class);
        when(r.getCallBackFunc()).thenReturn(callBackFunc);
        return r;
    }

    private static NxisasCardUsingJobResult cardUsingResult(NxisasCardUsingEntry entry) {
        NxisasCardUsingCardArr arr = mock(NxisasCardUsingCardArr.class);
        when(arr.getCardUsing()).thenReturn(List.of(entry));
        NxisasCardUsingJobResult res = mock(NxisasCardUsingJobResult.class);
        when(res.getCardArr()).thenReturn(List.of(arr));
        return res;
    }
}
