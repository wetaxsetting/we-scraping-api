package com.kosign.wecms.domain.payment.cardtransactions.service;

import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.einvoice.entity.BusaDocDetl;
import com.kosign.wecms.domain.einvoice.entity.BusaDocMstr;
import com.kosign.wecms.domain.einvoice.repository.BusaDocDetlRepository;
import com.kosign.wecms.domain.einvoice.repository.BusaDocMstrRepository;
import com.kosign.wecms.domain.payment.cardtransactions.dto.CreateReceiptRequest;
import com.kosign.wecms.domain.payment.cardtransactions.dto.LinkExpensesRequest;
import com.kosign.wecms.domain.payment.cardtransactions.dto.LinkableEInvoiceListResponse;
import com.kosign.wecms.domain.payment.cardtransactions.entity.CardTrnsHstr;
import com.kosign.wecms.domain.payment.cardtransactions.repository.BusaDocRcvPayHisRepository;
import com.kosign.wecms.domain.payment.cardtransactions.repository.CardTrnsHstrRepository;
import com.kosign.wecms.domain.payment.cardtransactions.repository.CardTrnsRcvPayInfmRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Link-reference audit fixes (L1 status exclusion, L2 currency guard,
 * G1 PROV_STTS lifecycle, G2 e-invoice items, G7 DOC_ID funnel).
 * See wecms-api/docs/audits/card-transaction-link-reference-legacy-audit.md.
 */
@ExtendWith(MockitoExtension.class)
class CardTransactionsServiceAuditFixesTest {

    @Mock CardTrnsHstrRepository        cardTrnsRepo;
    @Mock CardTrnsRcvPayInfmRepository  cardTrnsRcvPayRepo;
    @Mock BusaDocMstrRepository         busaDocMstrRepo;
    @Mock BusaDocDetlRepository         busaDocDetlRepo;
    @Mock LegacyFunctionRepository      legacyFunctions;
    @Mock BusaDocRcvPayHisRepository    busaDocRcvPayHisRepo;
    @Mock com.kosign.wecms.domain.payment.cardtransactions.repository.SysDocIdGenRepository sysDocIdGenRepo;
    @Mock com.kosign.wecms.domain.payment.cardtransactions.repository.BusaDocFileRepository busaDocFileRepo;
    @Mock com.kosign.wecms.domain.payment.cardtransactions.service.CardTransactionsFileResolver cardFileResolver;
    @Mock AuditLogService               auditLogService;

    @InjectMocks CardTransactionsService service;

    private static final String BIZ      = "VN_VNM0004_000001";
    private static final String USER     = "tester";
    private static final String CARD_SEQ = "1390";

