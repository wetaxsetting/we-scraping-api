package com.kosign.wecms.domain.setting.vasaccount;

import com.kosign.wecms.domain.setting.vasaccount.repository.BusiVasAcctInfmRepository;
import com.kosign.wecms.domain.setting.vasaccount.service.VasAccountExcelService;
import com.kosign.wecms.domain.setting.vasaccount.service.VasAccountService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration-test skeleton for Settings &gt; VAS Account — addresses
 * audit finding F1 in {@code docs/refactor-wecms/audits/vas-account.md}.
 *
 * <p><b>Status — disabled until shared integration-test infrastructure
 * lands.</b> Every test below is annotated {@link Disabled}; the bodies
 * describe what the test would verify so reviewers can confirm matrix
 * coverage against the testing plan. The class wires
 * {@link SpringBootTest @SpringBootTest} + {@link ActiveProfiles}{@code
 * ("test")} so a single un-{@code @Disabled} removal activates the suite
 * once the supporting infra is in place.
 *
 * <p><b>What's blocking activation</b> (tracked alongside this class):
 * <ul>
 *   <li>The repository carries no Testcontainers dependency
 *       ({@code wecms-api/CLAUDE.md}: "Not currently in
 *       {@code build.gradle}: SpringDoc OpenAPI, Testcontainers. Add
 *       them in the sprint where the first feature actually needs them.").</li>
 *   <li>{@code application-test.yml} uses {@code ddl-auto=create-drop}
 *       + {@code flyway.enabled=false} — it expects Hibernate to
 *       reconstruct the schema from entity declarations. Several legacy
 *       tables this feature interacts with (audit ledger, screen-option
 *       tables) declare only a Sprint subset of columns, so Hibernate-
 *       generated DDL would be incomplete.</li>
 *   <li>No JWT mock / security relaxation profile exists; every endpoint
 *       in {@link com.kosign.wecms.domain.setting.vasaccount.controller.VasAccountController}
 *       sits behind {@code SecurityConfig}'s blanket {@code authenticated()}
 *       rule.</li>
 * </ul>
 *
 * <p><b>Activation plan</b> (one MR — out of scope for the VAS Account
 * feature MR):
 * <ol>
 *   <li>Add {@code testImplementation 'org.testcontainers:postgresql'}
 *       + {@code 'org.testcontainers:junit-jupiter'} to
 *       {@code build.gradle}.</li>
 *   <li>Add a shared {@code @PostgresIntegrationTest} meta-annotation
 *       that boots a tenant-scoped PostgreSQL container with Flyway
 *       migrations applied.</li>
 *   <li>Add a {@code TestSecurityConfig} (or
 *       {@code @WithMockUser(authorities = "ROLE_ADMIN")}) so MockMvc
 *       calls don't 401.</li>
 *   <li>Drop the per-method {@link Disabled} markers.</li>
 * </ol>
 *
 * <p>Until activation lands, the unit-level coverage in:
 * <ul>
 *   <li>{@link com.kosign.wecms.domain.setting.vasaccount.service.VasAccountServiceTest}
 *       (≈ 20 tests — CRUD, sort whitelist, pagination clamp, audit emission,
 *       i18n key mapping, status-filter normalisation).</li>
 *   <li>{@link com.kosign.wecms.domain.setting.vasaccount.service.VasAccountExcelServiceTest}
 *       (preview happy + 500-row cap + bad header + per-cell errors,
 *       append + replace import paths, audit emission).</li>
 *   <li>{@link com.kosign.wecms.domain.setting.vasaccount.controller.VasAccountControllerTest}
 *       (snake_case wire round-trip, request param decoding, error
 *       envelope shape).</li>
 *   <li>{@link com.kosign.wecms.domain.setting.vasaccount.service.VasAccountSortFieldTest}
 *       (enum parse — lenient on unknown / blank).</li>
 *   <li>{@link com.kosign.wecms.domain.setting.vasaccount.export.VasAccountExportProviderTest}
 *       (export filter / sort wiring, label resolution, subheader shape).</li>
 * </ul>
 * carries the bulk of the behavioural contract. The scenarios below are
 * the cross-layer integration checks — DB-side tenant isolation, the
 * actual {@code AUDIT_LOGS_INFM} insert, the {@code @Transactional}
 * rollback contract on Replace, and View Setting lazy-init — that
 * Mockito cannot model.
 *
 * <p>See also {@code docs/refactor-wecms/testing/vas-account.md §1.F1}
 * for the deferral note + checklist.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@ActiveProfiles("test")
@Disabled("F1 — class-level disable so JUnit skips without booting the Spring context. "
        + "Re-enable once the Testcontainers + JWT-mock shared infra lands (see class Javadoc).")
class VasAccountIntegrationTest {

    /** Tenant scoped via test fixtures (currently a placeholder — the
     *  activation MR replaces this with a {@code @Testcontainers}
     *  PostgreSQL bring-up + Flyway migration). */
    private static final String TENANT_A = "VN_VNM0004_000001";

    /** Second tenant for the cross-tenant negative tests. The seed
     *  belongs to V73 (HAENGSUNG). */
    private static final String TENANT_B = "VN_VNM0004_000014";

    @Autowired(required = false) MockMvc                  mvc;
    @Autowired(required = false) VasAccountService        vasAccountService;
    @Autowired(required = false) VasAccountExcelService   vasAccountExcelService;
    @Autowired(required = false) BusiVasAcctInfmRepository repository;

    /* =================================================================
     *  CRUD round-trips
     * ================================================================= */

    @Test
    @Disabled("F1 — pending shared integration-test infrastructure (Testcontainers + JWT mock).")
    @DisplayName("list returns seeded rows for the current tenant")
    void list_returnsSeededRows() {
        // After Flyway V78 seed, GET /api/v1/settings/vas-account for
        // VN_VNM0004_000001 returns >= 1 row mapped correctly with
        // vas_acct_seq_no as a string + vas_acct_type_key resolved
        // ("settings.vas_account.type.{...}").
    }

    @Test
    @Disabled("F1 — pending shared integration-test infrastructure.")
    @DisplayName("list filters by status — A / I / both / neither")
    void list_filtersByStatus() {
        // Insert 1 ACTIVE + 1 INACTIVE; ?stts=A returns 1, ?stts=I
        // returns 1, ?stts=A&stts=I returns both. (Empty `statuses` on
        // the frontend short-circuits without a wire call — separate
        // hook contract.)
    }

    @Test
    @Disabled("F1 — pending shared integration-test infrastructure.")
    @DisplayName("list filters by search_wd against vas_acct_cd OR vas_acct_nm (ILIKE)")
    void list_filtersBySearchWd() {
        // Insert row with vas_acct_nm = "Tiền mặt"; ?search_wd=tiền
        // returns it (case-insensitive ILIKE, escape '%' / '_' / '!').
    }

    @Test
    @Disabled("F1 — pending shared integration-test infrastructure.")
    @DisplayName("CRUD round-trip: create → detail → update → delete → detail-404")
    void crud_roundTrip() {
        // Each step round-trips through real Hibernate + JDBC. Final
        // GET /{vasAcctSeqNo} returns WBE2040 envelope after the soft
        // delete (DEL_YN='Y' makes the row invisible to the live filter).
    }

    @Test
    @Disabled("F1 — pending shared integration-test infrastructure.")
    @DisplayName("pagination — page 0 of 25 rows returns 20 rows + totalPages=2")
    void pagination_pageZeroOfTwentyFive() {
        // ?page=0&size=20 returns 20 rows; pageNumber=0, pageSize=20,
        // totalCount=25, totalPages=2.
    }

    @Test
    @Disabled("F1 — pending shared integration-test infrastructure.")
    @DisplayName("pagination — size clamped to MAX_PAGE_SIZE (100)")
    void pagination_sizeClamp() {
        // ?size=500 → clamped; pageSize=100 echoed back.
    }

    /* =================================================================
     *  Tenant isolation
     * ================================================================= */

    @Test
    @Disabled("F1 — pending shared integration-test infrastructure.")
    @DisplayName("tenant A's list never includes tenant B's rows")
    void list_doesNotLeakCrossTenant() {
        // Seed one row each for TENANT_A + TENANT_B; authenticate as
        // TENANT_A; GET /api/v1/settings/vas-account; assert payload
        // includes only TENANT_A's seq_no.
    }

    @Test
    @Disabled("F1 — pending shared integration-test infrastructure.")
    @DisplayName("tenant A cannot read tenant B's row by seq_no — returns WBE2040")
    void detail_doesNotLeakCrossTenant() {
        // GET /api/v1/settings/vas-account/{tenantB_seqNo} → 200 with
        // status.code = WBE2040. The envelope does NOT distinguish
        // not-found vs wrong-tenant — single envelope merge.
    }

    @Test
    @Disabled("F1 — pending shared integration-test infrastructure.")
    @DisplayName("tenant A cannot update tenant B's row — returns WBE2040")
    void update_doesNotLeakCrossTenant() {
        // PUT /api/v1/settings/vas-account/{tenantB_seqNo} → WBE2040
        // envelope; tenant B's row is unchanged in the DB.
    }

    @Test
    @Disabled("F1 — pending shared integration-test infrastructure.")
    @DisplayName("tenant A cannot delete tenant B's row — returns WBE2040")
    void softDelete_doesNotLeakCrossTenant() {
        // DELETE /api/v1/settings/vas-account/{tenantB_seqNo} → WBE2040;
        // tenant B's row still DEL_YN='N'.
    }

    @Test
    @Disabled("F1 — pending shared integration-test infrastructure.")
    @DisplayName("Excel export does not leak cross-tenant rows")
    void export_doesNotLeakCrossTenant() {
        // POST /api/v1/settings/vas-account/export as TENANT_A; parse
        // the response XLSX; assert no TENANT_B vas_acct_cd values
        // appear in the workbook.
    }

    /* =================================================================
     *  Excel preview / import / replace
     * ================================================================= */

    @Test
    @Disabled("F1 — pending shared integration-test infrastructure.")
    @DisplayName("Excel preview — 10-row valid sheet returns tot=10, suc=10, err=0")
    void excelPreview_happyPath() {
        // POST /excel/preview with a 10-row workbook (3-row preamble
        // + valid header at row 2 + 10 data rows). Response carries 10
        // valid rows with empty `errors`.
    }

    @Test
    @Disabled("F1 — pending shared integration-test infrastructure.")
    @DisplayName("Excel preview — 501-row sheet → WBE2041 (cap exceeded)")
    void excelPreview_500RowCap() {
        // Sheet with 501 data rows → 200 with status.code = WBE2041
        // and {0} bound to "500".
    }

    @Test
    @Disabled("F1 — pending shared integration-test infrastructure.")
    @DisplayName("Excel preview — bad header row → WBE2042")
    void excelPreview_badHeader() {
        // Sheet whose row 2 carries garbage labels in columns B + C →
        // WBE2042 envelope.
    }

    @Test
    @Disabled("F1 — pending shared integration-test infrastructure.")
    @DisplayName("Excel preview — duplicate vas_acct_cd against existing live row flagged")
    void excelPreview_duplicateAgainstDb() {
        // Insert a live row with vas_acct_cd='111'. Upload a preview
        // sheet that also contains '111'. The matching row appears
        // with errors.vas_acct_cd.code = 'dup_db'.
    }

    @Test
    @Disabled("F1 — pending shared integration-test infrastructure.")
    @DisplayName("Excel import Append — 5 valid rows persist; 1 audit row emitted")
    void excelImport_appendHappy() {
        // POST /excel/import with upld_optn='A' + 5 valid rows. After
        // commit: GET /api/v1/settings/vas-account returns the 5 new
        // rows; AUDIT_LOGS_INFM has exactly one new row with
        // wsvc_id='vas-account', detail_key=audit.vas_account.excel_append.detail,
        // ref_no2='APPEND' (current contract — see audit F6 follow-up).
    }

    @Test
    @Disabled("F1 — pending shared integration-test infrastructure.")
    @DisplayName("Excel import Replace — wipes existing 3 + inserts 5; atomic")
    void excelImport_replaceReplaces() {
        // Existing 3 live rows; POST /excel/import with upld_optn='R'
        // + 5 new rows. Post-commit: old 3 are DEL_YN='Y'; new 5 are
        // DEL_YN='N'. AUDIT_LOGS_INFM has one EXCEL_REPLACE row.
    }

    @Test
    @Disabled("F1 — pending shared integration-test infrastructure.")
    @DisplayName("Excel import Replace — rollback on insert failure preserves wipe")
    void excelImport_replaceRollsBackOnFailure() {
        // Inject a constraint violation mid-insert (e.g. force a
        // NOT-NULL on a required field via a payload row missing
        // vas_acct_nm — bean validation would normally catch this; the
        // test simulates a deeper failure). Verify the soft-delete is
        // rolled back so the user's pre-import data is intact.
    }

    /* =================================================================
     *  Audit ledger
     * ================================================================= */

    @Test
    @Disabled("F1 — pending shared integration-test infrastructure.")
    @DisplayName("each user-facing write emits exactly one AUDIT_LOGS_INFM row with wsvc_id='vas-account'")
    void audit_emitsOneRowPerAction() {
        // Run CREATE → UPDATE → DELETE → EXCEL_IMPORT_APPEND →
        // EXCEL_IMPORT_REPLACE in sequence. Count AUDIT_LOGS_INFM rows
        // for wsvc_id='vas-account' = 5 (one per locale × wsvc — note
        // AuditLogService writes one row per supported locale, so the
        // actual count depends on the resolver's locale set; assert the
        // distinct activity_key set instead).
    }

    /* =================================================================
     *  View Setting
     * ================================================================= */

    @Test
    @Disabled("F1 — pending shared integration-test infrastructure.")
    @DisplayName("View Setting lazy-init for vas-account on first GET")
    void viewSetting_lazyInit() {
        // First GET /api/v1/screen-options/vas-account/default for a
        // fresh user copies V79's sys defaults into USER_SCR_OPTION_WECMS.
        // Subsequent calls reflect saved user overrides.
    }
}
