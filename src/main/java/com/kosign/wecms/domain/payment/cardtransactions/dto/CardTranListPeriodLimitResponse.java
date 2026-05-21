package com.kosign.wecms.domain.payment.cardtransactions.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Card Transactions &gt; Update &mdash; per-bank period-limit lookup
 * response. Mirrors legacy
 * {@code nxisas_cardTranList_r001_act.jsp:124-132} which builds
 * {@code REC_OUT[i] = inputRow ∪ ido.row} from
 * {@code BANK_INFM_DETL_R007}.
 *
 * <p>Each row carries:
 * <ul>
 *   <li>{@code bankCd} / {@code bankCertSeq} &mdash; echoed from the
 *       request so the caller can join.</li>
 *   <li>{@code cont1} &mdash; period count ({@code "3"}, {@code "30"},
 *       {@code "90"}). Defaults to {@code "90"} when the bank has no
 *       {@code BANK_DETL_INFO_CD='26'} row.</li>
 *   <li>{@code cont2} &mdash; period unit ({@code "days"},
 *       {@code "months"}, {@code "years"}). Defaults to {@code "days"}
 *       when the bank has no {@code BANK_DETL_INFO_CD='27'} row.</li>
 * </ul>
 *
 * <p>The popup picks the LONGEST cap across all rows for the range
 * guard.
 */
public record CardTranListPeriodLimitResponse(
        List<Row> recOut) {

    public record Row(
            String bankCd,
            BigDecimal bankCertSeq,
            String cont1,
            String cont2) {
    }
}
