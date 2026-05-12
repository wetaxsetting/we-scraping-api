package com.kosign.wecms.domain.setting.vasaccount.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * Wire body for {@code POST /api/v1/settings/vas-account/excel/import}.
 *
 * <p>{@code upldOptn}:
 * <ul>
 *   <li>{@code "A"} — Append. Existing rows are kept; new rows are
 *       inserted. Duplicates against existing live rows are rejected
 *       with {@link com.kosign.wecms.domain.setting.vasaccount.service.VasAccountExcelService#WBE_DUPLICATE_CODE}
 *       (the legacy "Append + duplicate" failure mode).</li>
 *   <li>{@code "R"} — Reset/Replace. Every live row for the tenant is
 *       soft-deleted before the new payload is inserted. Atomic — a
 *       single transaction wraps the wipe + insert; failure rolls back
 *       both.</li>
 * </ul>
 *
 * <p>{@code rows} carries only the cells the UI exposes: {@code vasAcctCd}
 * and {@code vasAcctNm}. Type defaults to Dual on Create (PO call
 * 2026-05-11 — UI no longer surfaces Type).
 */
public record VasAccountExcelImportRequest(
        @NotNull
        @Pattern(regexp = "^[AR]$", message = "upld_optn: must be 'A' (Append) or 'R' (Reset/Replace)")
        String upldOptn,
        @NotNull
        List<Row> rows
) {
    /** One row from the preview the frontend forwarded back unchanged. */
    public record Row(String vasAcctCd, String vasAcctNm) { }
}
