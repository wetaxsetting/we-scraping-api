package com.kosign.wecms.domain.setting.firmbankingsettings.dto;

import java.math.BigDecimal;

/**
 * One row of the firm-banking-settings list. Matches the legacy
 * {@code WSVC.comm_0220_001_r001} {@code REC} sub-rule plus the
 * extra columns the legacy IDO already returned but the WSVC dropped
 * ({@code BAL}, {@code EXPI_DT}). Wire field names auto-derived from
 * camelCase via the global Jackson SNAKE_CASE strategy.
 *
 * <p>{@code wdrlActNo} is masked at the service layer before the row
 * reaches Jackson — the wire value is e.g. {@code "1009****0384"},
 * never the cleartext stored on disk. See feature §9 (OQ-4 plain-text
 * decision; presentation-layer masking).
 */
public record FirmBankingSettingRow(
        String     bizKey,
        String     outBankCd,
        String     bankNm,
        String     ccyCd,
        String     bankPrtnId,
        String     vanOrgCd,
        String     orgStat,
        String     wdrlActNo,
        String     fileType,
        String     batTranBranchDiv,
        BigDecimal batTranBranchLimit,
        String     regDttm,
        String     startDttm,
        String     endDttm,
        String     modDttm,
        String     lastEdtr,
        BigDecimal bal,
        String     expiDt
) { }
