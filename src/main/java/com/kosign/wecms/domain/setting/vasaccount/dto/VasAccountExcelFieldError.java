package com.kosign.wecms.domain.setting.vasaccount.dto;

/**
 * One per-field validation error on an Excel preview row. The frontend
 * renders these as cell-level tooltips on the preview grid; the
 * {@code field} value matches the wire {@code col.id} on the shared
 * {@code <ExcelUploadDialog>} so the cell can be highlighted.
 *
 * <p>{@code message} is intentionally pre-localized server-side
 * (caller's Accept-Language → {@code TenantContext.currentLangCd()})
 * so the dialog can render the text verbatim without a second i18n
 * lookup. Keeps cell-level error text consistent with the legacy
 * tooltip behaviour.
 *
 * <p>{@code code} categorises the validation failure so the frontend
 * can re-derive valid/invalid state when the user switches the
 * upload option without a network round-trip:
 * <ul>
 *   <li>{@code "required"}  — the cell was empty.</li>
 *   <li>{@code "length"}    — the value is outside the 3-15 length window
 *                              (Code) or over 50 chars (Name).</li>
 *   <li>{@code "format"}    — the value contains characters outside
 *                              {@code [A-Za-z0-9_]} (Code only).</li>
 *   <li>{@code "dup_file"}  — duplicate of an earlier valid row in the
 *                              same file (Code only).</li>
 *   <li>{@code "dup_db"}    — duplicate of an existing live row in the
 *                              tenant's DB (Code only). The Reset/Replace
 *                              upload mode IGNORES this category client-
 *                              side and server-side because the wipe-and-
 *                              insert path clears the conflict before
 *                              writing.</li>
 * </ul>
 */
public record VasAccountExcelFieldError(
        /** Cell key — {@code "vas_acct_cd"} or {@code "vas_acct_nm"}.
         *  Matches the frontend {@code col.id}. */
        String field,
        /** Category code — see Javadoc above. */
        String code,
        /** Pre-localized cell-level error message. */
        String message
) { }
