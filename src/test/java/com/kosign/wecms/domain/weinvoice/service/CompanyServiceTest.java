package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.weinvoice.WeInvoiceTestProps;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceSecretCipher;
import com.kosign.wecms.domain.weinvoice.dto.CompanyCreateRequest;
import com.kosign.wecms.domain.weinvoice.dto.CompanyItem;
import com.kosign.wecms.domain.weinvoice.dto.CompanyPasswordRequest;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository.CompanyRow;
import com.kosign.wecms.domain.weinvoice.service.ScrapingClient.LoginResult;
import com.kosign.wecms.domain.weinvoice.service.ScrapingClient.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Screen 06: the GDT account is verified through the scraping engine BEFORE anything is stored
 * (docs/issues/weinvoice-gdt-login-test-synchronous-2026-11-06.md); quota, duplicates, password
 * stored encrypted and never echoed.
 */
class CompanyServiceTest {

    private static final String OWNER = "lamine@gmail.com";
    private static final LoginResult OK = new LoginResult(Outcome.OK, "");

    private final CompanyConnectionRepository companies = mock(CompanyConnectionRepository.class);
    private final CompanyAccessGuard guard = mock(CompanyAccessGuard.class);
    private final WeInvoiceSecretCipher cipher = mock(WeInvoiceSecretCipher.class);
    private final ScrapingClient scraping = mock(ScrapingClient.class);
    private CompanyService service;

    @BeforeEach
    void setUp() {
        when(guard.ownerId()).thenReturn(OWNER);
        when(guard.userId()).thenReturn(OWNER);
        when(cipher.encrypt(anyString())).thenAnswer(inv -> "v1:enc(" + inv.getArgument(0).hashCode() + ")");
        service = new CompanyService(companies, guard, cipher, scraping, WeInvoiceTestProps.enabled("", 2));
    }

    private static CompanyRow row(String mst, String gdtUser, boolean deleted) {
        return new CompanyRow(OWNER, mst, null, null, gdtUser, !deleted, "CONNECTED", null, null, false, deleted, null, null);
    }

    private void newMst(String mst) {
        when(companies.find(OWNER, mst)).thenReturn(Optional.empty(), Optional.of(row(mst, mst, false)));
        when(companies.activeMsts(OWNER)).thenReturn(List.of());
    }

    @Test
    void createLogsInFirstThenStoresEncryptedPasswordAsConnected() {
        newMst("1201496252");
        when(scraping.loginTest("1201496252", "1201496252-U002", "Secret#1")).thenReturn(OK);

        CompanyItem item = service.create(new CompanyCreateRequest("1201496252", "1201496252-U002", "Secret#1", "HQ"));

        assertThat(item.taxId()).isEqualTo("1201496252");
        assertThat(item.status()).isEqualTo("connected");
        verify(companies).upsert(eq(OWNER), eq("1201496252"), eq("HQ"), eq("1201496252-U002"),
                eq("v1:enc(" + "Secret#1".hashCode() + ")"), eq("CONNECTED"), eq(OWNER));
    }

    @Test
    void blankUsernameLogsInWithTheTaxId() {
        newMst("1201496252");
        when(scraping.loginTest("1201496252", "1201496252", "pw")).thenReturn(OK);
        service.create(new CompanyCreateRequest("1201496252", " ", "pw", null));
        verify(companies).upsert(eq(OWNER), eq("1201496252"), isNull(), eq("1201496252"), anyString(), eq("CONNECTED"), eq(OWNER));
    }

