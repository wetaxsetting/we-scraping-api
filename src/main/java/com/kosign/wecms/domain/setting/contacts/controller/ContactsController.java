package com.kosign.wecms.domain.setting.contacts.controller;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExcelExportService;
import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.setting.contacts.dto.ContactBankListResponse;
import com.kosign.wecms.domain.setting.contacts.dto.ContactBulkDeleteRequest;
import com.kosign.wecms.domain.setting.contacts.dto.ContactBulkImportRequest;
import com.kosign.wecms.domain.setting.contacts.dto.ContactBulkImportResponse;
import com.kosign.wecms.domain.setting.contacts.dto.ContactsByCodesRequest;
import com.kosign.wecms.domain.setting.contacts.dto.ContactsExcelPreflightRequest;
import com.kosign.wecms.domain.setting.contacts.dto.ContactsExcelPreflightResponse;
import com.kosign.wecms.domain.setting.contacts.dto.ContactsExcelResolveRecipientsRequest;
import com.kosign.wecms.domain.setting.contacts.dto.ContactsExcelResolveRecipientsResponse;
import com.kosign.wecms.domain.setting.contacts.dto.ContactCreateRequest;
import com.kosign.wecms.domain.setting.contacts.dto.ContactFileListResponse;
import com.kosign.wecms.domain.setting.contacts.dto.ContactFileRow;
import com.kosign.wecms.domain.setting.contacts.dto.ContactListResponse;
import com.kosign.wecms.domain.setting.contacts.dto.ContactLookupRecipientRequest;
import com.kosign.wecms.domain.setting.contacts.dto.ContactLookupRecipientResponse;
import com.kosign.wecms.domain.setting.contacts.dto.ContactRow;
import com.kosign.wecms.domain.setting.contacts.dto.ContactUpdateRequest;
import com.kosign.wecms.domain.setting.contacts.service.ContactsDownloadService;
import com.kosign.wecms.domain.setting.contacts.service.ContactsFileService;
import com.kosign.wecms.domain.setting.contacts.service.ContactsFileService.DownloadResource;
import com.kosign.wecms.domain.setting.contacts.service.ContactsImportService;
import com.kosign.wecms.domain.setting.contacts.service.ContactsService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import java.util.List;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

/**
 * Settings &gt; Contacts &mdash; REST controller. Mirrors the legacy
 * {@code comm_0184_001} (list) + {@code comm_0184_002_c001} (create) +
 * {@code comm_0184_005_u001} (update) + {@code comm_0184_001_u001}
 * (bulk soft-delete) WSVCs over a slug-named refactor REST surface.
 *
 * <p>Wire format: lowercase snake_case via the global Jackson
 * SNAKE_CASE strategy. Validation lives in {@link ContactsService} +
 * {@link com.kosign.wecms.domain.setting.contacts.service.ContactsValidator};
 * the controller is a thin pass-through that wraps the result in
 * {@link ApiResponse}.
 *
 * <p>Authorization: every endpoint is under {@code /api/v1/*} and
 * therefore {@code authenticated()} per {@code SecurityConfig}.
 * Tenant scope is read from {@code TenantContext.currentBizKey()};
 * the wire never carries {@code biz_key}.
 */
@RestController
@RequestMapping("/api/v1/settings/contacts")
@RequiredArgsConstructor
@Validated
@PreAuthorize("@menuAuthz.canAccess('settings-core-contacts')")
public class ContactsController {

    private final ContactsService service;
    private final ContactsImportService importService;
    private final ContactsFileService fileService;
    private final ExcelExportService excelExportService;
    private final ContactsDownloadService downloadService;

