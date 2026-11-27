package com.kosign.wecms.domain.weinvoice.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.weinvoice.dto.AdoptRequest;
import com.kosign.wecms.domain.weinvoice.dto.EmailAddressResponse;
import com.kosign.wecms.domain.weinvoice.dto.EmailMessageDetail;
import com.kosign.wecms.domain.weinvoice.dto.EmailMessageItem;
import com.kosign.wecms.domain.weinvoice.dto.RequestAccepted;
import com.kosign.wecms.domain.weinvoice.dto.UploadResponse;
import com.kosign.wecms.domain.weinvoice.dto.WeInvoicePage;
import com.kosign.wecms.domain.weinvoice.service.EmailService;
import com.kosign.wecms.domain.weinvoice.service.UploadService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * WeInvoice screens 07 (invoices from mail) and 09 / 15 (manual upload, "add to the store").
 * Upload files are imported by the engine; {@code GET /uploads/{id}} reports each file's
 * outcome once the engine has processed it.
 */
@RestController
@RequestMapping("/api/v1/weinvoice")
@RequiredArgsConstructor
@Validated
public class WeInvoiceMailUploadController {

    private final EmailService emails;
    private final UploadService uploads;

    @GetMapping("/email/address")
    public ApiResponse<EmailAddressResponse> address(@RequestParam("company_mst") @NotBlank String companyMst) {
        return ApiResponse.ok(emails.address(companyMst));
    }

    @GetMapping("/email/messages")
    public ApiResponse<WeInvoicePage<EmailMessageItem>> messages(
            @RequestParam("company_mst") @NotBlank String companyMst,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "result", required = false) String result,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "0") int size) {
        return ApiResponse.ok(emails.messages(companyMst, from, to, result, page, size));
    }

    @GetMapping("/email/messages/{id}")
    public ApiResponse<EmailMessageDetail> message(@PathVariable("id") String id) {
        return ApiResponse.ok(emails.message(id));
    }

    /** XML, ZIP or PDF files (field {@code files}, repeated) for the working MST. */
    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadResponse> upload(
            @RequestParam("company_mst") @NotBlank String companyMst,
            @RequestPart("files") List<MultipartFile> files) {
        return ApiResponse.ok(uploads.create(companyMst, files));
    }

    @GetMapping("/uploads/{uploadId}")
    public ApiResponse<UploadResponse> getUpload(@PathVariable("uploadId") String uploadId) {
        return ApiResponse.ok(uploads.get(uploadId));
    }

    /** "Thêm MST … và nhập": add the foreign MST (no password yet) and import its files. */
    @PostMapping("/uploads/{uploadId}/adopt")
    public ApiResponse<RequestAccepted> adopt(@PathVariable("uploadId") String uploadId,
                                              @Valid @RequestBody AdoptRequest request) {
        return ApiResponse.ok(uploads.adopt(uploadId, request));
    }

    @DeleteMapping("/uploads/{uploadId}")
    public ApiResponse<Void> deleteUpload(@PathVariable("uploadId") String uploadId) {
        uploads.delete(uploadId);
        return ApiResponse.ok(null);
    }

    /** Acceptance data set (M14); answers {@code WBE3018} unless enabled for the environment. */
    @PostMapping("/test-data/load")
    public ApiResponse<RequestAccepted> loadTestData(@RequestParam("company_mst") @NotBlank String companyMst) {
        return ApiResponse.ok(uploads.loadTestData(companyMst));
    }
}
