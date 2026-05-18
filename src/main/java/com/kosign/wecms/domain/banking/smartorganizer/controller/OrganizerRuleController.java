package com.kosign.wecms.domain.banking.smartorganizer.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.smartorganizer.dto.OrganizerRuleListResponse;
import com.kosign.wecms.domain.banking.smartorganizer.dto.OrganizerRuleReorderRequest;
import com.kosign.wecms.domain.banking.smartorganizer.dto.OrganizerRuleRow;
import com.kosign.wecms.domain.banking.smartorganizer.dto.OrganizerRuleSaveRequest;
import com.kosign.wecms.domain.banking.smartorganizer.service.OrganizerRuleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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
 * Banking &gt; Smart Organizer &gt; Organizer Settings popup —
 * rule CRUD + reorder. Refactor slug {@code smart-organizer};
 * legacy {@code fnsh_0200_002_*} preserved only in citations.
 */
@RestController
@RequestMapping("/api/v1/banking/smart-organizer/rules")
@RequiredArgsConstructor
@Validated
public class OrganizerRuleController {

    private final OrganizerRuleService service;

    @GetMapping
    public ApiResponse<OrganizerRuleListResponse> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<OrganizerRuleRow> create(@Valid @RequestBody OrganizerRuleSaveRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{rmrk_seq_no}")
    public ApiResponse<OrganizerRuleRow> update(
            @PathVariable("rmrk_seq_no") @Positive Long rmrkSeqNo,
            @Valid @RequestBody OrganizerRuleSaveRequest req) {
        return ApiResponse.ok(service.update(rmrkSeqNo, req));
    }

    @DeleteMapping("/{rmrk_seq_no}")
    public ApiResponse<Void> delete(@PathVariable("rmrk_seq_no") @Positive Long rmrkSeqNo) {
        service.delete(rmrkSeqNo);
        return ApiResponse.ok(null);
    }

    @PostMapping("/reorder")
    public ApiResponse<Void> reorder(@Valid @RequestBody OrganizerRuleReorderRequest req) {
        service.reorder(req);
        return ApiResponse.ok(null);
    }
}