    /**
     * List managed (non-trashed) contacts for the current tenant.
     *
     * @param search         optional ILIKE pattern across {@code trco_cmnm}
     *                       / {@code trco_cmenm} / {@code hand_trco_key}
     * @param trcoType       optional {@code 'L'} / {@code 'O'} filter
     * @param page           zero-based page index (default {@code 0})
     * @param size           rows per page (default {@link ContactsService#DEFAULT_PAGE_SIZE},
     *                       clamped to {@link ContactsService#MAX_PAGE_SIZE})
     * @param sortField      whitelisted {@code FIELD_ID}
     *                       (see {@link com.kosign.wecms.domain.setting.contacts.service.ContactSortField})
     * @param sortDirection  {@code ASC} / {@code DESC}; both unset or
     *                       unknown &rarr; default ORDER BY
     */
    @GetMapping
    public ApiResponse<ContactListResponse> list(
            @RequestParam(value = "search",         required = false) String search,
            @RequestParam(value = "trco_type",      required = false) String trcoType,
            @RequestParam(value = "page",           defaultValue = "0") int page,
            @RequestParam(value = "size",           defaultValue = "15") int size,
            @RequestParam(value = "sort_field",     required = false) String sortField,
            @RequestParam(value = "sort_direction", required = false) String sortDirection) {
        return ApiResponse.ok(
                service.list(search, trcoType, page, size, sortField, sortDirection));
    }

    /**
     * Read one contact by primary key. {@code WBE_CONTACTS_NOT_FOUND}
     * when the row doesn't exist in the current tenant &mdash; the
     * same code covers "wrong tenant" so a client cannot probe other
     * tenants' key space.
     */
    @GetMapping("/{trcoKey}")
    public ApiResponse<ContactRow> getOne(
            @PathVariable("trcoKey") @NotBlank String trcoKey) {
        return ApiResponse.ok(service.getOne(trcoKey));
    }

