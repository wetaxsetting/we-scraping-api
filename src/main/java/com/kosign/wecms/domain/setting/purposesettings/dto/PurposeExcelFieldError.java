package com.kosign.wecms.domain.setting.purposesettings.dto;

/**
 * One per-field validation error on a Purpose Excel preview row. The
 * frontend renders these as cell-level tooltips on the preview grid;
 * {@link #field} matches the wire {@code col.id} on the shared
 * {@code <ExcelUploadDialog>} so the cell can be highlighted.
 *
 * <p>{@link #message} is pre-localized server-side (caller's
 * Accept-Language / JWT {@code langCd}) so the dialog renders the text
 * verbatim without a second i18n lookup. Mirrors the legacy
 * {@code comm_0187_004_r001} {@code ERR_FLD_CAUS} payload behaviour.
 *
 * <p>{@link #code} categorises the validation failure:
 * <ul>
 *   <li>{@code "required"} — the cell was empty.</li>
 *   <li>{@code "length"}   — the value exceeds the column's max length.</li>
 *   <li>{@code "dup_file"} — duplicate of an earlier valid row in the
 *                            same file (`hand_use_usag_cd` only).</li>
 *   <li>{@code "dup_db"}   — duplicate of an existing active row in
 *                            the tenant's DB (`hand_use_usag_cd` only).</li>
 *   <li>{@code "parent_not_found"} — `parent_use_usag_cd` does not
 *                            resolve to an active level-1 row
 *                            (either in the file as a root, or in
 *                            the DB as a level-1 purpose).</li>
 *   <li>{@code "parent_not_root"} — `parent_use_usag_cd` resolves to
 *                            a level-2 row, which would create a
 *                            three-level hierarchy (forbidden per
 *                            the legacy 2-level cap).</li>
 * </ul>
 */
public record PurposeExcelFieldError(
        /** Cell key — matches the frontend `col.id`
         *  ({@code "hand_use_usag_cd"} / {@code "use_usag_nm"} /
         *  {@code "use_usag_cont"} / {@code "parent_use_usag_cd"}). */
        String field,
        /** Category code — see Javadoc above. */
        String code,
        /** Pre-localized cell-level error message. */
        String message
) { }