    @Test
    void rejectedAccountIsWbe3027WithTheGdtMessageAndNothingIsSaved() {
        newMst("1201496252");
        when(scraping.loginTest(any(), any(), any()))
                .thenReturn(new LoginResult(Outcome.REJECTED, "Tên đăng nhập hoặc mật khẩu không đúng"));

        assertThatThrownBy(() -> service.create(new CompanyCreateRequest("1201496252", null, "wrong", null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("WBE3027");
                    assertThat(e.getArgs()).containsExactly("1201496252", "Tên đăng nhập hoặc mật khẩu không đúng");
                });
        verify(companies, never()).upsert(any(), any(), any(), any(), any(), any(), any());
        verify(cipher, never()).encrypt(anyString());
    }

    @Test
    void uncheckableAccountIsWbe3028AndNothingIsSaved() {
        newMst("1201496252");
        when(scraping.loginTest(any(), any(), any())).thenReturn(new LoginResult(Outcome.UNAVAILABLE, "HttpTimeoutException"));
        assertThatThrownBy(() -> service.create(new CompanyCreateRequest("1201496252", null, "pw", null)))
                .isInstanceOf(BusinessException.class).hasMessage("WBE3028");
        verify(companies, never()).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void activeDuplicateIsWbe3003WithoutCallingGdt() {
        when(companies.find(OWNER, "1201496252")).thenReturn(Optional.of(row("1201496252", "1201496252", false)));
        assertThatThrownBy(() -> service.create(new CompanyCreateRequest("1201496252", null, "pw", null)))
                .isInstanceOf(BusinessException.class).hasMessage("WBE3003");
        verifyNoInteractions(scraping);
    }

    @Test
    void softDeletedConnectionCanBeAddedAgain() {
        when(companies.find(OWNER, "1201496252"))
                .thenReturn(Optional.of(row("1201496252", "1201496252", true)), Optional.of(row("1201496252", "1201496252", false)));
        when(companies.activeMsts(OWNER)).thenReturn(List.of("0101243150"));
        when(scraping.loginTest(any(), any(), any())).thenReturn(OK);
        service.create(new CompanyCreateRequest("1201496252", null, "pw", null));
        verify(companies).upsert(eq(OWNER), eq("1201496252"), isNull(), eq("1201496252"), anyString(), eq("CONNECTED"), eq(OWNER));
    }

    @Test
    void quotaIsWbe3004WithoutCallingGdt() {
        when(companies.find(OWNER, "1201496252")).thenReturn(Optional.empty());
        when(companies.activeMsts(OWNER)).thenReturn(List.of("0101243150", "0105987432"));
        assertThatThrownBy(() -> service.create(new CompanyCreateRequest("1201496252", null, "pw", null)))
                .isInstanceOf(BusinessException.class).hasMessage("WBE3004");
        verifyNoInteractions(scraping);
    }

    @Test
    void invalidTaxIdIsWbe3001() {
        assertThatThrownBy(() -> service.create(new CompanyCreateRequest("ABC", null, "pw", null)))
                .isInstanceOf(BusinessException.class).hasMessage("WBE3001");
        verifyNoInteractions(scraping);
    }

    @Test
    void passwordChangeLogsInWithTheStoredUsernameThenSaves() {
        when(guard.requireCompany("1201496252")).thenReturn(row("1201496252", "1201496252-U002", false));
        when(companies.find(OWNER, "1201496252")).thenReturn(Optional.of(row("1201496252", "1201496252-U002", false)));
        when(scraping.loginTest("1201496252", "1201496252-U002", "New#2")).thenReturn(OK);

        CompanyItem item = service.changePassword("1201496252", new CompanyPasswordRequest("New#2", null));

        assertThat(item.status()).isEqualTo("connected");
        verify(companies).updatePassword(OWNER, "1201496252", null, "v1:enc(" + "New#2".hashCode() + ")", OWNER);
    }

    @Test
    void rejectedPasswordChangeKeepsTheOldPassword() {
        when(guard.requireCompany("1201496252")).thenReturn(row("1201496252", "1201496252", false));
        when(scraping.loginTest(any(), any(), any())).thenReturn(new LoginResult(Outcome.REJECTED, "sai mật khẩu"));
        assertThatThrownBy(() -> service.changePassword("1201496252", new CompanyPasswordRequest("bad", null)))
                .isInstanceOf(BusinessException.class).hasMessage("WBE3027");
        verify(companies, never()).updatePassword(any(), any(), any(), any(), any());
    }

    @Test
    void requestRecordsNeverPrintThePassword() {
        assertThat(new CompanyCreateRequest("1201496252", null, "Secret#1", null).toString()).doesNotContain("Secret#1");
        assertThat(new CompanyPasswordRequest("Secret#1", null).toString()).doesNotContain("Secret#1");
    }

    @Test
    void responseItemHasNoPasswordField() {
        assertThat(CompanyService.toItem(row("1201496252", "1201496252", false)).toString()).doesNotContain("v1:");
        assertThat(java.util.Arrays.stream(CompanyItem.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .doesNotContain("password", "gdtPwEnc");
    }
}
