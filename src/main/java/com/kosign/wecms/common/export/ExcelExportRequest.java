package com.kosign.wecms.common.export;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.Map;

/**
 * Wire body for {@code POST /api/v1/.../export} endpoints. Every
 * exportable list page sends this exact shape — per-resource controllers
 * are thin shims that forward the body to {@link ExcelExportService}
 * with a screen-specific {@link ExportRowProvider} resolved from the
 * registry.
 *
 * <p>Frontend responsibilities:
 * <ul>
 *   <li>Resolve the visible + ordered columns from the View Setting
 *       hook (`useViewSetting` → `vs.items` filtered by {@code USE_YN='Y'}
 *       + {@code !isSpecialViewSettingField(FIELD_ID)}).</li>
 *   <li>Translate column headers via the page's i18n dictionary.</li>
 *   <li>Pass current filter state (currency, bank, keyword, …) verbatim
 *       in {@link #filters()} — the per-screen provider decides which
 *       keys it understands.</li>
 *   <li>Pass current sort state in {@link #sort()} — same
 *       {@code FIELD_ID} + direction the list endpoint accepts.</li>
 * </ul>
 *
 * <p>Backend responsibilities:
 * <ul>
 *   <li>Validate {@link #screenId()} via the kebab-case regex (rejects
 *       legacy JEX ids and bad input).</li>
 *   <li>Resolve the {@link ExportRowProvider} via the registry; reject
 *       unknown screenIds with {@code WBE2010}.</li>
 *   <li>Intersect {@link #columns()} with the provider's allow-list AND
 *       the persisted View Setting visibility ({@code USE_YN='Y'}) so a
 *       stale / malicious frontend cannot leak hidden columns.</li>
 *   <li>Re-run the same filtered+sorted query the list endpoint does;
 *       cap row count at {@code 50_000} → {@code WBE2011}.</li>
 *   <li>Compute the subheader (count + totals) — backend is the source
 *       of truth (CLAUDE.md decision 5). The wire {@link #subHeader()}
 *       is informational fallback only.</li>
 * </ul>
 *
 * @param screenId   View Setting {@code WEB_SVC_ID} —
 *                   semantic kebab-case (e.g. {@code banking-account-inquiry-vnd}).
 * @param dymAreaId  View Setting {@code DYM_AREA_ID}; single-area pages
 *                   pass {@code "default"}.
 * @param fileName   Caller-supplied filename without extension (e.g.
 *                   {@code "Banking Account Inquiry (VND)"}); the
 *                   controller appends {@code .xlsx}.
 * @param filters    Free-form filter state (interpreted per-screen).
 *                   {@code null} or empty is allowed.
 * @param sort       Optional sort — same wire shape as the list endpoint.
 * @param columns    Visible + ordered columns from the page's View
 *                   Setting state. Order matters; backend emits columns
 *                   in this order after the visibility intersection.
 * @param subHeader  Optional caller-supplied summary line. Ignored when
 *                   the provider returns its own ({@link ExportFetch#subHeader()}).
 * @param title      Optional in-sheet title shown in the merged row 0
 *                   header cell. Distinct from {@link #fileName()} on
 *                   purpose — the on-screen page title (e.g.
 *                   {@code "계좌 조회 (VND)"}) flows here verbatim,
 *                   while the filename uses the legacy filesystem
 *                   convention (no inner space + DDMMYYYY suffix:
 *                   {@code "계좌조회(VND)_02052026"}). Falls back to
 *                   {@link #fileName()} when {@code null} so existing
 *                   callers keep working.
 */
public record ExcelExportRequest(
        @JsonProperty("screenId")
        @NotBlank
        @Pattern(
                regexp = "^[a-z][a-z0-9]*(-[a-z0-9]+)+$",
                message = "screenId must be lowercase kebab-case with at least 2 segments"
        )
        String screenId,

        @JsonProperty("dymAreaId")
        @NotBlank
        @Pattern(
                regexp = "^[a-z][a-z0-9]*(-[a-z0-9]+)*$",
                message = "dymAreaId must be lowercase kebab-case"
        )
        String dymAreaId,

        @JsonProperty("fileName")  String fileName,
        @JsonProperty("title")     String title,
        @JsonProperty("filters")   Map<String, Object> filters,
        @JsonProperty("sort")      ExcelSortSpec sort,
        @JsonProperty("columns")   List<ExcelColumnSpec> columns,
        @JsonProperty("subHeader") String subHeader
) { }
