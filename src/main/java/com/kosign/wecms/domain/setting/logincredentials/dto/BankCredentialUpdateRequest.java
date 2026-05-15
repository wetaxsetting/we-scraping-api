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
        BigDecimal                   nxisasGbSeqNo,
        /** Optional current-password guard. When non-null, the service
         *  decrypts the stored ciphertext and rejects the update with
         *  {@code WBE2091} if the value doesn't match — mirrors the
         *  legacy {@code comm_0218_004_u001_act.jsp:55-57} WHERE-clause
         *  guard that includes {@code decrypt(cert_info_detl_con) = ?}.
         *  Null means "no guard" — kept optional so callers that don't
         *  need the legacy parity (e.g. admin reset flows) can still
         *  PATCH a single field. */
        @Size(max = 2000) String     oldPassword
) { }
