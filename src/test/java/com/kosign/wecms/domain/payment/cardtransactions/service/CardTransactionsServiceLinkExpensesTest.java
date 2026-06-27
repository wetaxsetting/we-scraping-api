package com.kosign.wecms.domain.payment.cardtransactions.service;

import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.einvoice.entity.BusaDocMstr;
import com.kosign.wecms.domain.einvoice.repository.BusaDocMstrRepository;
import com.kosign.wecms.domain.payment.cardtransactions.dto.LinkExpensesRequest;
import com.kosign.wecms.domain.payment.cardtransactions.entity.BusaDocRcvPayHis;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C2 — Card Transactions link-expenses child inserts.
 *
 * Pins that the per-doc {@code BUSA_DOC_RCV_PAY_HIS} rows are persisted via a
 * single {@code saveAll} (was per-row {@code save}). Each doc is distinct by
 * {@code doc_id}, so the per-doc {@code wb_get_next_val} allocations stay
 * independent and the batched persist is behaviour-equivalent.
 */
@ExtendWith(MockitoExtension.class)
class CardTransactionsServiceLinkExpensesTest {

    @Mock CardTrnsHstrRepository        cardTrnsRepo;
    @Mock CardTrnsRcvPayInfmRepository  cardTrnsRcvPayRepo;
    @Mock BusaDocMstrRepository         busaDocMstrRepo;
    @Mock LegacyFunctionRepository      legacyFunctions;
    @Mock BusaDocRcvPayHisRepository    busaDocRcvPayHisRepo;
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

    @Test
    void linkExpenses_multipleDistinctDocs_savesAllWithIndependentSeqs_noPerRowSave() {
        CardTrnsHstr card = new CardTrnsHstr();
        card.setBizKey(BIZ);
        card.setCardSeqNo(CARD_SEQ);
        card.setBankCd("11704020");
        card.setCardNo("407465******5559");
        card.setTrscDt("20251019");
        card.setTrscAmt(new BigDecimal("200")); // == 120 + 80
        when(cardTrnsRepo.findByBizKeyAndCardSeqNo(BIZ, CARD_SEQ)).thenReturn(Optional.of(card));
        when(cardTrnsRcvPayRepo.existsByBizKeyAndCardSeqNo(BIZ, CARD_SEQ)).thenReturn(false);

        LinkExpensesRequest req = new LinkExpensesRequest(CARD_SEQ, List.of(
                new LinkExpensesRequest.Item("DOC1", new BigDecimal("120")),
                new LinkExpensesRequest.Item("DOC2", new BigDecimal("80"))));

        BusaDocMstr d1 = new BusaDocMstr();
        d1.setBizKey(BIZ);
        d1.setDocId("DOC1");
        BusaDocMstr d2 = new BusaDocMstr();
        d2.setBizKey(BIZ);
        d2.setDocId("DOC2");
        when(busaDocMstrRepo.findLinkableByDocIds(eq(BIZ), any())).thenReturn(List.of(d1, d2));
        // Per-doc MAX+1 — independent per (biz_key, doc_id); each fresh doc → 1.
        when(legacyFunctions.nextBusaDocRcvPayHisSeqNo(BIZ, "DOC1")).thenReturn("1");
        when(legacyFunctions.nextBusaDocRcvPayHisSeqNo(BIZ, "DOC2")).thenReturn("1");

        service.linkExpenses(req);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BusaDocRcvPayHis>> cap = ArgumentCaptor.forClass(List.class);
        verify(busaDocRcvPayHisRepo).saveAll(cap.capture());
        List<BusaDocRcvPayHis> saved = cap.getValue();

        // One batched saveAll with one row per distinct doc, in request order.
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(BusaDocRcvPayHis::getDocId).containsExactly("DOC1", "DOC2");
        assertThat(saved).allSatisfy(r ->
                assertThat(r.getRcvPayHisSeqNo()).isEqualByComparingTo("1"));
        // Per-doc key generation is unchanged and called once per distinct doc.
        verify(legacyFunctions).nextBusaDocRcvPayHisSeqNo(BIZ, "DOC1");
        verify(legacyFunctions).nextBusaDocRcvPayHisSeqNo(BIZ, "DOC2");
        // The per-row save path is no longer used.
        verify(busaDocRcvPayHisRepo, never()).save(any());
    }
}
