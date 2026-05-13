package com.kosign.wecms.domain.payment.internaltransfer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for {@code POST /api/v1/payment/internal-transfer}.
 * Mirrors legacy {@code WSVC.pymn_0009_002_c001.xml:in}.
 *
 * <p>Validation contract (server is the authority — Zod on the
 * frontend is UX-only):
 * <ul>
 *   <li>{@code titl} — required, ≤ 100 chars (legacy
 *       {@code ValidationHelper.validateRequired(TITL)} at
 *       {@code pymn_0009_002_c001_act.jsp:23}).</li>
 *   <li>{@code bankCd}, {@code acctNo} — required (legacy lines 34-35).</li>
 *   <li>{@code ccyCd}, {@code trnfCcyCd} — required + 3-char ISO
 *       currency (legacy lines 36-37 + {@code validateCcyCd}).</li>
 *   <li>{@code lines} — must contain at least one entry; each entry
 *       validated through {@link TransferLineInput} via {@link Valid}.</li>
 * </ul>
 *
 * <p>FILE_DATA multipart is intentionally NOT a property on this record
 * — multipart parts arrive as a separate {@code MultipartFile} parameter
 * on the controller method.
 */
public record CreateScheduledTransferRequest(

        @NotBlank
        @Size(max = 100)
        String titl,

        @NotBlank
        @Size(max = 20)
        String bankCd,

        @NotBlank
        @Size(max = 100)
        String acctNo,

        @NotBlank
        @Size(min = 3, max = 3)
        @Pattern(regexp = "^[A-Z]{3}$",
                 message = "ccy_cd: must be a 3-letter ISO 4217 currency code")
        String ccyCd,

        @NotBlank
        @Size(min = 3, max = 3)
        @Pattern(regexp = "^[A-Z]{3}$",
                 message = "trnf_ccy_cd: must be a 3-letter ISO 4217 currency code")
        String trnfCcyCd,

        @NotEmpty
        @Valid
        List<TransferLineInput> lines) { }
