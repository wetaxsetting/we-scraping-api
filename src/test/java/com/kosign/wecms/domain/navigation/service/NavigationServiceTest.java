package com.kosign.wecms.domain.navigation.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.service.MenuAuthorizationService;
import com.kosign.wecms.domain.navigation.dto.NavMenuNode;
import com.kosign.wecms.domain.navigation.dto.NavigationResponse;
import com.kosign.wecms.domain.navigation.entity.MenuItem;
import com.kosign.wecms.domain.navigation.entity.MenuItemTranslation;
import com.kosign.wecms.domain.navigation.entity.ProductMenu;
import com.kosign.wecms.domain.navigation.repository.MenuItemRepository;
import com.kosign.wecms.domain.navigation.repository.MenuItemTranslationRepository;
import com.kosign.wecms.domain.navigation.repository.ProductMenuRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link NavigationService} — the
 * {@code Visible Menu = Product Tree (by prdt_type) ∩ grants} resolver.
 *
 * <p>Fixture is a focused slice of the real catalogue: Banking ▸ Reports
 * ▸ {Daily Cash, Daily Financial, Daily Financial HS, Cash Flow} and
 * Add-on ▸ E-Invoice ▸ {Sales, Expenses}. F/M carry the full slice (no HS,
 * with the Add-on subtree); H carries Banking ▸ Reports ▸ Daily Financial HS
 * only (no Add-on, no Daily Cash / standard Daily Financial / Cash Flow) —
 * mirroring the export's F/M/H difference and the HS Daily-Financial split.
 */
@ExtendWith(MockitoExtension.class)
class NavigationServiceTest {

    private static final String TENANT = "VN_VNM0004_000001";
    private static final String USER   = "arthurmorgan@rdr2.com";

    @Mock private MenuItemRepository menuItemRepo;
    @Mock private ProductMenuRepository productMenuRepo;
    @Mock private MenuItemTranslationRepository translationRepo;
    @Mock private MenuAuthorizationService menuAuthService;

    @InjectMocks private NavigationService service;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    // ---- fixture --------------------------------------------------------

    private static MenuItem item(String id, String parent, int depth, String cd, String route) {
        MenuItem m = new MenuItem();
        m.setMenuItemId(id);
        m.setParentId(parent);
        m.setMenuDep(depth);
        m.setShowYn("Y");
        m.setPermissionCd(cd);   // null => parent
        m.setRoute(route);
        m.setIcon(null);
        m.setLegacyMnuId(id);
        m.setLegacyWsvcId(cd);
        return m;
    }

    /** All 10 catalogue nodes (product-independent structure). */
    private List<MenuItem> allItems() {
        return List.of(
                item("banking", null, 0, null, null),
                item("banking-reports", "banking", 1, null, null),
                item("banking-report-daily-cash", "banking-reports", 2,
                        "banking-reports-daily-cash", "/banking/reports/daily-cash"),
                item("banking-report-daily-financial", "banking-reports", 2,
                        "banking-reports-daily-financial", "/banking/reports/daily-financial"),
                item("banking-report-daily-financial-haengsung", "banking-reports", 2,
                        "banking-reports-daily-financial-hs", "/banking/reports/daily-financial-hs"),
                item("banking-report-cash-flow", "banking-reports", 2,
                        "banking-reports-cash-flow", "/banking/reports/cash-flow"),
                item("addon-service", null, 0, null, null),
                item("addon-einvoice", "addon-service", 1, null, null),
                item("addon-sales-einvoice", "addon-einvoice", 2,
                        "addon-einvoices-sales", "/addon/sales-einvoices"),
                item("addon-expense-einvoice", "addon-einvoice", 2,
                        "addon-einvoices-expenses", "/addon/expenses-einvoices"));
    }

    private static ProductMenu pm(String pt, String id, int seq) {
        ProductMenu p = new ProductMenu();
        p.setProductType(pt);
        p.setMenuItemId(id);
        p.setDispOrd(seq);
        return p;
    }

    /** F/M membership: full slice, no HS node. */
    private List<ProductMenu> fmMembership(String pt) {
        return List.of(
                pm(pt, "banking", 0),
                pm(pt, "banking-reports", 0),
                pm(pt, "banking-report-daily-cash", 0),
                pm(pt, "banking-report-daily-financial", 1),
                pm(pt, "banking-report-cash-flow", 2),
                pm(pt, "addon-service", 1),
                pm(pt, "addon-einvoice", 0),
                pm(pt, "addon-sales-einvoice", 0),
                pm(pt, "addon-expense-einvoice", 1));
    }

    /** H membership: Banking ▸ Reports ▸ Daily Financial HS only. */
    private List<ProductMenu> hMembership() {
        return List.of(
                pm("H", "banking", 0),
                pm("H", "banking-reports", 0),
                pm("H", "banking-report-daily-financial-haengsung", 0));
    }

