package com.kosign.wecms.domain.banking.synchistory.dto;

/**
 * One row of the Bank Transaction Sync &gt; History popup
 * ({@code GET /api/v1/banking/sync-history}). Mirrors the screenshot's
 * 7-column layout: Date · Bank · Account No. · Account Type · Date
 * Range · Account Nickname · Result.
 *
 * <p>Wire shape: camelCase Java fields auto-serialise as lowercase
 * snake_case via the global Jackson naming strategy.
 *
 * <ul>
 *   <li>{@code regDtm} — {@code yyyyMMddHHmmss}. Frontend renders as
 *       {@code dd-MM-yyyy HH:mm:ss}.</li>
 *   <li>{@code bankNm} — resolved server-side from {@code BANK_CD} via
 *       {@code BankCdResolver}.</li>
 *   <li>{@code acctTypeCd} / {@code acctTypeKey} — same convention as
 *       Account Inquiry. Frontend renders {@code t(acctTypeKey)} and
 *       falls back to {@code acctTypeNm} when the key is null.</li>
 *   <li>{@code startDate} / {@code endDate} — {@code yyyyMMdd}. Frontend
 *       renders as {@code dd-MM-yyyy ~ dd-MM-yyyy} (legacy
 *       "Date Range" column).</li>
 *   <li>{@code rsltCd} — verbatim legacy result code ({@code "00000000"}
 *       = success, anything else = failed). Frontend computes
 *       Success/Failed label via {@code resultKey}.</li>
 *   <li>{@code resultKey} — server-resolved i18n key,
 *       {@code "sync_history.result.success"} or
 *       {@code "sync_history.result.failed"}. Saves the frontend a
 *       branch per row.</li>
 *   <li>{@code errCont} — gateway free-text error, usually Korean for
 *       failed legacy rows. Frontend may surface in a row tooltip.</li>
 * </ul>
 */
public record SyncHistoryRow(
        String refDate,
        String seqNo,
        String regDtm,
        String bankCd,
        String bankNm,
        String acctNo,
        String acctNickNm,
        String acctTypeCd,
        String acctTypeNm,
        String acctTypeKey,
        String ccyCd,
        String startDate,
        String endDate,
        String rsltCd,
        String resultKey,
        String errCont
) { }
