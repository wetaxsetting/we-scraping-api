package com.kosign.wecms.domain.weinvoice.common;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository.CompanyRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Ownership scope: the working MST must be an active connection of the signed-in USER, not of the
 * WeCMS tenant (UI Spec 02: one account manages several MSTs, possibly of several companies).
 */
class CompanyAccessGuardTest {

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String USER = "the.lai@webcashglobal.com";
    private static final String COLLEAGUE = "colleague@webcashglobal.com";

    private final CompanyConnectionRepository companies = mock(CompanyConnectionRepository.class);
    private final CompanyAccessGuard guard = new CompanyAccessGuard(mock(WeInvoiceDb.class), companies);

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private static CompanyRow row(String owner, String mst, boolean deleted) {
        return new CompanyRow(owner, mst, "Webcash", null, mst, true, "CONNECTED", null, null, false, deleted, null, null);
    }

    @Test
    void malformedTaxIdIsWbe3001WithoutTouchingTheDb() {
        TenantContext.set(USER, BIZ, LangCd.VI);
        assertThatThrownBy(() -> guard.requireCompany("12345"))
                .isInstanceOf(BusinessException.class).hasMessage("WBE3001");
        verifyNoInteractions(companies);
    }

    @Test
    void foreignOrDeletedTaxIdIsWbe3002() {
        TenantContext.set(USER, BIZ, LangCd.VI);
        when(companies.find(USER, "0101243150")).thenReturn(Optional.empty());
        when(companies.find(USER, "1201496252")).thenReturn(Optional.of(row(USER, "1201496252", true)));
        assertThatThrownBy(() -> guard.requireCompany("0101243150")).hasMessage("WBE3002");
        assertThatThrownBy(() -> guard.requireCompany("1201496252")).hasMessage("WBE3002");
    }

    @Test
    void activeConnectionIsReturned() {
        TenantContext.set(USER, BIZ, LangCd.VI);
        when(companies.find(USER, "1201496252")).thenReturn(Optional.of(row(USER, "1201496252", false)));
        assertThat(guard.requireCompany(" 1201496252 ").mst()).isEqualTo("1201496252");
    }

    @Test
    void branchTaxIdFormatIsAccepted() {
        TenantContext.set(USER, BIZ, LangCd.VI);
        when(companies.find(USER, "0106026495-001")).thenReturn(Optional.of(row(USER, "0106026495-001", false)));
        assertThat(guard.requireCompany("0106026495-001").mst()).isEqualTo("0106026495-001");
    }

    /** Regression (docs/issues/weinvoice-company-ownership-by-user-2026-11-05.md): owner is the user id. */
    @Test
    void ownerIsTheSignedInUserNotTheTenant() {
        TenantContext.set(USER, BIZ, LangCd.VI);
        when(companies.find(anyString(), anyString())).thenReturn(Optional.empty());
        when(companies.find(USER, "1201496252")).thenReturn(Optional.of(row(USER, "1201496252", false)));

        assertThat(guard.ownerId()).isEqualTo(USER);
        assertThat(guard.requireCompany("1201496252").mst()).isEqualTo("1201496252");
        verify(companies, never()).find(BIZ, "1201496252");
        verify(companies, never()).activeMsts(BIZ);
    }

    /** Two users of the same WeCMS company keep separate tax-code lists. */
    @Test
    void colleaguesOfOneCompanyHaveSeparateLists() {
        when(companies.activeMsts(USER)).thenReturn(List.of("1201496252", "0108515678-001"));
        when(companies.activeMsts(COLLEAGUE)).thenReturn(List.of("3600790154"));
        when(companies.find(COLLEAGUE, "1201496252")).thenReturn(Optional.empty());

        TenantContext.set(USER, BIZ, LangCd.VI);
        assertThat(guard.ownedMsts()).containsExactly("1201496252", "0108515678-001");

        TenantContext.set(COLLEAGUE, BIZ, LangCd.VI);
        assertThat(guard.ownedMsts()).containsExactly("3600790154");
        assertThatThrownBy(() -> guard.requireCompany("1201496252")).hasMessage("WBE3002");
    }

    @Test
    void noUserIsAccessDenied() {
        assertThatThrownBy(guard::ownerId).isInstanceOf(AccessDeniedException.class);
    }
}
