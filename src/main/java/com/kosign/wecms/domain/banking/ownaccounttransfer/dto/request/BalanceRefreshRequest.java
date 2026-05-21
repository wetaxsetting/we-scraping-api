package com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body for {@code POST /api/v1/banking/own-account-transfer/balance-refresh}.
 * Mirrors legacy {@code WSVC.trns_0001_001_u001.xml} field-for-field
 * with bean-validation lifted from {@code _u001_act.jsp:28-41}:
 * <ul>
 *   <li>{@code bank_cd} / {@code acct_no} / {@code acct_bal_inq_stts}
 *       are required.</li>
 *   <li>{@code bal} / {@code real_amt} are optional; non-negative when
 *       supplied ({@code @DecimalMin("0")}).</li>
 *   <li>{@code ccy_cd} is optional on the wire but the service rejects
 *       any value other than {@code VND} — the feature is VND-only
 *       (OQ-A).</li>
 * </ul>
 *
 * <p>The composite {@code (BIZ_KEY, bank_cd, acct_no)} key is resolved
 * by the service: {@code BIZ_KEY} always comes from {@code TenantContext}.
 * Frontend MUST NOT send a {@code biz_key} field — it is silently
 * ignored by Jackson (no such property on the record).
 *
 * <p>Wire format is lowercase snake_case via the global Jackson naming
 * strategy. Java field names stay camelCase.
 */
public record BalanceRefreshRequest(

        @NotBlank
        @Pattern(regexp = "\\d{8}",
                 message = "{validation.own_account_transfer.bank_cd}")
        String bankCd,

        @NotBlank @Size(max = 100)
        String acctNo,

        @DecimalMin(value = "0", message = "{validation.own_account_transfer.amount_non_negative}")
        BigDecimal bal,

        @DecimalMin(value = "0", message = "{validation.own_account_transfer.amount_non_negative}")
        BigDecimal realAmt,

        @NotBlank @Size(max = 20)
        String acctBalInqStts,

        @Size(max = 255)
        String acctBalInqMsg,

        @Pattern(regexp = "[A-Z]{3}",
                 message = "{validation.own_account_transfer.ccy_cd}")
        String ccyCd

) { }
