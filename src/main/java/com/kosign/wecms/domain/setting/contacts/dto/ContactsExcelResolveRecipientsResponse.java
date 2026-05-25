package com.kosign.wecms.domain.setting.contacts.dto;

import java.util.List;

/**
 * Response body for {@code POST /api/v1/settings/contacts/excel/resolve-recipients}.
 *
 * <p>One {@link Result} per request row, in the same index order.
 * Routing depends on {@link Result#bankFound} + {@link Result#antDivCd}:
 *
 * <ul>
 *   <li>{@code bankFound = false} &mdash; the row's bank cell didn't
 *       match any NAPAS / CITAD entry. Frontend flags the cell red
 *       (legacy {@code comm_0184_007_r001_act.jsp:483 WBE1013}).</li>
 *   <li>{@code bankFound = true}, {@code antDivCd = "NAPAS"} &mdash;
 *       {@code napasBankCd} populated; if {@code currency = "VND"} the
 *       firm-banking inquiry ran and {@code fincMdaOwnerNm} carries the
 *       resolved holder name (may be empty when the inquiry returned no
 *       value, mirroring legacy {@code checkRecptName}'s no-op branch).</li>
 *   <li>{@code bankFound = true}, {@code antDivCd = "CITAD"} &mdash;
 *       {@code citadBankCd} populated; no recipient inquiry (CITAD has
 *       no real-time account-name source). User fills Recipient manually
 *       via the Edit dialog after import.</li>
 * </ul>
 */
public record ContactsExcelResolveRecipientsResponse(
        List<Result> results) {

    public record Result(
            int rowIndex,
            boolean bankFound,
            /** {@code "NAPAS"} / {@code "CITAD"} / null when not found. */
            String antDivCd,
            /** Populated when {@code antDivCd = "NAPAS"}. */
            String napasBankCd,
            /** Populated when {@code antDivCd = "CITAD"}. */
            String citadBankCd,
            /** Populated only for NAPAS-VND rows where the firm-banking
             *  inquiry resolved a holder name. Empty / null otherwise. */
            String fincMdaOwnerNm) { }
}
