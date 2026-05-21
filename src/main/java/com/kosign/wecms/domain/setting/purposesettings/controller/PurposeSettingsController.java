package com.kosign.wecms.domain.setting.purposesettings.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeExcelImportRequest;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeExcelImportResult;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeExcelPreview;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeReorderRequest;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeSettingCreateRequest;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeSettingDetail;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeSettingListResponse;
import com.kosign.wecms.domain.setting.purposesettings.dto.PurposeSettingUpdateRequest;
import com.kosign.wecms.domain.setting.purposesettings.service.PurposeExcelService;
import com.kosign.wecms.domain.setting.purposesettings.service.PurposeSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Settings &gt; Purpose endpoints. URI uses the kebab-case refactor
 * slug — never the legacy JEX page id. Mirrors:
 *
 * <ul>
 *   <li>{@code GET /api/v1/settings/purpose-settings[?keyword=]} —
 *       list (merges legacy {@code _r001} + {@code _r002}).</li>
 *   <li>{@code GET .../{use_usag_cd}} — single-PK detail.</li>
 *   <li>{@code POST /api/v1/settings/purpose-settings} — create
 *       (legacy {@code _c001}).</li>
 *   <li>{@code PUT .../{use_usag_cd}} — update (legacy {@code _u002}
 *       REC sub-rule).</li>
 *   <li>{@code DELETE .../{use_usag_cd}} — soft-delete (legacy
 *       {@code _u002} REC2 sub-rule + OQ-5 recursive check).</li>
 *   <li>{@code PUT .../order} — bulk reorder (legacy {@code _u004}).</li>
 *   <li>{@code POST .../excel/preview} — parse + validate uploaded
 *       Excel; mirrors legacy {@code comm_0187_004_r001}.</li>
 *   <li>{@code POST .../excel/import} — commit the previewed rows;
 *       mirrors legacy {@code comm_0187_004_c001}. Two-step flow
 *       matches the VAS Account precedent.</li>
 * </ul>
 *
 * <p>Wire format is lowercase snake_case via the global Jackson
 * SNAKE_CASE strategy ({@code JacksonConfig}). Path parameter is the
 * 10-character system PK {@code use_usag_cd}, never the user-visible
 * {@code hand_use_usag_cd}.
 *
 * <p>Per OQ-6 the controller carries no {@code @PreAuthorize} — JWT
 * authentication is the only gate. The {@code @Validated} class-level
 * annotation enables the {@code @Pattern} guards on path / query
 * parameters.
 */
@RestController
@RequestMapping("/api/v1/settings/purpose-settings")
@RequiredArgsConstructor
@Validated
public class PurposeSettingsController {

    /** Refactor-generated {@code use_usag_cd} values are 10-digit
     *  zero-padded strings ({@code 0000000001..0999999999}), but legacy
     *  data may carry 1-20 char codes — the path pattern allows the
     *  full schema range so legacy rows remain addressable. */
    private static final String USE_USAG_CD_PATTERN = "^[A-Za-z0-9]{1,20}$";

    /** Search keyword on {@code use_usag_nm} — case-insensitive
     *  substring; restricted to printable characters to avoid SQL
     *  metacharacter abuse before it hits the LIKE expression. */
    private static final String KEYWORD_PATTERN     = "^[\\p{L}\\p{N}\\p{P}\\p{Zs}]{0,100}$";

    private final PurposeSettingsService service;
    private final PurposeExcelService    excelService;

    /** List all active refactor-scope purposes for the current tenant.
     *  Optional {@code keyword} performs a case-insensitive substring
     *  match against {@code use_usag_nm}. */
    @GetMapping
    public ApiResponse<PurposeSettingListResponse> list(
            @RequestParam(value = "keyword", required = false)
            @Size(max = 100)
            @Pattern(regexp = KEYWORD_PATTERN, message = "keyword: invalid format")
            String keyword) {
        return ApiResponse.ok(service.list(keyword));
    }

    /** Detail by system PK. */
    @GetMapping("/{use_usag_cd}")
    public ApiResponse<PurposeSettingDetail> detail(
            @PathVariable("use_usag_cd")
            @Size(max = 20)
            @Pattern(regexp = USE_USAG_CD_PATTERN, message = "use_usag_cd: invalid format")
            String useUsagCd) {
        return ApiResponse.ok(service.detail(useUsagCd));
    }

    /** Create a new purpose. Returns the persisted detail echo. */
    @PostMapping
    public ApiResponse<PurposeSettingDetail> create(
            @Valid @RequestBody PurposeSettingCreateRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    /** Update an existing purpose. Path PK is immutable — the body
     *  carries the editable fields only. */
    @PutMapping("/{use_usag_cd}")
    public ApiResponse<PurposeSettingDetail> update(
            @PathVariable("use_usag_cd")
            @Size(max = 20)
            @Pattern(regexp = USE_USAG_CD_PATTERN, message = "use_usag_cd: invalid format")
            String useUsagCd,
            @Valid @RequestBody PurposeSettingUpdateRequest req) {
        return ApiResponse.ok(service.update(useUsagCd, req));
    }

    /** Soft-delete a purpose. Returns 409 + {@code WBE1199} when the
     *  row has active children OR (per OQ-5) when the row / any active
     *  child is referenced by an active transaction. */
    @DeleteMapping("/{use_usag_cd}")
    public ApiResponse<Void> delete(
            @PathVariable("use_usag_cd")
            @Size(max = 20)
            @Pattern(regexp = USE_USAG_CD_PATTERN, message = "use_usag_cd: invalid format")
            String useUsagCd) {
        service.delete(useUsagCd);
        return ApiResponse.ok(null);
    }

    /** Bulk reorder by submitting the full ordered set of
     *  {@code (use_usag_cd, disp_ord)} pairs. Returns the refreshed
     *  list. */
    @PutMapping("/order")
    public ApiResponse<PurposeSettingListResponse> reorder(
            @Valid @RequestBody PurposeReorderRequest req) {
        return ApiResponse.ok(service.reorder(req));
    }

    /* =================================================================
     *  Excel — preview / import (legacy comm_0187_004 family)
     *
     *  The "Download Template" button is served by the static XLSX
     *  shipped under `wecms-web/public/templates/purpose/`. No
     *  backend endpoint involved.
     * ================================================================= */

    /**
     * Parse + validate the uploaded Excel file. Returns the preview
     * payload — every parsed row (valid + invalid) plus the
     * completed / error counts the dialog renders in its summary
     * chips. Multipart with a single {@code file} part.
     *
     * <p>Validation errors per row are pre-localized server-side so
     * the preview UI renders cell tooltips verbatim. Mirrors legacy
     * {@code comm_0187_004_r001}.
     */
    @PostMapping(value = "/excel/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PurposeExcelPreview> excelPreview(
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(excelService.preview(file));
    }

    /**
     * Persist the preview rows. Pure-append (no Replace mode) per
     * legacy {@code comm_0187_004_c001}. Atomic — any row failure
     * rolls back the whole batch. Roots insert first so children can
     * resolve their parent's freshly-generated {@code use_usag_cd}.
     */
    @PostMapping(value = "/excel/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<PurposeExcelImportResult> excelImport(
            @Valid @RequestBody PurposeExcelImportRequest req) {
        return ApiResponse.ok(excelService.import_(req));
    }
}