    @BeforeEach
    void setUp() {
        TenantContext.set(USER, BIZ, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CardTrnsHstr card(String ccy, String amt) {
        CardTrnsHstr c = new CardTrnsHstr();
        c.setBizKey(BIZ);
        c.setCardSeqNo(CARD_SEQ);
        c.setBankCd("11704020");
        c.setCardNo("407465******5559");
        c.setTrscDt("20251019");
        c.setCcyCd(ccy);
        c.setTrscAmt(new BigDecimal(amt));
        return c;
    }

    private BusaDocMstr doc(String docId, String ccy, String amt, String einvStatus) {
        BusaDocMstr d = new BusaDocMstr();
        d.setBizKey(BIZ);
        d.setDocId(docId);
        d.setTrcoTrscCcyCd(ccy);
        d.setTotlAmt(new BigDecimal(amt));
        d.setEinvInvoicestatus(einvStatus);
        return d;
    }

    // ---- L1 -----------------------------------------------------------------

    @Test
    void isLinkable_excludesCancelledReplacedReplacementAdjustment() {
        assertThat(EInvoiceLinkStatuses.isLinkable("Hóa đơn mới")).isTrue();     // Original
        assertThat(EInvoiceLinkStatuses.isLinkable(null)).isTrue();               // status-less
        assertThat(EInvoiceLinkStatuses.isLinkable("Hóa đơn đã bị hủy")).isFalse();       // Cancelled
        assertThat(EInvoiceLinkStatuses.isLinkable("Hóa đơn thay thế")).isFalse();        // Replacement
        assertThat(EInvoiceLinkStatuses.isLinkable("Hóa đơn điều chỉnh")).isFalse();      // Adjustment
        assertThat(EInvoiceLinkStatuses.isLinkable("Hóa đơn đã bị thay thế")).isFalse();  // Replaced
        assertThat(EInvoiceLinkStatuses.isLinkable("Hóa đơn đã bị điều chỉnh")).isFalse();// Adjusted
    }

    @Test
    void link_rejectsInvalidStatus_beforeAnyWrite() {
        when(cardTrnsRepo.findByBizKeyAndCardSeqNo(BIZ, CARD_SEQ)).thenReturn(Optional.of(card("VND", "100")));
        when(cardTrnsRcvPayRepo.existsByBizKeyAndCardSeqNo(BIZ, CARD_SEQ)).thenReturn(false);
        when(busaDocMstrRepo.findByBizKeyAndDocIdIn(eq(BIZ), anyList()))
                .thenReturn(List.of(doc("DOC1", "VND", "100", "Hóa đơn đã bị hủy"))); // Cancelled

        LinkExpensesRequest req = new LinkExpensesRequest(CARD_SEQ,
                List.of(new LinkExpensesRequest.Item("DOC1", new BigDecimal("100"))));

        assertThatThrownBy(() -> service.linkExpenses(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE_LINK_INVALID_STATUS");

        verify(busaDocRcvPayHisRepo, never()).saveAll(anyList());
        verify(cardTrnsRcvPayRepo, never()).save(any());
    }

    // ---- L2 -----------------------------------------------------------------

    @Test
    void link_rejectsCurrencyMismatch() {
        when(cardTrnsRepo.findByBizKeyAndCardSeqNo(BIZ, CARD_SEQ)).thenReturn(Optional.of(card("VND", "100")));
        when(cardTrnsRcvPayRepo.existsByBizKeyAndCardSeqNo(BIZ, CARD_SEQ)).thenReturn(false);
        when(busaDocMstrRepo.findByBizKeyAndDocIdIn(eq(BIZ), anyList()))
                .thenReturn(List.of(doc("DOC1", "USD", "100", "Hóa đơn mới"))); // USD vs VND card

        LinkExpensesRequest req = new LinkExpensesRequest(CARD_SEQ,
                List.of(new LinkExpensesRequest.Item("DOC1", new BigDecimal("100"))));

        assertThatThrownBy(() -> service.linkExpenses(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE_LINK_CURRENCY_MISMATCH");

        verify(busaDocRcvPayHisRepo, never()).saveAll(anyList());
    }

    // ---- G1 -----------------------------------------------------------------

    @Test
    void link_setsProvSttsPaOnFullMatch() {
        when(cardTrnsRepo.findByBizKeyAndCardSeqNo(BIZ, CARD_SEQ)).thenReturn(Optional.of(card("VND", "100")));
        when(cardTrnsRcvPayRepo.existsByBizKeyAndCardSeqNo(BIZ, CARD_SEQ)).thenReturn(false);
        BusaDocMstr d1 = doc("DOC1", "VND", "100", "Hóa đơn mới");
        when(busaDocMstrRepo.findByBizKeyAndDocIdIn(eq(BIZ), anyList())).thenReturn(List.of(d1));
        when(busaDocMstrRepo.findLinkableByDocIds(eq(BIZ), anyList(), anyList())).thenReturn(List.of(d1));
        when(legacyFunctions.nextBusaDocRcvPayHisSeqNo(BIZ, "DOC1")).thenReturn("1");

        LinkExpensesRequest req = new LinkExpensesRequest(CARD_SEQ,
                List.of(new LinkExpensesRequest.Item("DOC1", new BigDecimal("100"))));

        service.linkExpenses(req);

        ArgumentCaptor<BusaDocMstr> cap = ArgumentCaptor.forClass(BusaDocMstr.class);
        verify(busaDocMstrRepo).save(cap.capture());
        assertThat(cap.getValue().getProvStts()).isEqualTo("PA");
    }

    @Test
    void unlink_resetsLinkedDocsToOpen() {
        when(busaDocRcvPayHisRepo.findDocIdsByBizKeyAndRefNo(BIZ, CARD_SEQ))
                .thenReturn(List.of("DOC1", "DOC2"));
        when(busaDocRcvPayHisRepo.deleteByBizKeyAndRefNo(BIZ, CARD_SEQ)).thenReturn(2);
        when(cardTrnsRcvPayRepo.findByBizKeyAndCardSeqNo(BIZ, CARD_SEQ)).thenReturn(List.of());

        service.unlinkExpenses(CARD_SEQ);

        verify(busaDocMstrRepo).resetProvSttsToOpen(eq(BIZ), eq(List.of("DOC1", "DOC2")), eq(USER), any());
    }

    // ---- G7 -----------------------------------------------------------------

    @Test
    void createReceipt_usesSysDocIdGenFunnel_andUpsertsDailyRow() {
        when(legacyFunctions.nextSysDocIdGenDocId(BIZ, "E")).thenReturn("R-20240523005");

        CreateReceiptRequest req = new CreateReceiptRequest(
                "20240523", null, "Merchant X", "0100000000",
                new BigDecimal("100"), "VND", "0000000001", "desc");

        var resp = service.createReceipt(req);

        assertThat(resp.docId()).isEqualTo("R-20240523005");
        ArgumentCaptor<BusaDocMstr> cap = ArgumentCaptor.forClass(BusaDocMstr.class);
        verify(busaDocMstrRepo).save(cap.capture());
        assertThat(cap.getValue().getDocId())
                .isEqualTo("R-20240523005")
                .matches("R-\\d{8}\\d{3}"); // legacy R-yyyyMMddNNN shape
        // USER_ID must be stamped so the busa_doc_mstr_log trigger can allocate
        // USER_TRCO_USE_HIS.SEQ_NO via wb_get_next_val(..., new.user_id).
        assertThat(cap.getValue().getUserId()).isEqualTo(USER);
        verify(legacyFunctions).nextSysDocIdGenDocId(BIZ, "E");
        // Companion upsert records the minted id for the day (req_date = the
        // id's own embedded UTC date "20240523"), so the next same-day call
        // increments instead of colliding on 001.
        verify(sysDocIdGenRepo).upsertDailyDocId(BIZ, "E", "20240523", "R-20240523005");
    }

    @Test
    void createReceipt_failsFastWhenFunnelBranchMissing() {
        when(legacyFunctions.nextSysDocIdGenDocId(BIZ, "E")).thenReturn(null);

        CreateReceiptRequest req = new CreateReceiptRequest(
                "20240523", null, "Merchant X", null,
                new BigDecimal("100"), "VND", null, null);

        assertThatThrownBy(() -> service.createReceipt(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WBE_RECEIPT_DOCID_UNAVAILABLE");
        verify(busaDocMstrRepo, never()).save(any());
        verify(sysDocIdGenRepo, never()).upsertDailyDocId(any(), any(), any(), any());
    }

    // ---- L1 (list) + G2 -----------------------------------------------------

    @Test
    void listLinkableEInvoices_passesInvalidStatusesAndAttachesItems() {
        BusaDocMstr d = doc("DOC1", "VND", "100", "Hóa đơn mới");
        d.setTrcoCmnm("Merchant X");
        when(busaDocMstrRepo.findLinkableEInvoices(
                eq(BIZ), eq("20260601"), eq("20260630"), any(),
                eq(EInvoiceLinkStatuses.INVALID_LINK_STATUSES), any()))
                .thenReturn(new PageImpl<>(List.of(d)));

        BusaDocDetl i1 = new BusaDocDetl();
        i1.setDocId("DOC1");
        i1.setEinvProductname("NLS GIFT PINK SAKURA 3.8/4KG");
        BusaDocDetl i2 = new BusaDocDetl();
        i2.setDocId("DOC1");
        i2.setEinvProductname("Cước vận chuyển");
        when(busaDocDetlRepo.findByBizKeyAndDocIdInOrderByDocIdAscDispOrdAsc(eq(BIZ), anyList()))
                .thenReturn(List.of(i1, i2));

        LinkableEInvoiceListResponse resp =
                service.listLinkableEInvoices("20260601", "20260630", null, 0, 50);

        assertThat(resp.rows()).hasSize(1);
        assertThat(resp.rows().get(0).items())
                .containsExactly("NLS GIFT PINK SAKURA 3.8/4KG", "Cước vận chuyển");
    }
}
