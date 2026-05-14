package com.kosign.wecms.domain.banking.nxisas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Inline credential payload for the {@code POST /api/v1/banking/scraping/verify}
 * endpoint. Unlike {@link StartScrapeRequest} (which looks up an already
 * persisted {@code BANK_CERT_INFO} row by {@code seqNo}), this request
 * carries the user's typed credentials directly so the Login Credentials
 * "Login" button can verify them with Coocon BEFORE the row is saved —
 * mirrors the legacy WeCMS {@code enter_login_popup} → {@code _v2nxisas.callNxisas}
 * flow, where the bank scrape happens first and persistence only follows
 * a successful account-list response.
 *
 * <p>Persistence happens later via {@code POST /api/v1/login-credentials/bank}
 * once the user has picked accounts from the scrape result.
 */
public record VerifyCredentialsRequest(
        @NotBlank @Size(max = 20)   String bankCd,
        @NotBlank @Size(max = 2000) String loginId,
        @NotBlank @Size(max = 2000) String password,
        /** Sub-type discriminator (0 = Corporate, 1 = Personal,
         *  2 = VCB DigiBiz, etc.). Defaults to 0 server-side. */
        BigDecimal                  nxisasGbSeqNo,
        /** Optional Corporate ID / extra-field input. Maps to legacy
         *  CERT_INFO_DETL_CON2 / NXISAS loginPwd1. */
        @Size(max = 2000)           String extra,
        /** Optional Login Type selector (e.g. "OTP" / "EMAIL" / "SMS" /
         *  "ID"). Defaults to "ID" when null/blank. */
        @Size(max = 2000)           String loginType
) { }