    private static MenuItemTranslation tr(String pt, String id, String label) {
        MenuItemTranslation t = new MenuItemTranslation();
        t.setProductType(pt);
        t.setMenuItemId(id);
        t.setLangCd("EN");
        t.setLabel(label);
        return t;
    }

    private List<MenuItemTranslation> enTranslations(String pt, List<ProductMenu> membership) {
        List<MenuItemTranslation> out = new ArrayList<>();
        for (ProductMenu p : membership) {
            out.add(tr(pt, p.getMenuItemId(), "EN " + p.getMenuItemId()));
        }
        return out;
    }

    private void session(String prdtType, String authGrCd) {
        TenantContext.set(USER, USER, TENANT, LangCd.EN, authGrCd, prdtType);
    }

    /** Flatten the resolved tree to the set of permission_cds present. */
    private static List<String> cds(List<NavMenuNode> nodes) {
        List<String> out = new ArrayList<>();
        for (NavMenuNode n : nodes) {
            if (n.permissionCd() != null) out.add(n.permissionCd());
            out.addAll(cds(n.children()));
        }
        return out;
    }

    private static NavMenuNode child(List<NavMenuNode> nodes, String id) {
        return nodes.stream().filter(n -> n.id().equals(id)).findFirst().orElse(null);
    }

    // ---- tests ----------------------------------------------------------

    @Test
    void fAdmin_seesFullProductTree() {
        session("F", TenantContext.AUTH_GR_ADMIN);
        when(menuItemRepo.findAll()).thenReturn(allItems());
        when(productMenuRepo.findAllByProductType("F")).thenReturn(fmMembership("F"));
        when(translationRepo.findAllByProductTypeAndLangCd("F", "EN"))
                .thenReturn(enTranslations("F", fmMembership("F")));

        NavigationResponse res = service.resolve();

        assertThat(res.productType()).isEqualTo("F");
        assertThat(res.menuGroupId()).isEqualTo("WE_CMSVN");
        // 2 top menus: Banking + Add-on (ordered by seq).
        assertThat(res.topMenus()).extracting(NavMenuNode::id)
                .containsExactly("banking", "addon-service");
        // Reports carries all three F reports, in seq order.
        NavMenuNode reports = child(child(res.topMenus(), "banking").children(), "banking-reports");
        assertThat(reports.children()).extracting(NavMenuNode::permissionCd)
                .containsExactly("banking-reports-daily-cash",
                        "banking-reports-daily-financial",
                        "banking-reports-cash-flow");
        // No HS variant in F.
        assertThat(cds(res.topMenus())).doesNotContain("banking-reports-daily-financial-hs");
        // Label resolved from the EN translation.
        assertThat(child(res.topMenus(), "banking").label()).isEqualTo("EN banking");
    }

    @Test
    void mAdmin_sameStructureAsF_lumensGroup() {
        session("M", TenantContext.AUTH_GR_ADMIN);
        when(menuItemRepo.findAll()).thenReturn(allItems());
        when(productMenuRepo.findAllByProductType("M")).thenReturn(fmMembership("M"));
        when(translationRepo.findAllByProductTypeAndLangCd("M", "EN"))
                .thenReturn(enTranslations("M", fmMembership("M")));

        NavigationResponse res = service.resolve();

        assertThat(res.menuGroupId()).isEqualTo("WE_CMSVN_LUMENS");
        assertThat(res.topMenus()).extracting(NavMenuNode::id)
                .containsExactly("banking", "addon-service");
        NavMenuNode reports = child(child(res.topMenus(), "banking").children(), "banking-reports");
        assertThat(reports.children()).hasSize(3);
        assertThat(cds(res.topMenus())).doesNotContain("banking-reports-daily-financial-hs");
    }

    @Test
    void hAdmin_reducedTree_hsReportOnly_noAddon() {
        session("H", TenantContext.AUTH_GR_ADMIN);
        when(menuItemRepo.findAll()).thenReturn(allItems());
        when(productMenuRepo.findAllByProductType("H")).thenReturn(hMembership());
        when(translationRepo.findAllByProductTypeAndLangCd("H", "EN"))
                .thenReturn(enTranslations("H", hMembership()));

        NavigationResponse res = service.resolve();

        assertThat(res.menuGroupId()).isEqualTo("WE_CMSVN_HAENGSUNG");
        // Only Banking — no Add-on top menu.
        assertThat(res.topMenus()).extracting(NavMenuNode::id).containsExactly("banking");
        // Reports shows ONLY the HS Daily Financial.
        NavMenuNode reports = child(child(res.topMenus(), "banking").children(), "banking-reports");
        assertThat(reports.children()).extracting(NavMenuNode::permissionCd)
                .containsExactly("banking-reports-daily-financial-hs");
        // None of the F-only reports / Add-on leaked in.
        assertThat(cds(res.topMenus())).doesNotContain(
                "banking-reports-daily-financial",
                "banking-reports-daily-cash",
                "banking-reports-cash-flow",
                "addon-einvoices-sales",
                "addon-einvoices-expenses");
    }

