package com.kosign.wecms.domain.weinvoice.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.weinvoice.dto.ExportZipRequest;
import com.kosign.wecms.domain.weinvoice.dto.FetchXmlResult;
import com.kosign.wecms.domain.weinvoice.dto.FileUploadResult;
import com.kosign.wecms.domain.weinvoice.dto.IdsRequest;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceDetail;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceFacets;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceFileItem;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceFilter;
import com.kosign.wecms.domain.weinvoice.dto.InvoiceListResponse;
import com.kosign.wecms.domain.weinvoice.dto.LookupCodeRequest;
import com.kosign.wecms.domain.weinvoice.dto.RequestAccepted;
import com.kosign.wecms.domain.weinvoice.dto.TagAssignRequest;
import com.kosign.wecms.domain.weinvoice.service.InvoiceActionService;
import com.kosign.wecms.domain.weinvoice.service.InvoiceExportService;
import com.kosign.wecms.domain.weinvoice.service.InvoiceFileService;
import com.kosign.wecms.domain.weinvoice.service.InvoiceQueryService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * WeInvoice screens 04 (purchase / sales list) and 05 (invoice detail).
 *
 * <p>Every call names the working MST ({@code company_mst}, or the invoice id, whose MST is
 * checked); the owner is the JWT user. Filters are snake_case query parameters; list values
 * ({@code statuses}, {@code tag_ids}, {@code exclude_tax_ids}) accept repeated parameters or a
 * comma-separated value. Errors follow the house envelope ({@code WBE30xx} for WeInvoice rules).
 * Binary answers (Excel, ZIP, a file) are written straight to the response.
 */
@RestController
@RequestMapping("/api/v1/weinvoice")
@RequiredArgsConstructor
@Validated
public class WeInvoiceInvoiceController {

    private final InvoiceQueryService queries;
    private final InvoiceFileService files;
    private final InvoiceActionService actions;
    private final InvoiceExportService exports;

    @GetMapping("/invoices")
    public ApiResponse<InvoiceListResponse> list(
            @RequestParam("company_mst") @NotBlank String companyMst,
            @RequestParam(value = "direction", required = false) String direction,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "xml", required = false) String xml,
            @RequestParam(value = "pdf", required = false) String pdf,
            @RequestParam(value = "statuses", required = false) List<String> statuses,
            @RequestParam(value = "kind", required = false) String kind,
            @RequestParam(value = "tag_ids", required = false) List<String> tagIds,
            @RequestParam(value = "provider", required = false) String provider,
            @RequestParam(value = "exclude_tax_ids", required = false) List<String> excludeTaxIds,
            @RequestParam(value = "sort_field", required = false) String sortField,
            @RequestParam(value = "sort_direction", required = false) String sortDirection,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "0") int size) {
        InvoiceFilter filter = new InvoiceFilter(companyMst, direction, from, to, keyword, xml, pdf, statuses, kind,
                tagIds, provider, excludeTaxIds, null, sortField, sortDirection);
        return ApiResponse.ok(queries.list(filter, page, size));
    }

    /** Counts behind the "issued through" / status / kind filters of one MST, direction and period. */
    @GetMapping("/invoices/facets")
    public ApiResponse<InvoiceFacets> facets(
            @RequestParam("company_mst") @NotBlank String companyMst,
            @RequestParam(value = "direction", required = false) String direction,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        return ApiResponse.ok(queries.facets(new InvoiceFilter(companyMst, direction, from, to, null, null, null, null,
                null, null, null, null, null, null, null)));
    }

    /** Every id matching the list filter — "select all" across pages. */
    @GetMapping("/invoices/ids")
    public ApiResponse<List<String>> ids(
            @RequestParam("company_mst") @NotBlank String companyMst,
            @RequestParam(value = "direction", required = false) String direction,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "xml", required = false) String xml,
            @RequestParam(value = "pdf", required = false) String pdf,
            @RequestParam(value = "statuses", required = false) List<String> statuses,
            @RequestParam(value = "kind", required = false) String kind,
            @RequestParam(value = "tag_ids", required = false) List<String> tagIds,
            @RequestParam(value = "provider", required = false) String provider,
            @RequestParam(value = "exclude_tax_ids", required = false) List<String> excludeTaxIds) {
        return ApiResponse.ok(queries.ids(new InvoiceFilter(companyMst, direction, from, to, keyword, xml, pdf, statuses,
                kind, tagIds, provider, excludeTaxIds, null, null, null)));
    }

    /** "Lấy XML còn thiếu" — queues the coded invoices without XML (engine request). */
    @PostMapping("/invoices/fetch-missing-xml")
    public ApiResponse<FetchXmlResult> fetchMissingXml(@Valid @RequestBody IdsRequest request) {
        return ApiResponse.ok(actions.fetchMissingXml(request));
    }

    /** Excel of the filtered list or of {@code ids}; body = the list filter. */
    @PostMapping("/invoices/export/excel")
    public void exportExcel(@RequestBody InvoiceFilter filter, HttpServletResponse response) {
        exports.excel(filter, response);
    }

    /** ZIP of XML / PDF of the selected invoices, streamed. */
    @PostMapping("/invoices/export/zip")
    public void exportZip(@Valid @RequestBody ExportZipRequest request, HttpServletResponse response) {
        exports.zip(request, response);
    }

    /** Adds or removes one tag on many invoices; returns how many invoices were targeted. */
    @PutMapping("/invoices/tags")
    public ApiResponse<Map<String, Integer>> tags(@Valid @RequestBody TagAssignRequest request) {
        return ApiResponse.ok(Map.of("count", actions.assignTag(request)));
    }

    @GetMapping("/invoices/{id}")
    public ApiResponse<InvoiceDetail> detail(@PathVariable("id") String id) {
        return ApiResponse.ok(queries.detail(id));
    }

    @GetMapping("/invoices/{id}/files")
    public ApiResponse<List<InvoiceFileItem>> files(@PathVariable("id") String id) {
        return ApiResponse.ok(files.list(id));
    }

    @GetMapping("/invoices/{id}/files/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable("id") String id, @PathVariable("fileId") String fileId) {
        return files.download(id, fileId);
    }

    /**
     * Adds a file to the record. {@code kind} = {@code xml} / {@code pdf} / {@code attachment};
     * {@code confirm} repeats the answers to a previous {@code needs_confirm}
     * ({@code attach_anyway}, {@code replace}).
     */
    @PostMapping(value = "/invoices/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileUploadResult> upload(
            @PathVariable("id") String id,
            @RequestParam("kind") @NotBlank String kind,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "confirm", required = false) List<String> confirm) {
        return ApiResponse.ok(files.upload(id, kind, file, confirm));
    }

    @DeleteMapping("/invoices/{id}/files/{fileId}")
    public ApiResponse<Void> deleteFile(@PathVariable("id") String id, @PathVariable("fileId") String fileId) {
        files.delete(id, fileId);
        return ApiResponse.ok(null);
    }

    /** Fetch the original PDF now (engine request: open portal → submit code → download). */
    @PostMapping("/invoices/{id}/original-pdf")
    public ApiResponse<RequestAccepted> originalPdf(@PathVariable("id") String id) {
        return ApiResponse.ok(actions.fetchOriginalPdf(id));
    }

    /** Lookup code typed by the user (code or whole link); queues a PDF fetch with it. */
    @PutMapping("/invoices/{id}/lookup-code")
    public ApiResponse<RequestAccepted> lookupCode(@PathVariable("id") String id,
                                                   @Valid @RequestBody LookupCodeRequest request) {
        return ApiResponse.ok(actions.setLookupCode(id, request));
    }
}
