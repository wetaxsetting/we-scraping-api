package com.kosign.wecms.domain.setting.contacts.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for {@code POST /api/v1/settings/contacts/excel/resolve-recipients}.
 *
 * <p>Carries the parsed Local-Contact Excel rows that need a server-side
 * bank-name &rarr; ({@code NAPAS_BANK_CD} / {@code CITAD_BANK_CD})
 * resolution + (for NAPAS-VND rows) a recipient-name inquiry against
 * the firm-banking module. Mirrors the per-row iteration in legacy
 * {@code comm_0184_007_r001_act.jsp:401-535} (the {@code k==3..k==8}
 * branches): each row's bank cell is matched against
 * {@code TB_FIRM_BANK_CODE.ENG_ABRVAT_NM}, the count of matches drives
 * NAPAS-vs-CITAD routing, and NAPAS rows additionally fire
 * {@code FIRM_PROC.inquiryAccount(...)} to stamp {@code RCPT_NM}.
 *
 * <p>Overseas contacts never enter this flow &mdash; their template has
 * no Bank Name + Account No. + Currency triplet (they use
 * BENE_NAME / BIC_CODE / BENE_ACCT_NO instead).
 */
public record ContactsExcelResolveRecipientsRequest(
        @NotEmpty
        @Size(max = 500, message = "Cannot resolve recipients for more than 500 rows at once.")
        @Valid
        List<Row> rows) {

    public record Row(
            int rowIndex,
            @Size(max = 100) String bankName,
            @Size(max = 100) String acctNo,
            @Size(max = 3)   String currency) { }
}
