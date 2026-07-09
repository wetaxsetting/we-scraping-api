package com.kosign.wecms.domain.payment.haengsung.cardaccount;

import java.math.BigDecimal;

/**
 * Mapped row for one HAENGSUNG Oracle {@code TB_ACCT_HISTORY} insert (Step 7 —
 * legacy {@code ERP_0100_003.CreateTbAcctHistoryData} + {@code TB_ACCT_HISTORY_C002}).
 * Built by {@link HaengsungCardAccountSyncService} from an {@link AcctHistorySyncRow}.
 *
 * <p>The legacy insert copies the read-row columns ({@code putAll}) then overrides
 * {@code BIZ_NO}/{@code CMS_PUT_DTTM}/{@code ERP_GET_ST}. Columns present on
 * {@code TB_ACCT_HISTORY} but NOT in the read projection
 * ({@code BANK_BRANCH}/{@code PURPOSE}/{@code VAS}/{@code USER_MENO}/
 * {@code GDT_INVOICE_INFO}/{@code ERP_GET_DTTM}) are therefore written null —
 * the repository hardcodes those nulls + {@code ERP_GET_ST='0'}, so they are not
 * carried here.
 */
public record OracleAcctHistory(
        String bizNo,
        String bankCd,
        String acctNo,
        String trDt,
        BigDecimal trSeqno,
        String ccyCd,
        BigDecimal trAmt,
        BigDecimal trCurBal,
        String ioGb,
        String acctMemo,
        String lastInqDttm,
        String cmsPutDttm) {
}
