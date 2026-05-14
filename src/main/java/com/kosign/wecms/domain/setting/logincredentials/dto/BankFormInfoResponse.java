package com.kosign.wecms.domain.setting.logincredentials.dto;

import java.util.List;

/**
 * Response shape for {@code GET /api/v1/login-credentials/banks/{bankCd}/form-info}.
 * One {@link BankFormInfoSubType} per {@code SEQ_NO} value the bank
 * has configured. The legacy filter that hides {@code "Personal Banking"}
 * stays on the frontend side (mirroring {@code comm_0218_nxisas.ts:443}).
 */
public record BankFormInfoResponse(
        String                     bankCd,
        List<BankFormInfoSubType>  subTypes
) { }
