package com.kosign.wecms.domain.banking.smartorganizer.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.smartorganizer.dto.ConfirmRequest;
import com.kosign.wecms.domain.banking.smartorganizer.dto.ConfirmResponse;
import com.kosign.wecms.domain.banking.smartorganizer.dto.EligibleTransactionListResponse;
import com.kosign.wecms.domain.banking.smartorganizer.service.SmartOrganizerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Banking &gt; Smart Organizer — eligibility listing + confirm
 * endpoints. Refactor slug {@code smart-organizer}; legacy
 * {@code fnsh_0200_001_r001} + {@code fnsh_0200_001_c001}.
 *
 * <p>Rule CRUD (Organizer Settings popup) lives in a sibling
 * controller at {@code /api/v1/banking/smart-organizer/rules}.
 */
@RestController
@RequestMapping("/api/v1/banking/smart-organizer")
@RequiredArgsConstructor
@Validated
public class SmartOrganizerController {

    private final SmartOrganizerService service;

    /**
     * List eligible transactions for the current tenant in the date
     * range. Sort keys must come from the View Setting registry for
     * {@code smart-organizer} (V162 seed); unknown values resolve to
     * the default ORDER BY ({@code trsc_dt DESC}).
     */
    @GetMapping("/eligible")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
    public ApiResponse<EligibleTransactionListResponse> listEligible(
            @RequestParam("bank_cd")  String bankCd,
            @RequestParam("acct_no")  String acctNo,
            @RequestParam("from_dt")  String fromDt,
            @RequestParam("to_dt")    String toDt,
            @RequestParam(name = "q",          required = false) String q,
            @RequestParam(name = "sort_field", required = false) String sortField,
            @RequestParam(name = "sort_dir",   required = false) String sortDir,
            @RequestParam(name = "page", defaultValue = "1")  int page,
            @RequestParam(name = "size", defaultValue = "15") int size) {
        return ApiResponse.ok(
                service.listEligible(bankCd, acctNo, fromDt, toDt, q, sortField, sortDir, page, size));
    }

    /** Bulk-confirm the rows the user has reviewed (scoped to the
     *  selected account — R11). */
    @PostMapping("/confirm")
    @PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")
    public ApiResponse<ConfirmResponse> confirm(@Valid @RequestBody ConfirmRequest req) {
        return ApiResponse.ok(service.confirm(req));
    }
}
