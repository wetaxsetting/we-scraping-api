package com.kosign.wecms.domain.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for {@code PATCH /{id}/approval-line}.
 * Replaces the approver chain on a pending overseas payment batch without
 * touching the OVRS_PYMN_SUMR or EXPT_RESOL_MSTR master rows.
 */
public record ChangeApprovalLineRequest(

        @NotNull
        Long apprLineNo,

        @NotNull
        @Size(min = 1, max = 5)
        @Valid
        List<ApproverInput> approvers
) {
    public record ApproverInput(
            @NotBlank @Size(max = 100) String apprvrId,
            @NotBlank @Size(max = 100) String apprvrNm
    ) {}
}
