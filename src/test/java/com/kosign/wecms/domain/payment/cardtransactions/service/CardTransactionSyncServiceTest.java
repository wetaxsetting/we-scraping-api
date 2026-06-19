package com.kosign.wecms.domain.payment.cardtransactions.service;

import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.repository.BankCertInfoRepository;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCredentialService.DecryptedCredential;
import com.kosign.wecms.domain.banking.nxisas.NxisasBankConfig;
import com.kosign.wecms.domain.banking.nxisas.NxisasGatewayClient;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasApiOut;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardUsingCardArr;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardUsingEntry;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCardUsingJobResult;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasCommon;
import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasLoginJobResult;
import com.kosign.wecms.domain.banking.repository.BankInfmRepository;
import com.kosign.wecms.domain.payment.cardtransactions.dto.SyncCardTransactionsRequest;
import com.kosign.wecms.domain.payment.cardtransactions.dto.SyncCardTransactionsResponse;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage for the Update / Sync de-duplication contract (audit D5).
 *
 * <p>Drives the public {@link CardTransactionSyncService#sync} path
 * through a mocked Coocon gateway down to the persist step and asserts
 * that the dedup probe uses the <b>legacy composite natural key</b>
 * ({@code biz_key, card_no, trsc_dt, trsc_tm, ccy_cd, trsc_amt,
 * merch_nm, trim(auth_code)} — {@code fnsh_0001_001_c001_act.jsp:1335-1357}),
 * inserts when the probe is negative, and skips when positive.
 *
 * <p>Pure Mockito (no Spring context). The gateway's {@code login} /
 * {@code cardUsing} / {@code firstResult} / {@code firstCardUsingResult}
 * seams are stubbed so the test never touches the real Coocon transport.
 */
@ExtendWith(MockitoExtension.class)
class CardTransactionSyncServiceTest {

    private static final String TENANT       = "VN_VNM0004_000001";
    private static final String USER         = "tester";
    private static final String WOORI_VN     = "11704020";
    private static final String CARD_NO      = "407465******5559";
    private static final BigDecimal CERT_SEQ = new BigDecimal("5");

    @Mock private CardMstrRepository       cardMstrRepo;
    @Mock private CardTrnsHstrRepository   cardTrnsRepo;
    @Mock private BankInfmRepository       bankInfmRepo;
    @Mock private BankCertInfoRepository   bankCertInfoRepo;
    @Mock private BankCredentialService    credentialService;
    @Mock private NxisasGatewayClient      gateway;
    @Mock private NxisasBankConfig         bankConfig;
    @Mock private LegacyFunctionRepository legacyFns;
    @Mock private AuditLogService          auditLogService;

    private CardTransactionSyncService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(USER, TENANT, LangCd.EN);
        // The per-card persist + D5 dedup was extracted into
        // CardTransactionPersister (2026-06-19). Wire a REAL persister
        // over the mocked repos so the existsLegacyScrapeDuplicate /
        // saveAndFlush / nextCardSeqNo verifications below still exercise
        // the same dedup path — now shared with CardTransactionScrapeService.
        service = new CardTransactionSyncService(
                cardMstrRepo, bankInfmRepo, bankCertInfoRepo, credentialService,
                gateway, bankConfig, auditLogService,
                new CardTransactionPersister(cardTrnsRepo, legacyFns));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void sync_newRow_probesLegacyCompositeKeyThenInserts() {
        wireGatewayForSingleScrapedEntry();
        // Probe negative → row is new → insert.
        when(cardTrnsRepo.existsLegacyScrapeDuplicate(
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(false);
        when(legacyFns.nextCardSeqNo(TENANT)).thenReturn("900");

        SyncCardTransactionsResponse resp =
                service.sync(new SyncCardTransactionsRequest("20251001", "20251031", false));

        assertThat(resp.insertedCount()).isEqualTo(1);
        assertThat(resp.skippedCount()).isZero();

        // Dedup probe matches the legacy composite natural key EXACTLY —
        // no bank_cd (legacy omits it), normalised/truncated stored values.
        verify(cardTrnsRepo).existsLegacyScrapeDuplicate(
                eq(TENANT),
                eq(CARD_NO),
                eq("20251019"),                 // trsc_dt (normYyyymmdd)
                eq("114140"),                   // trsc_tm (normHhmmss)
                eq("VND"),                      // ccy_cd (normCcy)
                eq(new BigDecimal("799200")),   // trsc_amt (parseAmount)
                eq("GOLDEN LOTUS - TVL"),       // merch_nm (truncate 50)
                eq("AUTH1"));                   // auth_code (truncate 15)

        // Inserted row carries the bank-mapped purchase status (Woori
        // "Giao dịch được duyệt" → "1") and the same normalised values.
        ArgumentCaptor<CardTrnsHstr> saved = ArgumentCaptor.forClass(CardTrnsHstr.class);
        verify(cardTrnsRepo).saveAndFlush(saved.capture());
        CardTrnsHstr row = saved.getValue();
        assertThat(row.getCardSeqNo()).isEqualTo("900");
        assertThat(row.getBankCd()).isEqualTo(WOORI_VN);
        assertThat(row.getCardNo()).isEqualTo(CARD_NO);
        assertThat(row.getTrscDt()).isEqualTo("20251019");
        assertThat(row.getTrscTm()).isEqualTo("114140");
        assertThat(row.getCcyCd()).isEqualTo("VND");
        assertThat(row.getTrscAmt()).isEqualByComparingTo("799200");
        assertThat(row.getMerchNm()).isEqualTo("GOLDEN LOTUS - TVL");
        assertThat(row.getAuthCode()).isEqualTo("AUTH1");
        assertThat(row.getPrchStts()).isEqualTo("1");
        assertThat(row.getInoutDv()).isEqualTo("2");
    }

    @Test
    void sync_existingRow_probeHits_skipsInsert() {
        wireGatewayForSingleScrapedEntry();
        // Probe positive → row already exists → skip, no insert.
        when(cardTrnsRepo.existsLegacyScrapeDuplicate(
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(true);

        SyncCardTransactionsResponse resp =
                service.sync(new SyncCardTransactionsRequest("20251001", "20251031", false));

        assertThat(resp.insertedCount()).isZero();
        assertThat(resp.skippedCount()).isEqualTo(1);
        verify(cardTrnsRepo, never()).saveAndFlush(any(CardTrnsHstr.class));
        verify(legacyFns, never()).nextCardSeqNo(any());
    }

    /* ---------------------------------------------------------------
     *  Gateway pipeline wiring — one card, one credential, one scraped
     *  transaction. Stubs login + card-using success so sync() reaches
     *  persistScrapedRows with a single NxisasCardUsingEntry.
     * --------------------------------------------------------------- */
    @SuppressWarnings("unchecked")
    private void wireGatewayForSingleScrapedEntry() {
        CardMstr card = new CardMstr();
        card.setBizKey(TENANT);
        card.setBankCd(WOORI_VN);
        card.setCardNo(CARD_NO);
        card.setCardTypeCd("1 ");
        card.setCardNm("Woori Card");
        card.setCardHldrNm("HONG GILDONG");
        card.setActvYn("Y");
        card.setBankCertSeq(CERT_SEQ);
        card.setRegId(USER);
        card.setRegDtm("20260101000000");
        card.setModId(USER);
        card.setModDtm("20260101000000");

        when(cardMstrRepo.findActiveCardsForSync(TENANT)).thenReturn(List.of(card));
        lenient().when(bankInfmRepo.findById(any())).thenReturn(Optional.empty());

        when(credentialService.loadForScraping(CERT_SEQ)).thenReturn(
                new DecryptedCredential(WOORI_VN, "loginid", "loginpwd", null, null, null));
        when(bankConfig.find(eq(WOORI_VN), any()))
                .thenReturn(Optional.of(new NxisasBankConfig.Entry("WR_MODULE", "WR_CLASS")));

        when(gateway.newTrSeq()).thenReturn("TRSEQ");

        // LOGIN success, no challenge.
        NxisasApiOut<NxisasLoginJobResult> loginOut = mock(NxisasApiOut.class);
        NxisasCommon loginCommon = mock(NxisasCommon.class);
        when(loginOut.getCommon()).thenReturn(loginCommon);
        when(loginCommon.getResultCd()).thenReturn("00000000");
        lenient().when(loginOut.getSasSessionID()).thenReturn("SAS");
        when(gateway.login(any(), any(), any(), any())).thenReturn(loginOut);
        when(gateway.firstResult(loginOut)).thenReturn(mock(NxisasLoginJobResult.class));

        // CARD_USING success returning one transaction.
        NxisasApiOut<NxisasCardUsingJobResult> useOut = mock(NxisasApiOut.class);
        NxisasCommon useCommon = mock(NxisasCommon.class);
        when(useOut.getCommon()).thenReturn(useCommon);
        when(useCommon.getResultCd()).thenReturn("00000000");
        when(gateway.cardUsing(any(), any(), any(), any(), any())).thenReturn(useOut);

        NxisasCardUsingEntry entry = new NxisasCardUsingEntry();
        entry.setCardNum(CARD_NO);
        entry.setCardName("Woori Card");
        entry.setUsingDate("20251019");
        entry.setUsingTime("114140");
        entry.setFranchise("GOLDEN LOTUS - TVL");
        entry.setUsingAmt("799200");
        entry.setCurrency("VND");
        entry.setAuthCode("AUTH1");
        entry.setCity("HANOI");
        entry.setPurchaseStatus("Giao dịch được duyệt"); // Woori → "1"
        entry.setUsingType("ONLINE");

        NxisasCardUsingCardArr cardArr = mock(NxisasCardUsingCardArr.class);
        when(cardArr.getCardUsing()).thenReturn(List.of(entry));
        NxisasCardUsingJobResult useRes = mock(NxisasCardUsingJobResult.class);
        when(useRes.getCardArr()).thenReturn(List.of(cardArr));
        when(gateway.firstCardUsingResult(useOut)).thenReturn(useRes);
    }
}
