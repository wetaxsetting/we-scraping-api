package com.kosign.wecms.domain.weinvoice.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /uploads/{upload_id}/adopt} — "Thêm MST … và nhập" (UI Spec 3.2): add the
 * foreign MST (status {@code need_password}) and import the files that belong to it.
 */
public record AdoptRequest(@NotBlank String companyMst) {
}
