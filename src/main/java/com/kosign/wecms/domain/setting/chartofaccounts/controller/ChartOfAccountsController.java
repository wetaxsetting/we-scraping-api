package com.kosign.wecms.domain.setting.chartofaccounts.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountBulkActionRequest;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountBulkActionResponse;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountBulkUploadRequest;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountBulkUploadResponse;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountCreateRequest;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountListResponse;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountRow;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountTreeResponse;
import com.kosign.wecms.domain.setting.chartofaccounts.dto.ChartOfAccountUpdateRequest;
import com.kosign.wecms.domain.setting.chartofaccounts.service.ChartOfAccountsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings &gt; Chart of Accounts — six REST endpoints under
 * {@code /api/v1/settings/chart-of-accounts}, mirroring the legacy
 * {@code comm_0223_001_*} surface (plus an explicit
 * {@code /tree} projection for the parent-account picker):
 * <ul>
 *   <li>{@code GET    /api/v1/settings/chart-of-accounts}
 *       → list (legacy {@code comm_0223_001_r001}).</li>
 *   <li>{@code GET    /api/v1/settings/chart-of-accounts/tree}
 *       → parent-picker tree (legacy {@code comm_0223_001_r002}).</li>
 *   <li>{@code GET    /api/v1/settings/chart-of-accounts/{chart_acct_seq_no}}
 *       → single-row read (used by the frontend deep-link path).</li>
 *   <li>{@code POST   /api/v1/settings/chart-of-accounts}
 *       → create / revive (legacy {@code comm_0223_001_c001}).</li>
 *   <li>{@code PUT    /api/v1/settings/chart-of-accounts/{chart_acct_seq_no}}
 *       → update (legacy {@code comm_0223_001_u001}, minus the
 *       deliberately-dropped lines 237-256 — see OQ-3).</li>
 *   <li>{@code DELETE /api/v1/settings/chart-of-accounts/{chart_acct_seq_no}}
 *       → soft delete (legacy {@code comm_0223_001_d001}).</li>
 * </ul>
 *
 * <p>Wire format is lowercase snake_case (global Jackson naming
 * strategy from {@code config/JacksonConfig}). Path / query / body
 * fields use camelCase in Java; {@code @PathVariable("chart_acct_seq_no")}
 * pins the segment to its snake_case URI name.
 *
 * <p>OQ-1 default (resolved by sprint-day-1 plan): no
 * {@code @PreAuthorize} — JWT authentication is the only gate, matches
 * live JEX behaviour. {@code @Validated} at the class level enables
 * the {@code @NotBlank}/{@code @Pattern} guards on the path id.
 *
 * <p>Audit-log: enabled (writes one event per Create / Update /
 * Delete to {@code AUDIT_LOGS_INFM} with {@code wsvc_id =
 * "chart-of-accounts"}). Matches firm-banking-settings / contacts
 * precedent.
 */
@RestController
@RequestMapping("/api/v1/settings/chart-of-accounts")
@RequiredArgsConstructor
@Validated
public class ChartOfAccountsController {

    private final ChartOfAccountsService service;

    /**
     * List every active row for the current tenant. Optional
     * case-insensitive substring search ({@code search}) over
     * {@code chart_acct_cd} OR {@code chart_acct_nm_en}, plus the
     * common-sort triple ({@code sort_field} / {@code sort_dir}).
     */
    @GetMapping
    public ApiResponse<ChartOfAccountListResponse> list(
            @RequestParam(name = "search",     required = false) String search,
            @RequestParam(name = "sort_field", required = false) String sortField,
            @RequestParam(name = "sort_dir",   required = false) String sortDir) {
        return ApiResponse.ok(service.list(search, sortField, sortDir));
    }

    /**
     * Tree projection for the inline parent-account picker. Active
     * rows whose hierarchy level resolves to 1..4; orphans / deeper
     * rows are filtered out (mirrors legacy R002 outer filter).
     */
    @GetMapping("/tree")
    public ApiResponse<ChartOfAccountTreeResponse> tree() {
        return ApiResponse.ok(service.tree());
    }

    /**
     * Single-row read. Used by the frontend Settings panel when
     * navigating with a deep-link / after a refresh. Returns the same
     * projected shape as one element of the list response.
     */
    @GetMapping("/{chart_acct_seq_no}")
    public ApiResponse<ChartOfAccountRow> detail(
            @PathVariable("chart_acct_seq_no")
            @NotBlank @Pattern(regexp = "\\d{1,14}") String chartAcctSeqNo) {
        return ApiResponse.ok(service.detail(chartAcctSeqNo));
    }