    @Test
    void fNonAdmin_withGrants_intersectsTreeAndGrants() {
        session("F", TenantContext.AUTH_GR_USER);
        when(menuItemRepo.findAll()).thenReturn(allItems());
        when(productMenuRepo.findAllByProductType("F")).thenReturn(fmMembership("F"));
        when(translationRepo.findAllByProductTypeAndLangCd("F", "EN"))
                .thenReturn(enTranslations("F", fmMembership("F")));
        when(menuAuthService.grantedMenuCds(TENANT, USER))
                .thenReturn(List.of("banking-reports-daily-financial"));

        NavigationResponse res = service.resolve();

        // Only the granted leaf survives; parents un-collapse to reach it.
        assertThat(res.topMenus()).extracting(NavMenuNode::id).containsExactly("banking");
        NavMenuNode reports = child(child(res.topMenus(), "banking").children(), "banking-reports");
        assertThat(reports.children()).extracting(NavMenuNode::permissionCd)
                .containsExactly("banking-reports-daily-financial");
        // Add-on is hidden (no grant) and the ungranted reports are gone.
        assertThat(cds(res.topMenus())).containsExactly("banking-reports-daily-financial");
    }

    @Test
    void hNonAdmin_grantOutsideProductTree_isInert() {
        session("H", TenantContext.AUTH_GR_USER);
        when(menuItemRepo.findAll()).thenReturn(allItems());
        when(productMenuRepo.findAllByProductType("H")).thenReturn(hMembership());
        when(translationRepo.findAllByProductTypeAndLangCd("H", "EN"))
                .thenReturn(enTranslations("H", hMembership()));
        // Cash Flow is NOT in the H tree — granting it must render nothing.
        when(menuAuthService.grantedMenuCds(TENANT, USER))
                .thenReturn(List.of("banking-reports-cash-flow"));

        NavigationResponse res = service.resolve();

        assertThat(res.menuGroupId()).isEqualTo("WE_CMSVN_HAENGSUNG");
        assertThat(res.topMenus()).isEmpty();
    }

    @Test
    void hNonAdmin_withHsGrant_seesHsReport() {
        session("H", TenantContext.AUTH_GR_USER);
        when(menuItemRepo.findAll()).thenReturn(allItems());
        when(productMenuRepo.findAllByProductType("H")).thenReturn(hMembership());
        when(translationRepo.findAllByProductTypeAndLangCd("H", "EN"))
                .thenReturn(enTranslations("H", hMembership()));
        // F-1: an H user granted the SPLIT code sees the HS Daily Financial.
        // Granting the standard `banking-reports-daily-financial` would NOT
        // (it is not the H tree's leaf cd) — which is exactly why H-tenant
        // legacy grants are remapped by migration V244.
        when(menuAuthService.grantedMenuCds(TENANT, USER))
                .thenReturn(List.of("banking-reports-daily-financial-hs"));

        NavigationResponse res = service.resolve();

        assertThat(cds(res.topMenus())).containsExactly("banking-reports-daily-financial-hs");
    }

    @Test
    void hNonAdmin_withStandardDfGrant_doesNotSeeHsReport() {
        session("H", TenantContext.AUTH_GR_USER);
        when(menuItemRepo.findAll()).thenReturn(allItems());
        when(productMenuRepo.findAllByProductType("H")).thenReturn(hMembership());
        when(translationRepo.findAllByProductTypeAndLangCd("H", "EN"))
                .thenReturn(enTranslations("H", hMembership()));
        // The standard code is NOT the H tree's Daily Financial leaf, so it is
        // inert for H — the report stays hidden until the grant is remapped
        // (migration V244). Mirrors the validation in F-1 step 5.
        when(menuAuthService.grantedMenuCds(TENANT, USER))
                .thenReturn(List.of("banking-reports-daily-financial"));

        NavigationResponse res = service.resolve();

        assertThat(res.topMenus()).isEmpty();
    }

    @Test
    void nonAdmin_noGrants_seesNothing() {
        session("F", TenantContext.AUTH_GR_USER);
        when(menuItemRepo.findAll()).thenReturn(allItems());
        when(productMenuRepo.findAllByProductType("F")).thenReturn(fmMembership("F"));
        when(translationRepo.findAllByProductTypeAndLangCd("F", "EN"))
                .thenReturn(enTranslations("F", fmMembership("F")));
        when(menuAuthService.grantedMenuCds(TENANT, USER)).thenReturn(List.of());

        NavigationResponse res = service.resolve();

        assertThat(res.productType()).isEqualTo("F");
        assertThat(res.topMenus()).isEmpty();
    }
}
