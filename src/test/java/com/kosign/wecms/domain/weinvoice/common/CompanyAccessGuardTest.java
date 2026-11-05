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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Tenant scope: the working MST must be an active connection of the JWT tenant. */
class CompanyAccessGuardTest {

    private static final String BIZ = "VN_VNM0004_000001";

    private final CompanyConnectionRepository companies = mock(CompanyConnectionRepository.class);
    private final CompanyAccessGuard guard = new CompanyAccessGuard(mock(WeInvoiceDb.class), companies);

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private static CompanyRow row(String mst, boolean deleted) {
        return new CompanyRow(BIZ, mst, "Webcash", null, mst, true, "CONNECTED", null, null, false, deleted, null, null);
    }

    @Test
    void malformedTaxIdIsWbe3001WithoutTouchingTheDb() {
        TenantContext.set("u1", BIZ, LangCd.VI);
        assertThatThrownBy(() -> guard.requireCompany("12345"))
                .isInstanceOf(BusinessException.class).hasMessage("WBE3001");
        verifyNoInteractions(companies);
    }

    @Test
    void foreignOrDeletedTaxIdIsWbe3002() {
        TenantContext.set("u1", BIZ, LangCd.VI);
        when(companies.find(BIZ, "0101243150")).thenReturn(Optional.empty());
        when(companies.find(BIZ, "1201496252")).thenReturn(Optional.of(row("1201496252", true)));
        assertThatThrownBy(() -> guard.requireCompany("0101243150")).hasMessage("WBE3002");
        assertThatThrownBy(() -> guard.requireCompany("1201496252")).hasMessage("WBE3002");
    }

    @Test
    void activeConnectionIsReturned() {
        TenantContext.set("u1", BIZ, LangCd.VI);
        when(companies.find(BIZ, "1201496252")).thenReturn(Optional.of(row("1201496252", false)));
        assertThat(guard.requireCompany(" 1201496252 ").mst()).isEqualTo("1201496252");
    }

    @Test
    void branchTaxIdFormatIsAccepted() {
        TenantContext.set("u1", BIZ, LangCd.VI);
        when(companies.find(BIZ, "0106026495-001")).thenReturn(Optional.of(row("0106026495-001", false)));
        assertThat(guard.requireCompany("0106026495-001").mst()).isEqualTo("0106026495-001");
    }

    @Test
    void noTenantIsAccessDenied() {
        assertThatThrownBy(guard::bizKey).isInstanceOf(AccessDeniedException.class);
    }
}
