package com.kosign.wecms.domain.payment.dispatch.recipient;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DepositorInquiryService} (C1 orchestrator) — verifies the
 * C001/U002 audit lifecycle, the firm_seq_no allocation hand-off, and the
 * Optional&lt;String&gt; degrade-to-empty failure contract. All collaborators
 * stubbed → no DB, no gateway.
 */
@ExtendWith(MockitoExtension.class)
class DepositorInquiryServiceTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String SEQ = "4242";

    @Mock LegacyFunctionRepository legacyFunctionRepository;
    @Mock FirmTranInquiryAuditService auditService;
    @Mock RecipientLookupService recipientLookupService;
    @Mock com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService opsAlertService;
    @InjectMocks DepositorInquiryService service;

    private static RecipientLookupCommand cmd() {
        return new RecipientLookupCommand(
                "ORG000001", "970424", "ORG000001", "970436", "A", "12345678901111", "55555555555555555555");
    }

    @Test
    void success_records_request_then_success_and_returns_trimmed_owner() {
        when(legacyFunctionRepository.nextTbFirmTranSeq()).thenReturn(SEQ);
        when(recipientLookupService.lookup(any()))
                .thenReturn(new RecipientLookupResult("  NGUYEN VAN A  ", "NAPAS"));

        Optional<String> result = service.inquire(BIZ_KEY, cmd());

        assertThat(result).contains("NGUYEN VAN A");
        verify(auditService).recordRequest(anyString(), eq(BIZ_KEY), eq(SEQ), anyString(), anyString(), any());
        verify(auditService).recordResult(anyString(), eq(BIZ_KEY), eq(SEQ),
                eq("0"), eq("0000"), any(), eq("  NGUYEN VAN A  "));
    }

    @Test
    void blank_owner_on_rc_0000_records_success_but_returns_empty() {
        when(legacyFunctionRepository.nextTbFirmTranSeq()).thenReturn(SEQ);
        when(recipientLookupService.lookup(any()))
                .thenReturn(new RecipientLookupResult("", "NAPAS"));

        Optional<String> result = service.inquire(BIZ_KEY, cmd());

        assertThat(result).isEmpty();
        verify(auditService).recordResult(anyString(), eq(BIZ_KEY), eq(SEQ),
                eq("0"), eq("0000"), any(), eq(""));
    }

    @Test
    void gateway_business_error_records_fail_with_gateway_rc_and_returns_empty() {
        when(legacyFunctionRepository.nextTbFirmTranSeq()).thenReturn(SEQ);
        when(recipientLookupService.lookup(any()))
                .thenThrow(new BusinessException("WBE_EFS_GATEWAY_ERROR", "G001"));

        Optional<String> result = service.inquire(BIZ_KEY, cmd());

        assertThat(result).isEmpty();
        verify(auditService).recordResult(anyString(), eq(BIZ_KEY), eq(SEQ),
                eq("3"), eq("G001"), eq("WBE_EFS_GATEWAY_ERROR"), isNull());
    }

    @Test
    void infrastructure_error_records_fail_and_returns_empty() {
        when(legacyFunctionRepository.nextTbFirmTranSeq()).thenReturn(SEQ);
        when(recipientLookupService.lookup(any()))
                .thenThrow(new IllegalStateException("No TB_VAN_API_INFO.API_KEY provisioned ... B2"));

        Optional<String> result = service.inquire(BIZ_KEY, cmd());

        assertThat(result).isEmpty();
        verify(auditService).recordResult(anyString(), eq(BIZ_KEY), eq(SEQ),
                eq("3"), eq("G999"), any(), isNull());
    }

    @Test
    void pre_call_audit_failure_skips_gateway_and_returns_empty() {
        when(legacyFunctionRepository.nextTbFirmTranSeq()).thenReturn(SEQ);
        Mockito.doThrow(new RuntimeException("db down"))
                .when(auditService)
                .recordRequest(anyString(), anyString(), anyString(), anyString(), anyString(), any());

        Optional<String> result = service.inquire(BIZ_KEY, cmd());

        assertThat(result).isEmpty();
        verify(recipientLookupService, never()).lookup(any());
        verify(auditService, never())
                .recordResult(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
    }
}
