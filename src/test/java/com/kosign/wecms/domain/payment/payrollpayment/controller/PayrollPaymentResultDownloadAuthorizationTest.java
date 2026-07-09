package com.kosign.wecms.domain.payment.payrollpayment.controller;

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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Closure-audit D1 — server-side authorization for the Payroll Payment Result
 * download, mirroring the proven {@code ReportEndpointAuthorizationTest} idiom
 * (no live DB, no booted Spring context — standalone-MockMvc controller tests
 * skip method security, so authz is pinned two ways):
 *
 * <ol>
 *   <li><b>Wiring</b> — the {@code @PreAuthorize} literal on each Result
 *       endpoint is read reflectively. The shared download must gate on
 *       <i>result OR scheduled</i>; list / detail / export keep the
 *       result-only gate.</li>
 *   <li><b>Decision matrix</b> — {@link MenuAuthorizationGuard#canAccess(String)}
 *       (the {@code @menuAuthz} bean the annotations invoke) yields the right
 *       allow/deny per operand, so the {@code or} composition admits a
 *       result-only user, a scheduled-only user, and denies a user with
 *       neither.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayrollPaymentResultDownloadAuthorizationTest {

    private static final String TENANT = "VN_VNM0004_000001";
    private static final String USER   = "lamine@gmail.com";

    private static final String RESULT    = "payment-payroll-result";
    private static final String SCHEDULED  = "payment-payroll-scheduled";

    private static final String DOWNLOAD_EXPR =
            "@menuAuthz.canAccess('" + RESULT + "') or @menuAuthz.canAccess('" + SCHEDULED + "')";

    // ================================================================
    //  (1) Wiring — the @PreAuthorize literal on each Result endpoint
    // ================================================================

    @Test
    void downloadFile_isGatedByResultOrScheduled() {
        Method m = findUniqueMethod(PayrollPaymentController.class, "downloadFile");
        PreAuthorize pre = m.getAnnotation(PreAuthorize.class);
        assertThat(pre)
                .as("downloadFile must carry a method-level @PreAuthorize (not inherit class-level scheduled)")
                .isNotNull();
        assertThat(pre.value())
                .as("shared download must accept EITHER the result or the scheduled permission")
                .isEqualTo(DOWNLOAD_EXPR);
    }

    @Test
    void resultListDetailExport_stayGatedByResultOnly() {
        assertPreAuthorize("listResults",  "@menuAuthz.canAccess('" + RESULT + "')");
        assertPreAuthorize("resultDetail", "@menuAuthz.canAccess('" + RESULT + "')");
        assertPreAuthorize("exportResults", "@menuAuthz.canAccess('" + RESULT + "')");
    }

    private static void assertPreAuthorize(String methodName, String expectedExpr) {
        Method m = findUniqueMethod(PayrollPaymentController.class, methodName);
        PreAuthorize pre = m.getAnnotation(PreAuthorize.class);
        assertThat(pre).as("%s must be gated by @PreAuthorize", methodName).isNotNull();
        assertThat(pre.value()).as("%s permission expression", methodName).isEqualTo(expectedExpr);
    }

    private static Method findUniqueMethod(Class<?> controller, String methodName) {
        Method found = null;
        for (Method m : controller.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                if (found != null) {
                    throw new IllegalStateException("Overloaded " + methodName + " — test assumes one declaration");
                }
                found = m;
            }
        }
        if (found == null) throw new IllegalStateException("No method " + methodName);
        return found;
    }

    // ================================================================
    //  (2) Decision matrix — @menuAuthz.canAccess(cd) per operand
    // ================================================================

    @Mock private MenuItemRepository        menuItemRepo;
    @Mock private ProductMenuRepository     productMenuRepo;
    @Mock private MenuAuthorizationService  menuAuthService;
    @InjectMocks private MenuAuthorizationGuard guard;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    /** F-product session; both payroll cds are leaves present in F's product tree. */
    private void fUserSession() {
        TenantContext.set(USER, USER, TENANT, LangCd.EN, TenantContext.AUTH_GR_USER, "F");
        when(menuItemRepo.existsByPermissionCd(RESULT)).thenReturn(true);
        when(menuItemRepo.existsByPermissionCd(SCHEDULED)).thenReturn(true);
        when(productMenuRepo.existsInProductTree("F", RESULT)).thenReturn(true);
        when(productMenuRepo.existsInProductTree("F", SCHEDULED)).thenReturn(true);
    }

    /** Evaluate the download endpoint's SpEL `result OR scheduled`. */
    private boolean downloadAllowed() {
        return guard.canAccess(RESULT) || guard.canAccess(SCHEDULED);
    }

    @Test
    void resultOnlyUser_canDownload() {
        fUserSession();
        when(menuAuthService.isGranted(TENANT, USER, RESULT)).thenReturn(true);
        when(menuAuthService.isGranted(TENANT, USER, SCHEDULED)).thenReturn(false);

        assertThat(guard.canAccess(RESULT)).isTrue();
        assertThat(guard.canAccess(SCHEDULED)).isFalse();
        assertThat(downloadAllowed()).as("result-only user must be allowed to download").isTrue();
    }

    @Test
    void scheduledOnlyUser_canDownload() {
        fUserSession();
        when(menuAuthService.isGranted(TENANT, USER, RESULT)).thenReturn(false);
        when(menuAuthService.isGranted(TENANT, USER, SCHEDULED)).thenReturn(true);

        assertThat(guard.canAccess(RESULT)).isFalse();
        assertThat(guard.canAccess(SCHEDULED)).isTrue();
        assertThat(downloadAllowed()).as("scheduled-only user must be allowed to download (shared endpoint)").isTrue();
    }

    @Test
    void userWithNeitherPermission_isDenied() {
        fUserSession();
        when(menuAuthService.isGranted(TENANT, USER, RESULT)).thenReturn(false);
        when(menuAuthService.isGranted(TENANT, USER, SCHEDULED)).thenReturn(false);

        assertThat(downloadAllowed()).as("user with neither permission must be denied (403)").isFalse();
    }
}
