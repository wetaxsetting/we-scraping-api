package com.kosign.wecms.domain.payment.haengsung.cardaccount;

import java.math.BigDecimal;

/**
 * Mapped row for one HAENGSUNG Oracle {@code TB_CARD_APPROVAL} insert (Step 7 —
 * legacy {@code ERP_0100_003.CreateTbCardApproval} + {@code TB_CARD_APPROVAL_C002}).
 * Built by {@link HaengsungCardAccountSyncService} from a {@link CardApprovalSyncRow}.
 *
 * <p>Legacy override notes (kept verbatim): {@code CARD_NM} is set from the
 * card-holder name ({@code CARD_HLDR_NM}), NOT the source {@code CARD_TRNS_HSTR.CARD_NM};
 * {@code PURCHASE_ST} from {@code PRCH_STTS}. Columns present on
 * {@code TB_CARD_APPROVAL} but not produced by the read/override
 * ({@code USAGE}/{@code PURPOSE}/{@code VAS}/{@code APPR_SEQNO}/
 * {@code GDT_INVOICE_INFO}/{@code ERP_GET_DTTM}) are written null by the
 * repository (legacy {@code putAll} leaves them unset); {@code ERP_GET_ST='0'}.
 */
public record OracleCardApproval(
        String bizNo,
        String bankCd,
        String cardNo,
        String apprDt,
        String apprTm,
        String authNo,
        String cardNm,        // legacy override: = CARD_HLDR_NM
        String merchNm,
        String ccyCd,
        BigDecimal apprAmt,
        String purchaseSt,    // = PRCH_STTS
        String city,
        String lastInqDttm,
        String cmsPutDttm) {
}
