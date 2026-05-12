package com.kosign.wecms.domain.setting.firmbankingsettings.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingAuditListResponse;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingSettingCreateRequest;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingSettingDetail;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingSettingListResponse;
import com.kosign.wecms.domain.setting.firmbankingsettings.dto.FirmBankingSettingUpdateRequest;
import com.kosign.wecms.domain.setting.firmbankingsettings.service.FirmBankingSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings &gt; Firm Banking Settings — read-only endpoints
 * (Phase 2, Path A). Mirrors the read paths of legacy
 * {@code comm_0220_*}:
 * <ul>
 *   <li>{@code GET /api/v1/settings/firm-banking-settings}
 *       → list (legacy {@code comm_0220_001_r001}).</li>
 *   <li>{@code GET .../{out_bank_cd}/{wdrl_act_no}}
 *       → detail (legacy {@code comm_0220_003_r001} row payload).</li>
 *   <li>{@code GET .../{out_bank_cd}/{wdrl_act_no}/audit}
 *       → audit-log history (legacy
 *       {@code comm_0220_003_r001} {@code REC} sub-result).</li>
 *   <li>{@code POST /api/v1/settings/firm-banking-settings}
 *       → create binding (legacy {@code comm_0220_002_c001}). Writes
 *       4 locale audit rows under one seq_no via the shared
 *       {@code AuditLogService}. Bounded-bank set per OQ-8a Option (c)
 *       — see service-level Javadoc.</li>
 *   <li>{@code PUT .../{out_bank_cd}/{wdrl_act_no}}
 *       → update editable fields (legacy
 *       {@code comm_0220_003_u001}). Writes 4 locale audit rows
 *       under one seq_no via the shared {@code AuditLogService}.</li>
 *   <li>{@code DELETE .../{out_bank_cd}/{wdrl_act_no}}
 *       → hard delete (legacy {@code comm_0220_001_d001}). Per OQ-2
 *       no audit row is written.</li>
 * </ul>
 *
 * <p>Wire format is lowercase snake_case. Java parameter names stay
 * camelCase; Spring's {@code @PathVariable("snake_name")} pins each
 * one to its snake_case URI segment. Validation lives in
 * {@code @Pattern} guards on the path parameters; deeper checks
 * (existence, tenant scoping) live in the service.
 *
 * <p>Per OQ-5 default the controller carries no {@code @PreAuthorize}
 * — JWT authentication is the only gate (matching JEX practical
 * behaviour). The {@code @Validated} class-level annotation enables
 * the {@code @Pattern} guards on path variables.
 */
@RestController
@RequestMapping("/api/v1/settings/firm-banking-settings")
@RequiredArgsConstructor
@Validated
public class FirmBankingSettingsController {

    /** Pattern shared by the two PK path components. {@code OUT_BANK_CD}
     *  is digits-only at the database level (NAPAS code); {@code
     *  WDRL_ACT_NO} can carry hyphens for some bank-format account
     *  numbers. Both share the same character class to keep the regex
     *  simple — the tighter discrimination would change the rejected
     *  shape without business value. */
    private static final String OUT_BANK_CD_PATTERN = "^[0-9]{1,15}$";
    private static final String WDRL_ACT_NO_PATTERN = "^[A-Za-z0-9\\-]{1,100}$";

    private final FirmBankingSettingsService service;

    /**
     * List every binding for the current tenant. No filters, no
     * pagination — matches legacy {@code comm_0220_001_r001} which
     * returns the entire result set for the tenant.
     */
    @GetMapping
    public ApiResponse<FirmBankingSettingListResponse> list() {
        return ApiResponse.ok(service.list());
    }

    /**
     * Detail by composite PK. {@code wdrl_act_no} is returned in
     * cleartext for the edit-dialog round-trip (per feature §9 /
     * OQ-4). The list endpoint masks the same field.
     */
    @GetMapping("/{out_bank_cd}/{wdrl_act_no}")
    public ApiResponse<FirmBankingSettingDetail> detail(
            @PathVariable("out_bank_cd")
            @Size(max = 15)
            @Pattern(regexp = OUT_BANK_CD_PATTERN, message = "out_bank_cd: invalid format")
            String outBankCd,
            @PathVariable("wdrl_act_no")
            @Size(max = 100)
            @Pattern(regexp = WDRL_ACT_NO_PATTERN, message = "wdrl_act_no: invalid format")
            String wdrlActNo) {
        return ApiResponse.ok(service.detail(outBankCd, wdrlActNo));
    }

