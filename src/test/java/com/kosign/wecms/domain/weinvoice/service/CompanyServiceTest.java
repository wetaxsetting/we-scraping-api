package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.weinvoice.WeInvoiceTestProps;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceSecretCipher;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import com.kosign.wecms.domain.weinvoice.dto.CompanyCreateRequest;
import com.kosign.wecms.domain.weinvoice.dto.CompanyPasswordRequest;
import com.kosign.wecms.domain.weinvoice.dto.RequestAccepted;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository.CompanyRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Screen 06: quota, duplicates, password stored encrypted and never echoed, login test queued. */
class CompanyServiceTest {

    private static final String BIZ = "VN_VNM0004_000001";

    private final CompanyConnectionRepository companies = mock(CompanyConnectionRepository.class);
    private final CompanyAccessGuard guard = mock(CompanyAccessGuard.class);
    private final WeInvoiceSecretCipher cipher = mock(WeInvoiceSecretCipher.class);
    private final EngineRequestService requests = mock(EngineRequestService.class);
    private final WeInvoiceDb db = mock(WeInvoiceDb.class);
    private CompanyService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(null));
        when(db.tx()).thenReturn(tx);
        when(guard.bizKey()).thenReturn(BIZ);
        when(guard.userId()).thenReturn("admin1");
        when(cipher.encrypt(anyString())).thenAnswer(inv -> "v1:enc(" + inv.getArgument(0).hashCode() + ")");
        service = new CompanyService(companies, guard, cipher, requests, db, WeInvoiceTestProps.enabled("", 2));
    }

    private static CompanyRow row(String mst, boolean deleted) {
        return new CompanyRow(BIZ, mst, null, null, mst, !deleted, "CONNECTED", null, null, false, deleted, null, null);
    }

    @Test
    void createStoresEncryptedPasswordAsPendingAndQueuesLoginTest() {
        when(companies.find(BIZ, "1201496252")).thenReturn(Optional.empty());
        when(companies.activeMsts(BIZ)).thenReturn(List.of());
        when(requests.queue("1201496252", EngineRequestService.LOGIN_TEST, "MANUAL", Map.of())).thenReturn(41L);

        RequestAccepted r = service.create(new CompanyCreateRequest("1201496252", "1201496252-U002", "Secret#1", "HQ"));

        assertThat(r.requestId()).isEqualTo("41");
        assertThat(r.subject()).isEqualTo("1201496252");
        verify(companies).upsert(eq(BIZ), eq("1201496252"), eq("HQ"), eq("1201496252-U002"),
                eq("v1:enc(" + "Secret#1".hashCode() + ")"), eq("PENDING"), eq("admin1"));
    }

    @Test
    void blankUsernameDefaultsToTheTaxId() {
        when(companies.find(BIZ, "1201496252")).thenReturn(Optional.empty());
        when(companies.activeMsts(BIZ)).thenReturn(List.of());
        service.create(new CompanyCreateRequest("1201496252", " ", "pw", null));
        verify(companies).upsert(eq(BIZ), eq("1201496252"), isNull(), eq("1201496252"), anyString(), eq("PENDING"), eq("admin1"));
    }

    @Test
    void activeDuplicateIsWbe3003() {
        when(companies.find(BIZ, "1201496252")).thenReturn(Optional.of(row("1201496252", false)));
        assertThatThrownBy(() -> service.create(new CompanyCreateRequest("1201496252", null, "pw", null)))
                .isInstanceOf(BusinessException.class).hasMessage("WBE3003");
        verify(companies, never()).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void softDeletedConnectionCanBeAddedAgain() {
        when(companies.find(BIZ, "1201496252")).thenReturn(Optional.of(row("1201496252", true)));
        when(companies.activeMsts(BIZ)).thenReturn(List.of("0101243150"));
        service.create(new CompanyCreateRequest("1201496252", null, "pw", null));
        verify(companies).upsert(eq(BIZ), eq("1201496252"), isNull(), eq("1201496252"), anyString(), eq("PENDING"), eq("admin1"));
    }

    @Test
    void quotaIsWbe3004() {
        when(companies.find(BIZ, "1201496252")).thenReturn(Optional.empty());
        when(companies.activeMsts(BIZ)).thenReturn(List.of("0101243150", "0105987432"));
        assertThatThrownBy(() -> service.create(new CompanyCreateRequest("1201496252", null, "pw", null)))
                .isInstanceOf(BusinessException.class).hasMessage("WBE3004");
    }

    @Test
    void invalidTaxIdIsWbe3001() {
        assertThatThrownBy(() -> service.create(new CompanyCreateRequest("ABC", null, "pw", null)))
                .isInstanceOf(BusinessException.class).hasMessage("WBE3001");
    }

    @Test
    void passwordChangeReEncryptsAndQueuesLoginTest() {
        when(guard.requireCompany("1201496252")).thenReturn(row("1201496252", false));
        when(requests.queue("1201496252", EngineRequestService.LOGIN_TEST, "MANUAL", Map.of())).thenReturn(42L);
        RequestAccepted r = service.changePassword("1201496252", new CompanyPasswordRequest("New#2", null));
        assertThat(r.requestId()).isEqualTo("42");
        verify(companies).updatePassword(BIZ, "1201496252", null, "v1:enc(" + "New#2".hashCode() + ")", "admin1");
    }

    @Test
    void requestRecordsNeverPrintThePassword() {
        assertThat(new CompanyCreateRequest("1201496252", null, "Secret#1", null).toString()).doesNotContain("Secret#1");
        assertThat(new CompanyPasswordRequest("Secret#1", null).toString()).doesNotContain("Secret#1");
    }

    @Test
    void responseItemHasNoPasswordField() {
        assertThat(CompanyService.toItem(row("1201496252", false)).toString()).doesNotContain("v1:");
        assertThat(java.util.Arrays.stream(com.kosign.wecms.domain.weinvoice.dto.CompanyItem.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .doesNotContain("password", "gdtPwEnc");
    }
}
