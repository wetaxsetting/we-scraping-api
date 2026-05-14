package com.kosign.wecms.domain.setting.logincredentials.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create OR Update payload for the GDT credential — the legacy
 * {@code comm_0218_007_c001} action is upsert-shaped (writes a new
 * row when SEQ_NO is unset, updates the existing row otherwise). The
 * refactor splits the two paths at the controller (POST vs PUT) but
 * shares one DTO since the fields are identical.
 *
 * <p>Both {@code loginId} and {@code password} are required on Create;
 * Update accepts either (a partial update with only one field changed
 * is legal — null means "leave unchanged"). Validation runs at the
 * controller layer; the service-level guard catches the all-null case
 * on Update.
 */
public record GdtCredentialRequest(
        @NotBlank @Size(max = 2000) String loginId,
        @NotBlank @Size(max = 2000) String password,
        @Size(max = 2000)           String extra
) { }
