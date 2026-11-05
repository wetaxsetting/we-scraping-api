package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard.InvoiceRef;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import com.kosign.wecms.domain.weinvoice.dto.LookupCodeRequest;
import com.kosign.wecms.domain.weinvoice.dto.RequestAccepted;
import com.kosign.wecms.domain.weinvoice.repository.InvoiceQueryRepository;
import com.kosign.wecms.domain.weinvoice.repository.LookupCodeRepository;
import com.kosign.wecms.domain.weinvoice.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UI Spec 3.4 — the lookup-code box takes the code or the whole provider link. */
class InvoiceActionServiceTest {

    @Test
    void codeIsReadFromKnownQueryKeys() {
        assertThat(InvoiceActionService.codeFromLink("https://tracuuhoadon.minvoice.vn/?sobaomat=QWE123&mst=1"))
                .isEqualTo("QWE123");
        assertThat(InvoiceActionService.codeFromLink("https://tchd.ehoadon.vn/TCHD?MTC=ABCDEF1234")).isEqualTo("ABCDEF1234");
    }

    @Test
    void codeIsReadFromTheLastPathSegment() {
        assertThat(InvoiceActionService.codeFromLink("https://tracuu.ehoadon.vn/ABC123XYZ")).isEqualTo("ABC123XYZ");
    }

    @Test
    void linkWithoutCodeGivesNull() {
        assertThat(InvoiceActionService.codeFromLink("https://www.meinvoice.vn/tra-cuu/")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void pastedLinkIsStoredAndAPdfFetchQueuedWithIt() {
        CompanyAccessGuard guard = mock(CompanyAccessGuard.class);
        LookupCodeRepository codes = mock(LookupCodeRepository.class);
        EngineRequestService requests = mock(EngineRequestService.class);
        WeInvoiceDb db = mock(WeInvoiceDb.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(null));
        when(db.tx()).thenReturn(tx);
        when(guard.ownerId()).thenReturn("B");
        when(guard.userId()).thenReturn("u1");
        when(guard.requireInvoice(5L)).thenReturn(new InvoiceRef(5L, "1201496252", "mua", "0101360697", 1, "C26TAB", 9L));
        when(requests.queue(eq("1201496252"), eq(EngineRequestService.FETCH_PDF), eq("MANUAL"), any())).thenReturn(88L);
        InvoiceActionService service = new InvoiceActionService(guard, mock(InvoiceQueryRepository.class), codes,
                mock(TagRepository.class), requests, db);

        RequestAccepted r = service.setLookupCode("5", new LookupCodeRequest("https://tracuu.ehoadon.vn/MTC9X8Y7Z", null));

        assertThat(r.requestId()).isEqualTo("88");
        verify(codes).upsert("B", 5L, "1201496252", "MTC9X8Y7Z", null, "https://tracuu.ehoadon.vn/MTC9X8Y7Z", "u1");
        verify(requests).queue("1201496252", EngineRequestService.FETCH_PDF, "MANUAL", Map.of(
                "invoice_ids", List.of(5L),
                "lookup_code", "MTC9X8Y7Z",
                "lookup_url", "https://tracuu.ehoadon.vn/MTC9X8Y7Z"));
    }
}
