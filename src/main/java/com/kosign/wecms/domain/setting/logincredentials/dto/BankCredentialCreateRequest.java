package com.kosign.wecms.domain.setting.logincredentials.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Create payload for one BANK_CERT_INFO row under the current tenant.
 * Mirrors the legacy {@code comm_0218_003_c001} contract — one bank
 * credential row per call. Multi-account banks are surfaced as
 * repeated calls (one row per loginId).
 *
 * <p>Distinct from the older
 * {@link com.kosign.wecms.domain.banking.bankcredentials.dto.BankCredentialCreateRequest}
 * which lives under the {@code /api/v1/banking/credentials} route used
 * by the NXISAS scraping spike. This DTO is consumed by the unified
 * {@code /api/v1/login-credentials/bank} endpoint.
 *
 * <p>Per-bank extras (carried over so NXISAS can route the LOGIN call
 * to the right adapter):
 * <ul>
 *   <li>{@code nxisasGbSeqNo} — sub-type discriminator (e.g. 0 =
 *       Corporate Banking, 1 = Personal Banking, 2 = VCB DigiBiz).
 *       Defaults to 0 server-side when null. Persisted in
 *       {@code BANK_CERT_INFO.NXISAS_GB_SEQ_NO}.</li>
 *   <li>{@code extra} / {@code corporateId} — Agribank/MB ask for a
 *       Corporate ID alongside login id; stored in
 *       {@code CERT_INFO_DETL_CON2} (encrypted at rest, legacy
 *       parity).</li>
 *   <li>{@code loginType} — ABBANK/Vietcom Personal carry a Login
 *       Type dropdown value (e.g. "OTP", "EMAIL", "SMS"); stored in
 *       {@code CERT_INFO_DETL_CON3}.</li>
 * </ul>
 */
public record BankCredentialCreateRequest(
        @NotBlank @Size(max = 20)   String bankCd,
        @NotBlank @Size(max = 2000) String loginId,
        @NotBlank @Size(max = 2000) String password,
        /** Sub-type seq_no — null defaults to 0 (Corporate). Carries
         *  the value the user picked in the radio group. */
        BigDecimal                  nxisasGbSeqNo,
        /** Optional Corporate ID / extra-field input. Maps to
         *  CERT_INFO_DETL_CON2. */
        @Size(max = 2000)           String extra,
        /** Optional Login Type selector (OTP / EMAIL / SMS / ...).
         *  Maps to CERT_INFO_DETL_CON3. */
        @Size(max = 2000)           String loginType
) { }
