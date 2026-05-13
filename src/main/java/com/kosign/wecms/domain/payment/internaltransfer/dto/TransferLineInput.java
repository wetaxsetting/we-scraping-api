package com.kosign.wecms.domain.payment.internaltransfer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * One line in a {@link CreateScheduledTransferRequest}. Mirrors legacy
 * {@code WSVC.pymn_0009_002_c001.xml:rule_Sub_0} (REC sub-record).
 *
 * <p>Per-field validation:
 * <ul>
 *   <li>{@code tranAmt} — required, must be &gt; 0.</li>
 *   <li>{@code fincMdaNo} — required (legacy
 *       {@code ValidationHelper.validateRequired(REC, FINC_MDA_NO)} at
 *       {@code pymn_0009_002_c001_act.jsp:30-31}).</li>
 *   <li>{@code antDivCd} — must be one of the recognised network codes
 *       when provided.</li>
 * </ul>
 */
public record TransferLineInput(

        @NotNull
        @DecimalMin(value = "0.000001", inclusive = true)
        BigDecimal tranAmt,

        @Size(max = 5)
        @Pattern(regexp = "^(N|NAPAS|INTER|C|CITAD|S|SWIFT)?$",
                 message = "ant_div_cd: must be one of N/NAPAS/INTER/C/CITAD/S/SWIFT")
        String antDivCd,

        @Size(max = 100)
        String rcptNm,

        @Size(max = 15)
        String tarBankCd,

        @NotBlank
        @Size(max = 100)
        String fincMdaNo,

        @Size(max = 200)
        String tranUsag,

        @Size(max = 20)
        String trcoKey,

        @Size(max = 20)
        String handTrcoKey,

        @Size(max = 50)
        String docNo,

        @Size(max = 255)
        String note,

        @Size(max = 200)
        String citadBankCd,

        @Size(max = 200)
        String trcoCmnm,

        @Size(max = 200)
        String trcoCmenm,

        @Size(max = 10)
        String chartAcctSeqNo,

        @Size(max = 20)
        String chartAcctCd,

        @Size(max = 3)
        String trnfCcyCd,

        @Size(max = 3)
        String depoCcyCd) { }
