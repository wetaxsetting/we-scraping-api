package com.kosign.wecms.domain.setting.logincredentials.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Wire body for
 * {@code POST /api/v1/login-credentials/bank/{seqNo}/verify-update}.
 *
 * <p>Edit flow's two-phase commit: the frontend submits ONLY the new
 * password here. The service resolves the rest of the credential
 * context (bank_cd, login_id, extra, login_type, nxisas_gb_seq_no)
 * from the existing BANK_CERT_INFO row, runs an inline NXISAS verify
 * against the bank with that context + the new password, and returns
 * a scrape session. The existing password is left untouched until the
 * frontend gets a COMPLETED result and follows up with the standard
 * {@code PUT /api/v1/login-credentials/bank/{seqNo}}.
 *
 * <p>Distinct from {@link BankCredentialUpdateRequest} (which writes
 * to the DB directly) — this endpoint never persists; it only proves
 * the new password works against the bank.
 */
public record VerifyBankPasswordRequest(
        @NotBlank @Size(max = 2000) String password
) { }
