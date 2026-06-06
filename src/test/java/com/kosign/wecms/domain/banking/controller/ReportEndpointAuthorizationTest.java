package com.kosign.wecms.domain.banking.controller;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.service.MenuAuthorizationService;
import com.kosign.wecms.domain.navigation.repository.MenuItemRepository;
import com.kosign.wecms.domain.navigation.repository.ProductMenuRepository;
import com.kosign.wecms.domain.navigation.service.MenuAuthorizationGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * F-3 Phase B-1 — server-side authorization coverage for the Banking Report
 * endpoints.
 *
 * <p>Two independent guarantees, neither needing a live DB or a booted Spring
 * context (matching the repo idiom — {@code UserManagementAccessPolicyTest}
 * unit-tests the SpEL bean directly; controller MockMvc tests use
 * {@code standaloneSetup}, which skips method security):
 *
 * <ol>
 *   <li><b>Wiring</b> — each report endpoint method carries the exact
 *       {@code @PreAuthorize("@menuAuthz.canAccess('<cd>')")} with the correct
 *       permission_cd. Read reflectively off the controller methods, so a
 *       dropped annotation or a wrong cd literal fails the build.</li>
 *   <li><b>Decision matrix</b> — {@link MenuAuthorizationGuard#canAccess(String)}
 *       (the {@code @menuAuthz} bean those annotations invoke) yields the
 *       required allow/deny for each report cd across the F/H × admin/non-admin
 *       matrix. This is the same proven approach as
 *       {@code MenuAuthorizationGuardTest}, narrowed to the four report cds and
 *       the scenarios the Phase B-1 spec enumerates.</li>
 * </ol>
 *
 * Together: the annotation strings are correct (1) and the guard they call
 * decides correctly (2), so the endpoints enforce exactly the product-tree ∩
 * grant rule the navigation renders.
 */
@ExtendWith(MockitoExtension.class)
class ReportEndpointAuthorizationTest {

    private static final String TENANT = "VN_VNM0004_000001";
    private static final String USER   = "arthurmorgan@rdr2.com";

    private static final String DAILY_CASH   = "banking-reports-daily-cash";
    private static final String DAILY_FIN     = "banking-reports-daily-financial";
    private static final String DAILY_FIN_HS  = "banking-reports-daily-financial-hs";
    private static final String CASH_FLOW     = "banking-reports-cash-flow";

    // ================================================================
    //  (1) Wiring — the @PreAuthorize literal on every report endpoint
    // ================================================================

    @Test
    void reportController_endpoints_carryCorrectMenuAuthzCd() {
        assertPreAuthorize(ReportController.class, "dailyCash", DAILY_CASH);
        assertPreAuthorize(ReportController.class, "dailyFinancial", DAILY_FIN);
        // The per-account transactions popup is part of the Daily Financial
        // feature → same cd as its parent ledger.
        assertPreAuthorize(ReportController.class, "dailyFinancialTransactions", DAILY_FIN);
        assertPreAuthorize(ReportController.class, "dailyFinancialHs", DAILY_FIN_HS);
    }

    @Test
    void dailyCashReportController_endpoints_carryCorrectMenuAuthzCd() {
        assertPreAuthorize(DailyCashReportController.class, "search", DAILY_CASH);
        // Export serialises the same report it gates → same cd.
        assertPreAuthorize(DailyCashReportController.class, "export", DAILY_CASH);
    }

    @Test
    void cashFlowReportController_endpoints_carryCorrectMenuAuthzCd() {
        assertPreAuthorize(CashFlowReportController.class, "search", CASH_FLOW);
        assertPreAuthorize(CashFlowReportController.class, "export", CASH_FLOW);
    }

    private static void assertPreAuthorize(Class<?> controller, String methodName, String expectedCd) {
        Method method = findUniqueMethod(controller, methodName);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation)
                .as("%s#%s must be gated by @PreAuthorize", controller.getSimpleName(), methodName)
                .isNotNull();
        assertThat(annotation.value())
                .as("%s#%s must gate on the '%s' permission_cd", controller.getSimpleName(), methodName, expectedCd)
                .isEqualTo("@menuAuthz.canAccess('" + expectedCd + "')");
    }

    private static Method findUniqueMethod(Class<?> controller, String methodName) {
        Method found = null;
        for (Method m : controller.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                if (found != null) {
                    throw new IllegalStateException(
                            "Overloaded method " + controller.getSimpleName() + "#" + methodName
                                    + " — test assumes a single declaration");
                }
                found = m;
            }
        }
        if (found == null) {
            throw new IllegalStateException(
                    "No method " + controller.getSimpleName() + "#" + methodName);
        }
        return found;
    }

    // ================================================================
    //  (2) Decision matrix — @menuAuthz.canAccess(cd) for report cds
    // ================================================================

    @Mock private MenuItemRepository menuItemRepo;
    @Mock private ProductMenuRepository productMenuRepo;
    @Mock private MenuAuthorizationService menuAuthService;

    @InjectMocks private MenuAuthorizationGuard guard;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    private void session(String prdtType, String authGrCd) {
        TenantContext.set(USER, USER, TENANT, LangCd.EN, authGrCd, prdtType);
    }

    /** All four report cds are product-tree leaves (in MENU_ITEM). */
    private void stubLeaf(String cd) {
        when(menuItemRepo.existsByPermissionCd(cd)).thenReturn(true);
    }

    // ---- Daily Financial (F/M standard) --------------------------------

    @Test
    void fUser_withGrant_canCallStandardDailyFinancial() {
        session("F", TenantContext.AUTH_GR_USER);
        stubLeaf(DAILY_FIN);
        when(productMenuRepo.existsInProductTree("F", DAILY_FIN)).thenReturn(true);
        when(menuAuthService.isGranted(TENANT, USER, DAILY_FIN)).thenReturn(true);

        assertThat(guard.canAccess(DAILY_FIN)).isTrue();
    }

    @Test
    void hUser_cannotCallStandardDailyFinancial() {
        // The standard cd is not in H's product tree (H carries the -hs split),
        // so the product-tree gate denies before any grant lookup.
        session("H", TenantContext.AUTH_GR_USER);
        stubLeaf(DAILY_FIN);
        when(productMenuRepo.existsInProductTree("H", DAILY_FIN)).thenReturn(false);

        assertThat(guard.canAccess(DAILY_FIN)).isFalse();
    }

    @Test
    void hAdmin_cannotCallStandardDailyFinancial() {
        session("H", TenantContext.AUTH_GR_ADMIN);
        stubLeaf(DAILY_FIN);
        when(productMenuRepo.existsInProductTree("H", DAILY_FIN)).thenReturn(false);

        // Even an admin is bound by the product-tree gate.
        assertThat(guard.canAccess(DAILY_FIN)).isFalse();
    }

    // ---- Daily Financial HS (H split) ----------------------------------

    @Test
    void hUser_withHsGrant_canCallDailyFinancialHs() {
        session("H", TenantContext.AUTH_GR_USER);
        stubLeaf(DAILY_FIN_HS);
        when(productMenuRepo.existsInProductTree("H", DAILY_FIN_HS)).thenReturn(true);
        when(menuAuthService.isGranted(TENANT, USER, DAILY_FIN_HS)).thenReturn(true);

        assertThat(guard.canAccess(DAILY_FIN_HS)).isTrue();
    }

    @Test
    void hUser_withoutHsGrant_cannotCallDailyFinancialHs() {
        session("H", TenantContext.AUTH_GR_USER);
        stubLeaf(DAILY_FIN_HS);
        when(productMenuRepo.existsInProductTree("H", DAILY_FIN_HS)).thenReturn(true);
        when(menuAuthService.isGranted(TENANT, USER, DAILY_FIN_HS)).thenReturn(false);

        assertThat(guard.canAccess(DAILY_FIN_HS)).isFalse();
    }

    @Test
    void hAdmin_canCallDailyFinancialHs() {
        // Spec: an H admin CAN call Daily Financial HS (it is in H's tree;
        // admin skips the per-user grant).
        session("H", TenantContext.AUTH_GR_ADMIN);
        stubLeaf(DAILY_FIN_HS);
        when(productMenuRepo.existsInProductTree("H", DAILY_FIN_HS)).thenReturn(true);

        assertThat(guard.canAccess(DAILY_FIN_HS)).isTrue();
    }

    @Test
    void fAdmin_cannotCallDailyFinancialHs() {
        // The -hs split is not in F's product tree.
        session("F", TenantContext.AUTH_GR_ADMIN);
        stubLeaf(DAILY_FIN_HS);
        when(productMenuRepo.existsInProductTree("F", DAILY_FIN_HS)).thenReturn(false);

        assertThat(guard.canAccess(DAILY_FIN_HS)).isFalse();
    }

    // ---- Daily Cash (F/M only) -----------------------------------------

    @Test
    void fAdmin_canCallDailyCash() {
        session("F", TenantContext.AUTH_GR_ADMIN);
        stubLeaf(DAILY_CASH);
        when(productMenuRepo.existsInProductTree("F", DAILY_CASH)).thenReturn(true);

        assertThat(guard.canAccess(DAILY_CASH)).isTrue();
    }

    @Test
    void hUser_cannotCallDailyCash() {
        session("H", TenantContext.AUTH_GR_USER);
        stubLeaf(DAILY_CASH);
        when(productMenuRepo.existsInProductTree("H", DAILY_CASH)).thenReturn(false);

        assertThat(guard.canAccess(DAILY_CASH)).isFalse();
    }

    @Test
    void hAdmin_cannotCallDailyCash() {
        // Spec: an H admin CANNOT call Daily Cash (not in H's tree).
        session("H", TenantContext.AUTH_GR_ADMIN);
        stubLeaf(DAILY_CASH);
        when(productMenuRepo.existsInProductTree("H", DAILY_CASH)).thenReturn(false);

        assertThat(guard.canAccess(DAILY_CASH)).isFalse();
    }

    // ---- Cash Flow (F/M only) ------------------------------------------

    @Test
    void fAdmin_canCallCashFlow() {
        session("F", TenantContext.AUTH_GR_ADMIN);
        stubLeaf(CASH_FLOW);
        when(productMenuRepo.existsInProductTree("F", CASH_FLOW)).thenReturn(true);

        assertThat(guard.canAccess(CASH_FLOW)).isTrue();
    }

    @Test
    void hUser_cannotCallCashFlow() {
        session("H", TenantContext.AUTH_GR_USER);
        stubLeaf(CASH_FLOW);
        when(productMenuRepo.existsInProductTree("H", CASH_FLOW)).thenReturn(false);

        assertThat(guard.canAccess(CASH_FLOW)).isFalse();
    }

    @Test
    void hAdmin_cannotCallCashFlow() {
        // Spec: an H admin CANNOT call Cash Flow (not in H's tree).
        session("H", TenantContext.AUTH_GR_ADMIN);
        stubLeaf(CASH_FLOW);
        when(productMenuRepo.existsInProductTree("H", CASH_FLOW)).thenReturn(false);

        assertThat(guard.canAccess(CASH_FLOW)).isFalse();
    }
}