    /**
     * Create a new chart-of-accounts row, or revive a soft-deleted
     * row with the same code. Allocates {@code chart_acct_seq_no} via
     * the legacy {@code wb_get_next_val} branch (V95) for new inserts.
     *
     * <p>Errors: {@code WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE},
     * {@code WBE_CHART_OF_ACCOUNTS_PARENT_NOT_FOUND},
     * {@code WBE_CHART_OF_ACCOUNTS_SELF_PARENT},
     * {@code WBE_CHART_OF_ACCOUNTS_DEPTH_EXCEEDED}.
     */
    @PostMapping
    public ApiResponse<ChartOfAccountRow> create(
            @Valid @RequestBody ChartOfAccountCreateRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    /**
     * Update an existing chart-of-accounts row. Path
     * {@code chart_acct_seq_no} is immutable — the body MUST NOT
     * carry it.
     *
     * <p>Errors: {@code WBE_CHART_OF_ACCOUNTS_NOT_FOUND},
     * {@code WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE} (against
     * ACC-01/02/03), plus every code from
     * {@link #create(ChartOfAccountCreateRequest)} and
     * {@code WBE_CHART_OF_ACCOUNTS_CYCLE}.
     */
    @PutMapping("/{chart_acct_seq_no}")
    public ApiResponse<ChartOfAccountRow> update(
            @PathVariable("chart_acct_seq_no")
            @NotBlank @Pattern(regexp = "\\d{1,14}") String chartAcctSeqNo,
            @Valid @RequestBody ChartOfAccountUpdateRequest req) {
        return ApiResponse.ok(service.update(chartAcctSeqNo, req));
    }

    /**
     * Bulk-create chart-of-accounts rows from an Excel upload batch.
     * Each row is processed independently — a failure on one row does
     * not roll back rows that already committed. Returns the created /
     * failed totals so the frontend can show the partial-failure dialog.
     *
     * <p>The endpoint delegates to
     * {@link ChartOfAccountsService#bulkUpload(ChartOfAccountBulkUploadRequest)}
     * which calls the single-row {@link ChartOfAccountsService#create}
     * through the Spring proxy so each row runs in its own transaction.
     */
    @PostMapping("/bulk-upload")
    public ApiResponse<ChartOfAccountBulkUploadResponse> bulkUpload(
            @Valid @RequestBody ChartOfAccountBulkUploadRequest req) {
        return ApiResponse.ok(service.bulkUpload(req));
    }

    /**
     * Bulk soft-delete chart-of-accounts rows by seq_no. Powers the
     * Settings panel's multi-select Delete button (rendered only when
     * the user has checked at least one row). Each seq_no is processed
     * independently — failures (restricted code, not found, etc.) are
     * tallied in {@code failed} and do NOT roll back rows that already
     * committed.
     */
    @PostMapping("/bulk-delete")
    public ApiResponse<ChartOfAccountBulkActionResponse> bulkDelete(
            @Valid @RequestBody ChartOfAccountBulkActionRequest req) {
        return ApiResponse.ok(service.bulkDelete(req));
    }

    /**
     * Bulk deactivate chart-of-accounts rows by seq_no — flips
     * {@code STTS='I'} on every selected row. Powers the Settings
     * panel's multi-select Deactivate button. Idempotent per-row:
     * already-inactive rows pass through with no DB write. Restricted
     * roots ({@code ACC-01 / 02 / 03}) are tallied in {@code failed}
     * via {@code WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE}.
     */
    @PostMapping("/bulk-deactivate")
    public ApiResponse<ChartOfAccountBulkActionResponse> bulkDeactivate(
            @Valid @RequestBody ChartOfAccountBulkActionRequest req) {
        return ApiResponse.ok(service.bulkDeactivate(req));
    }

    /**
     * Soft-delete a chart-of-accounts row for the current tenant.
     * Re-parents every active child to the deleting row's parent in
     * the same transaction. Returns an empty success envelope.
     *
     * <p>Errors: {@code WBE_CHART_OF_ACCOUNTS_NOT_FOUND},
     * {@code WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE}.
     */
    @DeleteMapping("/{chart_acct_seq_no}")
    public ApiResponse<Void> delete(
            @PathVariable("chart_acct_seq_no")
            @NotBlank @Pattern(regexp = "\\d{1,14}") String chartAcctSeqNo) {
        service.delete(chartAcctSeqNo);
        return ApiResponse.ok(null);
    }
}
