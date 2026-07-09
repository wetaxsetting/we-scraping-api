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
import com.kosign.wecms.domain.banking.entity.BankInfm;
import com.kosign.wecms.domain.banking.repository.BankInfmRepository;
import com.kosign.wecms.domain.banking.nxisas.dto.SubmitQnaRequest;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasApiOut;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardUsingCardArr;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardUsingEntry;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardUsingJobInput;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardUsingJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCommon;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobResult;
import com.kosign.wecms.domain.banking.nxisas.session.NxisasSessionRegistry;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CardSyncSessionResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage for the session/challenge-aware Card Transactions Update flow.
 *
 * <p>Verifies the audit-fix behaviour: Shinhan's QNA challenge is pumped
 * through the shared session framework, then {@code CARD_USING} runs and
 * rows persist; a wrong answer surfaces an error with no rows; cancelling
 * (never resuming) writes nothing; a non-challenge bank (Woori) completes
 * directly.
 *
 * <p>Uses a REAL {@link NxisasSessionRegistry} (so a challenge → resume
 * round-trip finds the same session) and a REAL
 * {@link CardTransactionPersister} over mocked repos (so the D5
 * composite-key dedup is exercised, shared with the headless path). The
 * gateway is mocked at its {@code firstResult} / {@code firstOutput} /
 * {@code firstCardUsingResult} seams so the test never touches Coocon.
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
    @Mock private BankInfmRepository     bankInfmRepo;

    private CardTransactionScrapeService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(USER, TENANT, LangCd.EN);
        NxisasSessionRegistry registry = new NxisasSessionRegistry(new NxisasGatewayProperties(60, 300));
        CardTransactionPersister persister = new CardTransactionPersister(cardTrnsRepo, legacyFns);
        service = new CardTransactionScrapeService(
                cardMstrRepo, credentialService, gateway, bankConfig, registry, persister,
                auditLogService, bankInfmRepo);
        // Bank-name lookup for the challenge dialog header.
        BankInfm shinhan = new BankInfm();
        shinhan.setBankCd(SHINHAN_VN);
        shinhan.setRprsBankNm("Shinhan VN");
        when(bankInfmRepo.findById(SHINHAN_VN)).thenReturn(Optional.of(shinhan));
        when(bankInfmRepo.findById(WOORI_VN)).thenReturn(Optional.empty());

        when(gateway.newTrSeq()).thenReturn("TRSEQ");
        when(credentialService.loadForScraping(CERT_SEQ)).thenReturn(
                new DecryptedCredential(SHINHAN_VN, "loginid", "loginpwd", null, null, null));
        when(bankConfig.find(any(), any()))
                .thenReturn(Optional.of(new NxisasBankConfig.Entry("BANK_Shinhan", "COMMON")));
        // Bank-adapter Output check is skipped (null) — challenge / error /
        // success are all driven via the gateway-envelope result code below.
        when(gateway.firstOutput(any())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shinhanQna_pumpsQnaThenCardUsingInserts() {
        when(cardMstrRepo.findActiveCardsForSync(TENANT)).thenReturn(List.of(card(SHINHAN_VN)));

        // 1) LOGIN returns a QNA challenge.
        NxisasApiOut<NxisasLoginJobResult> loginOut = okOut();
        NxisasLoginJobResult qnaChallenge = loginResult("setQNA");
        when(gateway.login(any(), any(), any(), any())).thenReturn(loginOut);
        when(gateway.firstResult(loginOut)).thenReturn(qnaChallenge);

        CardSyncSessionResponse start = service.start(
                new SyncCardTransactionsRequest("20251001", "20251031", false));

        assertThat(start.status()).isEqualTo(CardSyncSessionResponse.Status.QNA_REQUIRED);
        assertThat(start.sessionId()).isNotNull();
        // Bank name resolved for the Security Question dialog header.
        assertThat(start.bankName()).isEqualTo("Shinhan VN");
        // No scrape yet — the bank is still waiting on the answer.
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

        UUID sessionId = start.sessionId();
        CardSyncSessionResponse done = service.submitQna(
                sessionId, new SubmitQnaRequest("hidden-1", "What city?", "Hanoi"));

        assertThat(done.status()).isEqualTo(CardSyncSessionResponse.Status.COMPLETED);
        assertThat(done.cardsSynced()).isEqualTo(1);
        assertThat(done.insertedCount()).isEqualTo(1);
        assertThat(done.skippedCount()).isZero();

        // D5 dedup probe + insert ran through the shared persister.
        verify(cardTrnsRepo).existsLegacyScrapeDuplicate(
                eq(TENANT), eq(CARD_NO), eq("20251019"), eq("114140"),
                eq("VND"), eq(new BigDecimal("799200")), eq("GOLDEN LOTUS"), eq("AUTH1"));
        verify(cardTrnsRepo).saveAndFlush(any(CardTrnsHstr.class));
    }

    @Test
    void wrongQna_returnsError_noRowsInserted() {
        when(cardMstrRepo.findActiveCardsForSync(TENANT)).thenReturn(List.of(card(SHINHAN_VN)));
        NxisasApiOut<NxisasLoginJobResult> loginOut = okOut();
        NxisasLoginJobResult qnaChallenge = loginResult("setQNA");
        when(gateway.login(any(), any(), any(), any())).thenReturn(loginOut);
        when(gateway.firstResult(loginOut)).thenReturn(qnaChallenge);

        CardSyncSessionResponse start = service.start(
                new SyncCardTransactionsRequest("20251001", "20251031", false));
        assertThat(start.status()).isEqualTo(CardSyncSessionResponse.Status.QNA_REQUIRED);

        // Wrong answer → gateway rejects (non-OK result code) → ERROR.
        NxisasApiOut<NxisasLoginJobResult> qnaOut = errOut("80010011", "wrong answer");
        when(gateway.submitQna(any(), any(), any(), any(), any())).thenReturn(qnaOut);

        CardSyncSessionResponse res = service.submitQna(
                start.sessionId(), new SubmitQnaRequest("hidden-1", "What city?", "WRONG"));

        assertThat(res.status()).isEqualTo(CardSyncSessionResponse.Status.ERROR);
        assertThat(res.errorCode()).isEqualTo("80010011");
        verify(gateway, never()).cardUsing(any(), any(), any(), any(), any());
        verify(cardTrnsRepo, never()).saveAndFlush(any());
    }

    @Test
    void cancelQna_neverResumes_writesNothing() {
        when(cardMstrRepo.findActiveCardsForSync(TENANT)).thenReturn(List.of(card(SHINHAN_VN)));
        NxisasApiOut<NxisasLoginJobResult> loginOut = okOut();
        NxisasLoginJobResult qnaChallenge = loginResult("setQNA");
        when(gateway.login(any(), any(), any(), any())).thenReturn(loginOut);
        when(gateway.firstResult(loginOut)).thenReturn(qnaChallenge);

        CardSyncSessionResponse start = service.start(
                new SyncCardTransactionsRequest("20251001", "20251031", false));

        // The frontend cancel = never calling a resume endpoint. Nothing
        // is scraped or persisted; the session idle-evicts.
        assertThat(start.status()).isEqualTo(CardSyncSessionResponse.Status.QNA_REQUIRED);
        verify(gateway, never()).cardUsing(any(), any(), any(), any(), any());
        verify(cardTrnsRepo, never()).saveAndFlush(any());
    }

    @Test
    void woori_noChallenge_cardUsingInsertsDirectly() {
        when(cardMstrRepo.findActiveCardsForSync(TENANT)).thenReturn(List.of(card(WOORI_VN)));
        when(credentialService.loadForScraping(CERT_SEQ)).thenReturn(
                new DecryptedCredential(WOORI_VN, "loginid", "loginpwd", null, null, null));

        NxisasApiOut<NxisasLoginJobResult> loginOut = okOut();
        NxisasLoginJobResult cleanLogin = loginResult(null); // clean login, no challenge
        when(gateway.login(any(), any(), any(), any())).thenReturn(loginOut);
        when(gateway.firstResult(loginOut)).thenReturn(cleanLogin);

        NxisasApiOut<NxisasCardUsingJobResult> useOut = okOut();
        NxisasCardUsingJobResult useResult = cardUsingResult(scrapedEntry());
        when(gateway.cardUsing(any(), any(), any(), any(), any())).thenReturn(useOut);
        when(gateway.firstCardUsingResult(useOut)).thenReturn(useResult);
        when(cardTrnsRepo.existsLegacyScrapeDuplicate(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(false);
        when(legacyFns.nextCardSeqNo(TENANT)).thenReturn("901");

        CardSyncSessionResponse res = service.start(
                new SyncCardTransactionsRequest("20251001", "20251031", false));

        assertThat(res.status()).isEqualTo(CardSyncSessionResponse.Status.COMPLETED);
        assertThat(res.insertedCount()).isEqualTo(1);
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
        // Auto ignores the wire dates: start = TRN_LST_SUC_DTM[0:8], end = today.
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
        // Card with a null BANK_CERT_SEQ — no lowest-seq fallback (req #6).
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
        // BANK_CERT_SEQ is set but points at a missing / cross-tenant
        // BANK_CERT_INFO row — pre-validation must surface the clear
        // re-register error and never start a LOGIN (no partial scrape).
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
