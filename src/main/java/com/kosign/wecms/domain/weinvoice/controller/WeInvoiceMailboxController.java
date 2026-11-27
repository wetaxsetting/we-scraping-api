package com.kosign.wecms.domain.weinvoice.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.weinvoice.dto.MailboxItem;
import com.kosign.wecms.domain.weinvoice.dto.MailboxSaveRequest;
import com.kosign.wecms.domain.weinvoice.dto.MicrosoftAuthorizeResponse;
import com.kosign.wecms.domain.weinvoice.dto.RequestAccepted;
import com.kosign.wecms.domain.weinvoice.service.MailboxService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * WeInvoice screen 08 — mailbox connections (Gmail app password, Microsoft sign-in, company IMAP).
 *
 * <p>Every call is scoped to the signed-in user's own mailboxes and tax codes; secrets are write-only.
 * {@code GET /microsoft/callback} is the one endpoint without a JWT ({@code SecurityConfig} permitAll):
 * the browser comes back from Microsoft, and the owner is taken from the signed {@code state}.
 * Record: docs/features/addon-service/weinvoice/weinvoice-mailbox-connections-2026-11-12.md.
 */
@RestController
@RequestMapping(WeInvoiceMailboxController.BASE)
@RequiredArgsConstructor
@Validated
public class WeInvoiceMailboxController {

    static final String BASE = "/api/v1/weinvoice/mailboxes";
    /** Public (permitAll) path — keep {@code SecurityConfig} in sync. */
    public static final String MICROSOFT_CALLBACK_PATH = BASE + "/microsoft/callback";

    private final MailboxService mailboxes;

    @GetMapping
    public ApiResponse<List<MailboxItem>> list() {
        return ApiResponse.ok(mailboxes.list());
    }

    /** Logs in once through the engine; saves only an accepted login (WBE3029 / WBE3030 / WBE3036 otherwise). */
    @PostMapping
    public ApiResponse<MailboxItem> create(@Valid @RequestBody MailboxSaveRequest request) {
        return ApiResponse.ok(mailboxes.create(request));
    }

    /** Same body; blank password keeps the stored secret (still re-tested). */
    @PutMapping("/{id}")
    public ApiResponse<MailboxItem> update(@PathVariable("id") String id, @Valid @RequestBody MailboxSaveRequest request) {
        return ApiResponse.ok(mailboxes.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") String id) {
        mailboxes.delete(id);
        return ApiResponse.ok(null);
    }

    /** Queues engine request MAIL_SCAN; poll {@code GET /requests/{request_id}}. */
    @PostMapping("/{id}/scan")
    public ApiResponse<RequestAccepted> scan(@PathVariable("id") String id) {
        return ApiResponse.ok(mailboxes.scan(id));
    }

    @GetMapping("/microsoft/authorize")
    public ApiResponse<MicrosoftAuthorizeResponse> microsoftAuthorize(
            @RequestParam(name = "company_scope", required = false) String companyScope,
            @RequestParam(name = "company_mst", required = false) String companyMst,
            @RequestParam(name = "folders", required = false) List<String> folders) {
        return ApiResponse.ok(mailboxes.microsoftAuthorize(companyScope, companyMst, folders));
    }

    /** No JWT. Always a 302 back to the web app: {@code ?ms=ok} or {@code ?ms=error&reason=<code>}. */
    @GetMapping("/microsoft/callback")
    public ResponseEntity<Void> microsoftCallback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error) {
        String location = mailboxes.microsoftCallback(code, state, error);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
