package com.kosign.wecms.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-3 Phase F — "deny-or-declared" coverage lock-in for server-side menu
 * authorization.
 *
 * <p>Scans every {@code @RestController} under {@code com.kosign.wecms} (via
 * {@link ClassPathScanningCandidateComponentProvider} — no Spring context, no
 * DB boot, so it runs anywhere) and asserts that <b>every request-mapped
 * handler method</b> is one of:
 * <ol>
 *   <li>gated by {@code @menuAuthz.canAccess(...)} / {@code canAccessAny(...)} /
 *       {@code canAccessAccountInquiry(...)} / {@code canAccessEInvoice(...)} —
 *       at the method level <i>or</i> inherited from a class-level
 *       {@code @PreAuthorize}; or</li>
 *   <li>declared in {@link #ALLOWLISTED_CONTROLLERS} — a documented exception
 *       (auth/session, the navigation resolver itself, the user-preference
 *       screen-options store, cross-screen reference/utility endpoints, and
 *       refactor-new features that have no menu {@code permission_cd} yet).</li>
 * </ol>
 *
 * <p>Adding a new business endpoint without a {@code @menuAuthz} gate fails this
 * test — the omission cannot ship silently. Adding a controller to the
 * allow-list is a deliberate, reviewable edit to this file (with the reason
 * recorded below).
 *
 * <p><b>Why an allow-list and not blanket coverage:</b> a handful of endpoints
 * legitimately carry no single business-screen {@code permission_cd}:
 * authentication/session, the navigation resolver (it <i>computes</i> the menu),
 * the generic View Setting preference store (PO note Q-B — authenticated-only,
 * no business data), cross-screen reference data + lookups (countries,
 * notifications, blocked-chars, the NXiSAS scraping utility), and refactor-new
 * features not yet in the menu universe (bank-credentials, smart-organizer,
 * sync-history, environment-settings — route-migration-audit X-1/X-2 + open
 * items). Each is enumerated explicitly so the gap is visible, not implicit.
 */
class EndpointAuthorizationCoverageTest {

    private static final String BASE_PACKAGE = "com.kosign.wecms";

    /** The {@code @menuAuthz} SpEL bean name — any {@code @PreAuthorize} whose
     *  value references it counts as a menu gate. */
    private static final String MENU_AUTHZ = "@menuAuthz";

    @SuppressWarnings("unchecked")
    private static final List<Class<? extends Annotation>> MAPPING_ANNOTATIONS = List.of(
            RequestMapping.class, GetMapping.class, PostMapping.class,
            PutMapping.class, DeleteMapping.class, PatchMapping.class);

    /**
     * Controllers whose endpoints are intentionally NOT menu-gated. Keep this
     * list small and justified — every entry is a documented exception, not a
     * "TODO: gate later" dumping ground.
     */
    private static final Set<String> ALLOWLISTED_CONTROLLERS = Set.of(
            // --- auth / session (public or authenticated-only; no business screen) ---
            "AuthController",            // login / logout / me / change-password
            "UserController",            // user lookup helper (auth-adjacent; consumed by approval-line etc.)
            // --- navigation resolver (it IS the menu; cannot gate on a menu cd) ---
            "NavigationController",
            // --- View Setting preference store (PO note Q-B — authenticated-only, no business data) ---
            "ScreenOptionController",
            // --- cross-screen reference data / utilities (gating to one cd would break reuse) ---
            "CountryController",         // reference data
            "NotificationController",    // cross-cutting notifications
            "BlockedCharsRuleController",// payment-compose validation helper
            "BankScrapingController",    // NXiSAS scraping utility — consumed by multiple flows (flagged for PO)
            // --- refactor-new features with no menu permission_cd yet (route-migration-audit X-1/X-2 + open items) ---
            "BankCredentialController",      // /banking/credentials — new feature (X-1)
            "SmartOrganizerController",      // /banking/smart-organizer — new feature (X-2)
            "OrganizerRuleController",       // /banking/smart-organizer/rules — new feature (X-2)
            "SyncHistoryController",         // /banking/sync-history — new feature, no cd
            "EnvironmentSettingsController"  // /settings/environment-settings — no cd in the entitlement universe
    );

    @Test
    void everyProtectedEndpointIsMenuGatedOrAllowlisted() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> controllers = new ArrayList<>();
        List<String> violations = new ArrayList<>();

        for (var bd : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> controller = Class.forName(bd.getBeanClassName());
            controllers.add(controller.getSimpleName());

            boolean allowlisted = ALLOWLISTED_CONTROLLERS.contains(controller.getSimpleName());
            boolean classGated = referencesMenuAuthz(controller.getAnnotation(PreAuthorize.class));

            for (Method m : controller.getDeclaredMethods()) {
                if (m.isSynthetic() || !isRequestMapped(m)) {
                    continue;
                }
                boolean methodGated = referencesMenuAuthz(m.getAnnotation(PreAuthorize.class));
                if (allowlisted || classGated || methodGated) {
                    continue;
                }
                violations.add(controller.getSimpleName() + "#" + m.getName());
            }
        }

        // Sanity: the scan actually found the controller layer (guards against a
        // mis-scoped base package silently passing with zero endpoints).
        assertThat(controllers)
                .as("classpath scan must discover @RestController beans")
                .isNotEmpty();

        assertThat(violations)
                .as("every protected /api/v1/** handler must carry a @menuAuthz @PreAuthorize "
                        + "(method- or class-level) or be a documented allow-list exception")
                .isEmpty();
    }

    /**
     * Guards against allow-list rot: every name in {@link #ALLOWLISTED_CONTROLLERS}
     * must correspond to a real scanned controller. A stale entry (renamed /
     * deleted controller) fails here so the allow-list cannot quietly mask a
     * future business controller that happens to share the dead name.
     */
    @Test
    void allowlistHasNoStaleEntries() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Set<String> scanned = new java.util.HashSet<>();
        for (var bd : scanner.findCandidateComponents(BASE_PACKAGE)) {
            String fqcn = bd.getBeanClassName();
            scanned.add(fqcn.substring(fqcn.lastIndexOf('.') + 1));
        }

        assertThat(scanned)
                .as("every allow-listed controller name must still exist as a @RestController")
                .containsAll(ALLOWLISTED_CONTROLLERS);
    }

    private static boolean isRequestMapped(Method m) {
        for (Class<? extends Annotation> a : MAPPING_ANNOTATIONS) {
            if (m.isAnnotationPresent(a)) {
                return true;
            }
        }
        return false;
    }

    private static boolean referencesMenuAuthz(PreAuthorize pre) {
        return pre != null && pre.value() != null && pre.value().contains(MENU_AUTHZ);
    }
}
