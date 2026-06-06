package com.kosign.wecms.domain.auth.service;

import com.kosign.wecms.domain.setting.users.entity.UserMenuAuthInfm;
import com.kosign.wecms.domain.setting.users.repository.UserMenuAuthInfmRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link MenuAuthorizationService} (MENU-1) — the
 * {@code /me} allowed-menu resolver reads {@code USER_MENU_AUTH_INFM}
 * and returns only the {@code USE_YN='Y'} leaf slugs.
 */
@ExtendWith(MockitoExtension.class)
class MenuAuthorizationServiceTest {

    private static final String TENANT = "VN_VNM0004_000001";
    private static final String USER   = "arthurmorgan@rdr2.com";

    @Mock private UserMenuAuthInfmRepository menuAuthRepo;

    @InjectMocks private MenuAuthorizationService service;

    private static UserMenuAuthInfm row(String cd, String useYn) {
        UserMenuAuthInfm r = new UserMenuAuthInfm();
        r.setBizKey(TENANT);
        r.setUserId(USER);
        r.setMenuAuthCd(cd);
        r.setUseYn(useYn);
        return r;
    }

    @Test
    void grantedMenuCds_returnsOnlyUseYnYRows() {
        when(menuAuthRepo.findAllByBizKeyAndUserId(TENANT, USER)).thenReturn(List.of(
                row("addon-einvoices-sales", "Y"),
                row("addon-einvoices-expenses", "Y"),
                row("addon-operation-status-inquiry", "Y"),
                row("banking-vnd-account-inquiry", "N"),   // revoked → excluded
                row("payment-approval-box", "N")));         // revoked → excluded

        List<String> granted = service.grantedMenuCds(TENANT, USER);

        assertThat(granted).containsExactlyInAnyOrder(
                "addon-einvoices-sales",
                "addon-einvoices-expenses",
                "addon-operation-status-inquiry");
    }

    @Test
    void grantedMenuCds_emptyWhenNoGrants() {
        when(menuAuthRepo.findAllByBizKeyAndUserId(TENANT, USER)).thenReturn(List.of());

        assertThat(service.grantedMenuCds(TENANT, USER)).isEmpty();
    }
}
