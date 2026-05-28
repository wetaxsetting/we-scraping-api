package com.kosign.wecms.domain.setting.logincredentials.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Confirmation payload for the {@code POST /{type}/{seqNo}/reveal}
 * endpoint. The user re-enters the CREDENTIAL's own password (legacy
 * {@code comm_0218_008_r001} parity — {@code decrypt(cert_info_detl_con)
 * = ?}); the server confirms it matches the stored value before
 * returning the decrypted credential. Field name kept as
 * {@code userPassword} for wire compatibility ({@code user_password}).
 * Sized to the credential-password column max (2000), not the shorter
 * account-password cap.
 */
public record RevealRequest(
        @NotBlank @Size(max = 2000) String userPassword
) { }
