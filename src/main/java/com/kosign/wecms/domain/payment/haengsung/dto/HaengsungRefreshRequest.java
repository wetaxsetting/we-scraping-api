package com.kosign.wecms.domain.payment.haengsung.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/v1/payments/haengsung/refresh}.
 *
 * <p>Wire shape (lowercase snake_case via the global Jackson strategy):
 * <pre>{ "payment_type": "VENDOR", "period_date": "20260628" }</pre>
 *
 * <ul>
 *   <li>{@code payment_type} — one of {@code VENDOR|EXPENSE|PAYROLL|OVERSEAS|ALL}
 *       (case-insensitive; parsed in the service via
 *       {@link com.kosign.wecms.domain.payment.haengsung.HaengsungPaymentType#fromWire}).</li>
 *   <li>{@code period_date} — {@code YYYYMMDD} (single day) or
 *       {@code YYYYMMDD-YYYYMMDD} (inclusive range), filtered against Oracle
 *       {@code TB_TRNS_MAST.FILE_DATE}.</li>
 * </ul>
 */
public record HaengsungRefreshRequest(

        @NotBlank
        @Pattern(regexp = "(?i)VENDOR|EXPENSE|PAYROLL|OVERSEAS|ALL",
                 message = "payment_type must be VENDOR, EXPENSE, PAYROLL, OVERSEAS or ALL")
        String paymentType,

        @NotBlank
        @Pattern(regexp = "\\d{8}(-\\d{8})?",
                 message = "period_date must be YYYYMMDD or YYYYMMDD-YYYYMMDD")
        String periodDate

) { }