    /**
     * Create a new contact. The service allocates {@code TRCO_KEY}
     * via the legacy {@code wb_get_next_val} funnel (OQ-4) and seeds
     * the {@code PGRO_BSAC_LNKN_INFM} {@code 'B'} + {@code 'S'} rows
     * per legacy parity.
     */
    @PostMapping
    public ApiResponse<ContactRow> create(
            @Valid @RequestBody ContactCreateRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    /**
     * Full-update one contact &mdash; master row + nested upsert /
     * delete passes on addresses, accounts, overseas beneficiaries.
     * Single transaction; the first error rolls back everything.
     */
    @PutMapping("/{trcoKey}")
    public ApiResponse<ContactRow> update(
            @PathVariable("trcoKey") @NotBlank String trcoKey,
            @Valid @RequestBody ContactUpdateRequest request) {
        return ApiResponse.ok(service.update(trcoKey, request));
    }

    /**
     * Soft-delete one contact (sets {@code TRCO_STTS = 'C'}).
     * Throws {@code WBE1190} when the contact is still referenced by
     * any {@code ACCT_TRNS_RCV_PAY_INFM} row (OQ-2).
     */
    @DeleteMapping("/{trcoKey}")
    public ApiResponse<Void> delete(
            @PathVariable("trcoKey") @NotBlank String trcoKey) {
        service.softDelete(trcoKey);
        return ApiResponse.ok(null);
    }

    /**
     * Bulk soft-delete &mdash; mirrors legacy {@code comm_0184_001_u001}.
     * Per-key in-use guard runs inside one transaction; the first
     * offending key throws {@code WBE1190} and rolls back any earlier
     * flips in the same call (OQ-2).
     */
    @PostMapping("/bulk-delete")
    public ApiResponse<Void> bulkDelete(
            @Valid @RequestBody ContactBulkDeleteRequest request) {
        service.bulkSoftDelete(request.trcoKeys());
        return ApiResponse.ok(null);
    }

    /**
     * Excel export of the live contact list — shared common pipeline
     * ({@link ExcelExportService}). Mirrors legacy
     * {@code comm_0184_001_r006_excel} (the Download &rarr; Local /
     * Overseas Contacts dropdown). The request body carries the
     * provider-selecting {@code screenId}
     * ({@code settings-contacts-local} / {@code settings-contacts-overseas}),
     * the column set, and optional {@code filters.keyword}; the
     * {@code ContactsExportProvider} re-runs {@link ContactsService#listForExport}
     * so the export shares the list's tenant scope + active-only +
     * type + keyword filtering. Streams XLSX bytes (not JSON), so it
     * returns {@code void} and writes straight to the response.
     */
    @PostMapping("/export")
    public void export(
            @Valid @RequestBody ExcelExportRequest request,
            HttpServletResponse response) {
        excelExportService.exportTo(response, request);
    }

    /**
     * Legacy-parity template-populated Excel download &mdash; the
     * Settings &rarr; Contacts &rarr; Download &rarr; Local / Overseas
     * Contacts dropdown. Ports legacy
     * {@code comm_0184_001_r006_excel_view.jsp}: loads the styled locale
     * template from the backend classpath, populates one row per bank
     * account (Local) or per beneficiary (Overseas) preserving all
     * template formatting, and streams
     * {@code WeCMS_{Local|Overseas}_Contacts_download_{LANG}.xlsx}.
     *
     * <p>Distinct from {@link #export} (the generic
     * {@code ExcelExportService} grid export, which is retained
     * unchanged): this is the template-based generated report. Streams
     * XLSX bytes, so it returns {@code ResponseEntity} rather than the
     * {@code ApiResponse} envelope (the sanctioned binary-download
     * exception).
     *
     * @param type {@code 'L'} (Local) or {@code 'O'} (Overseas)
     * @param lang optional UI-locale hint (EN/KO/VI; KM/other → EN). When
     *             omitted the JWT {@code langCd} is used.
     */
    @GetMapping("/report")
    public ResponseEntity<ByteArrayResource> downloadReport(
            @RequestParam("type")
            @Pattern(regexp = "[LO]", message = "type: must be 'L' (Local) or 'O' (Overseas)")
            String type,
            @RequestParam(value = "lang", required = false) String lang) {
        ContactsDownloadService.GeneratedReport report = downloadService.generate(type, lang);
        ContentDisposition cd = ContentDisposition.builder("attachment")
                .filename(report.fileName(), StandardCharsets.UTF_8)
                .build();
        ByteArrayResource body = new ByteArrayResource(report.content());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(body.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Access-Control-Expose-Headers", HttpHeaders.CONTENT_DISPOSITION)
                .body(body);
    }

    /**
     * Bulk import from Excel upload. Processes each row individually so
     * a failure on one row does not roll back successfully imported rows.
     * Always returns HTTP 200 — partial-import results are encoded in
     * {@link ContactBulkImportResponse#errors()}.
     *
     * <p>Capped at 500 rows per call ({@link ContactBulkImportRequest}
     * bean validation). The frontend client-side parser already validates
     * and rejects structurally invalid rows before calling this endpoint.
     */
    @PostMapping("/excel/import")
    public ApiResponse<ContactBulkImportResponse> excelImport(
            @Valid @RequestBody ContactBulkImportRequest request) {
        return ApiResponse.ok(importService.bulkImport(request));
    }

    /**
     * Preflight check &mdash; given a batch of parsed Excel rows, return
     * which {@code hand_trco_key} / {@code trco_tax_id} values already
     * exist as an active contact in the tenant. The frontend uses the
     * result to red-flag duplicate rows in the preview table BEFORE the
     * user clicks Upload, so the case "Success: 38 imported, 3 failed
     * (already exists)" surfaces as preview errors instead of a
     * post-upload dialog.
     *
     * <p>Mirrors legacy {@code comm_0184_007_r001_act.jsp:401-450 /
     * 567-630} which calls {@code getContact()} + {@code getTaxId()}
     * per row at parse time and stamps {@code ERR_FLD} /
     * {@code ERR_FLD_CAUS} into the returned record.
     */
    @PostMapping("/excel/preflight")
    public ApiResponse<ContactsExcelPreflightResponse> excelPreflight(
            @Valid @RequestBody ContactsExcelPreflightRequest request) {
        return ApiResponse.ok(service.preflightExcelImport(request));
    }

    /**
     * Bulk recipient resolution for the Local-Contact Excel-upload
     * preview &mdash; one round-trip resolves every parsed row's
     * bank-name &rarr; ({@code NAPAS_BANK_CD} / {@code CITAD_BANK_CD})
     * AND fires the firm-banking inquiry for NAPAS-VND rows so the
     * preview table shows the Recipient column populated before the
     * user clicks Upload.
     *
     * <p>Mirrors the per-row work in legacy
     * {@code comm_0184_007_r001_act.jsp:475-533}: bank cell &rarr;
     * {@code TB_FIRM_BANK_CODE.ENG_ABRVAT_NM} match-count routing +
     * {@code FIRM_PROC.inquiryAccount(...)} for NAPAS-VND.
     */
    @PostMapping("/excel/resolve-recipients")
    public ApiResponse<ContactsExcelResolveRecipientsResponse> excelResolveRecipients(
            @Valid @RequestBody ContactsExcelResolveRecipientsRequest request) {
        return ApiResponse.ok(service.resolveExcelImportRecipients(request));
    }

    /**
     * Batch-fetch contacts by a list of user-supplied beneficiary codes
     * ({@code HAND_TRCO_KEY}). Designed for the overseas batch-payment
     * Excel preview — the frontend collects all unique codes from the
     * parsed sheet and resolves them in one round-trip rather than N
     * individual lookups. Codes that don't match any active contact in
     * the current tenant are silently omitted from the result.
     *
     * <p>Uses POST + JSON body instead of GET query-string to avoid
     * URL-length limits when an Excel file contains many rows (up to
     * {@code OverseasPaymentExcelService#MAX_ROWS} = 500 unique codes).
     */
    @PostMapping("/by-codes")
    public ApiResponse<List<ContactRow>> getByBeneficiaryCodes(
            @RequestBody @Valid ContactsByCodesRequest request) {
        return ApiResponse.ok(service.getByBeneficiaryCodes(request.codes()));
    }

    /**
     * NAPAS + CITAD bank list for the Add / Edit Contact dialog's
     * bank-picker autocomplete. Mirrors legacy
     * {@code comm_0109_001_r002}. Global dictionary — not tenant-scoped.
     */
    @GetMapping("/banks")
    public ApiResponse<ContactBankListResponse> listBanks() {
        return ApiResponse.ok(service.listBanks());
    }

    /**
     * Lookup Recipient — stub today (deterministic mock keyed off the
     * account-number suffix). Will swap to the real firm-banking
     * scraping call when that integration lands. Mirrors legacy
     * {@code comm_1003_001}.
     */
    @PostMapping("/lookup-recipient")
    public ApiResponse<ContactLookupRecipientResponse> lookupRecipient(
            @Valid @RequestBody ContactLookupRecipientRequest request) {
        return ApiResponse.ok(service.lookupRecipient(request));
    }

    /* =================================================================
     *  Attachments (TRCO_INFO_FILE — V98/V99)
     * ================================================================= */

    /**
     * List the attachments for one contact. Tenant scope is enforced
     * via the parent contact lookup &mdash; the file table carries no
     * {@code BIZ_KEY} column.
     */
    @GetMapping("/{trcoKey}/files")
    public ApiResponse<ContactFileListResponse> listFiles(
            @PathVariable("trcoKey") @NotBlank String trcoKey) {
        return ApiResponse.ok(new ContactFileListResponse(fileService.list(trcoKey)));
    }

    /**
     * Upload one attachment. Multipart field name is {@code file}.
     * Service caps the size at 10 MB and the extension at the legacy
     * allow-list (JPG/JPEG/PNG/GIF/PDF/DOC/DOCX/XLS/XLSX/PPT/PPTX).
     */
    @PostMapping(value = "/{trcoKey}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ContactFileRow> uploadFile(
            @PathVariable("trcoKey") @NotBlank String trcoKey,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(fileService.upload(trcoKey, file));
    }

    /**
     * Download one attachment. Streams the raw bytes under the
     * original filename. Tenant scope is enforced via the parent
     * contact lookup.
     */
    @GetMapping("/{trcoKey}/files/{fileId}")
    public ResponseEntity<InputStreamResource> downloadFile(
            @PathVariable("trcoKey") @NotBlank String trcoKey,
            @PathVariable("fileId")  @NotBlank String fileId) throws IOException {
        DownloadResource resolved = fileService.openForDownload(trcoKey, fileId);
        InputStream stream = Files.newInputStream(resolved.path());

        ContentDisposition cd = ContentDisposition.builder("attachment")
                .filename(resolved.orgFileNm(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(resolved.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Access-Control-Expose-Headers", HttpHeaders.CONTENT_DISPOSITION)
                .body(new InputStreamResource(stream));
    }

    /**
     * Delete one attachment (DB row + physical file). Tenant scope is
     * enforced via the parent contact lookup. Missing row &rarr;
     * {@code WBE_CONTACTS_FILE_NOT_FOUND}.
     */
    @DeleteMapping("/{trcoKey}/files/{fileId}")
    public ApiResponse<Void> deleteFile(
            @PathVariable("trcoKey") @NotBlank String trcoKey,
            @PathVariable("fileId")  @NotBlank String fileId) {
        fileService.delete(trcoKey, fileId);
        return ApiResponse.ok(null);
    }
}
