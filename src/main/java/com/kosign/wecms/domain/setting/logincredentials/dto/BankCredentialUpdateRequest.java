package com.kosign.wecms.domain.setting.logincredentials.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Update payload — every field is optional (null means "leave
 * unchanged"). Mirrors legacy {@code comm_0218_006_u001} /
 * {@code _009_u001} behaviour where the pencil-icon dialog lets the
 * user change any subset of fields independently.
 *
 * <p>Per-bank extras follow the same persistence contract as
 * {@link BankCredentialCreateRequest}: {@code extra} maps to
 * {@code CERT_INFO_DETL_CON2}, {@code loginType} to
 * {@code CERT_INFO_DETL_CON3}, {@code nxisasGbSeqNo} to
 * {@code NXISAS_GB_SEQ_NO}.
 */
public record BankCredentialUpdateRequest(
        @Size(max = 2000) String     loginId,
        @Size(max = 2000) String     password,
        @Size(max = 2000) String     extra,
        @Size(max = 2000) String     loginType,
        BigDecimal                   nxisasGbSeqNo
) { }
