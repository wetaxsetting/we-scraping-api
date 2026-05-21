package com.kosign.wecms.domain.payment.cardtransactions.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One row in the card-transactions ledger response. Mirrors the legacy
 * {@code REC} sub-rule of {@code WSVC.card_0001_001_r001.xml} with
 * three refactor adjustments:
 *
 * <ul>
 *   <li>{@code stts_key} (sibling to the raw {@code stts} code) carries
 *       the i18n key the frontend resolves via {@code t(...)}. Replaces
 *       the legacy {@code wb_get_prch_stts_nm} server-side label
 *       rendering — see backend doc §3.4.</li>
 *   <li>{@code linked_docs[]} / {@code approvers[]} are Phase B
 *       placeholders — service emits empty lists until the linked-doc
 *       and approver-chain entities land. See feature doc §11 OQ-1.</li>
 *   <li>{@code bank_nm} is resolved server-side via
 *       {@code BankCdResolver} rather than the legacy {@code
 *       wb_get_bank_nm} DB function (refactor stance, backend doc §3.4).</li>
 * </ul>
 *
 * <p>Wire serialisation is lowercase snake_case via the global Jackson
 * naming strategy ({@code config/JacksonConfig}); Java field names stay
 * camelCase per the wire-naming standard in
 * {@code wecms-api-main/CLAUDE.md} §"API Naming &amp; Serialization Standards".
 */
public record CardTransactionRow(
        String           cardSeqNo,
        String           bankCd,
        String           bankNm,
        String           cardNo,
        String           cardNm,
        String           cardNickNm,
        String           cardHldrNm,
        String           merchNm,
        String           ccyCd,
        BigDecimal       trscAmt,
        String           trscDt,
        String           trscTm,
        String           cardTrnsUsageCode,
        String           chartAcctSeqNo,
        String           chartAcctCd,
        String           cont,
        String           apprSeqNo,
        String           stts,
        String           sttsKey,
        List<LinkedDocRef> linkedDocs,
        List<ApproverRef>  approvers,
        String           regId,
        String           regDtm,
        String           modId,
        String           modDtm) {
}