    /**
     * Audit-log history for the row, in the caller's locale, latest
     * first. The frontend lazy-loads this when the audit tab opens —
     * a dedicated resource simplifies the detail dialog and lets the
     * audit query plan stay independent of the row read.
     */
    @GetMapping("/{out_bank_cd}/{wdrl_act_no}/audit")
    public ApiResponse<FirmBankingAuditListResponse> audit(
            @PathVariable("out_bank_cd")
            @Size(max = 15)
            @Pattern(regexp = OUT_BANK_CD_PATTERN, message = "out_bank_cd: invalid format")
            String outBankCd,
            @PathVariable("wdrl_act_no")
            @Size(max = 100)
            @Pattern(regexp = WDRL_ACT_NO_PATTERN, message = "wdrl_act_no: invalid format")
            String wdrlActNo) {
        return ApiResponse.ok(service.auditLog(outBankCd, wdrlActNo));
    }

    /**
     * Create a new firm-banking binding for the current tenant.
     * Mirrors legacy {@code comm_0220_002_c001}. Five required fields
     * on the wire ({@code out_bank_cd}, {@code wdrl_act_no},
     * {@code bank_prtn_id}, {@code van_org_cd}, {@code org_stat}) —
     * {@code file_type} is server-derived from {@code out_bank_cd}
     * per OQ-8a Option (c) and the {@code BANK_TO_FILE_TYPE} table.
     *
     * <p>Returns the created row in the standard
     * {@link FirmBankingSettingDetail} shape; on success the shared
     * {@code AuditLogService} appends 4 locale audit rows under one
     * {@code audit_logs_seq_no} (REQUIRES_NEW propagation).
     *
     * <p>Errors: {@code WBE0001} (out_bank_cd outside the supported
     * 3-bank set), {@code WBE0002} (bank not in {@code TB_FIRM_BANK_CODE}),
     * {@code WBE2021} (PK already exists), {@code WBE_VALIDATION}
     * (missing/malformed field).
     */
    @PostMapping
    public ApiResponse<FirmBankingSettingDetail> create(
            @Valid @RequestBody FirmBankingSettingCreateRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    /**
     * Update editable fields ({@code bank_prtn_id}, {@code van_org_cd},
     * {@code org_stat}) for one binding. Mirrors legacy
     * {@code comm_0220_003_u001}. Returns the updated row in the
     * standard {@link FirmBankingSettingDetail} shape; on success the
     * shared {@code AuditLogService} appends 4 locale audit rows
     * under one {@code audit_logs_seq_no} (REQUIRES_NEW propagation,
     * isolated from this PUT's transaction). Path PK is immutable —
     * the body MUST NOT carry {@code out_bank_cd} / {@code wdrl_act_no}.
     */
    @PutMapping("/{out_bank_cd}/{wdrl_act_no}")
    public ApiResponse<FirmBankingSettingDetail> update(
            @PathVariable("out_bank_cd")
            @Size(max = 15)
            @Pattern(regexp = OUT_BANK_CD_PATTERN, message = "out_bank_cd: invalid format")
            String outBankCd,
            @PathVariable("wdrl_act_no")
            @Size(max = 100)
            @Pattern(regexp = WDRL_ACT_NO_PATTERN, message = "wdrl_act_no: invalid format")
            String wdrlActNo,
            @Valid @RequestBody FirmBankingSettingUpdateRequest req) {
        return ApiResponse.ok(service.update(outBankCd, wdrlActNo, req));
    }

    /**
     * Hard-delete a binding by composite PK. Mirrors legacy
     * {@code comm_0220_001_d001}. Per OQ-2 no audit row is written —
     * the JEX runtime writes none either ({@code comm_0220_001_d001_act.jsp}
     * has no {@code AuditLogsInfm} call). Returns {@code WBE2020} when
     * the row does not exist for the current tenant.
     */
    @DeleteMapping("/{out_bank_cd}/{wdrl_act_no}")
    public ApiResponse<Void> delete(
            @PathVariable("out_bank_cd")
            @Size(max = 15)
            @Pattern(regexp = OUT_BANK_CD_PATTERN, message = "out_bank_cd: invalid format")
            String outBankCd,
            @PathVariable("wdrl_act_no")
            @Size(max = 100)
            @Pattern(regexp = WDRL_ACT_NO_PATTERN, message = "wdrl_act_no: invalid format")
            String wdrlActNo) {
        service.delete(outBankCd, wdrlActNo);
        return ApiResponse.ok(null);
    }
}
