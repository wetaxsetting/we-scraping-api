package com.kosign.wecms.domain.setting.firmbankingsettings.dto;

import java.math.BigDecimal;

/**
 * Detail (read-by-PK) shape. Mirrors the legacy
 * {@code WSVC.comm_0220_003_r001} top-level fields (the audit-log
 * {@code REC} is now its own resource at
 * {@code GET …/audit}). No join columns ({@code BANK_NM} / {@code CCY_CD}
 * / {@code BAL} / {@code EXPI_DT}) — those belong to the list view
 * shape.
 *
 * <p>{@link #wdrlActNo} is returned in <strong>cleartext</strong>
 * here. The detail endpoint is consumed by the edit dialog where the
 * user actively edits the field; masking it would break the
 * round-trip. The list-view shape ({@link FirmBankingSettingRow})
 * masks it instead. This split mirrors feature §9 (OQ-4 presentation-
 * layer masking).
 */
public record FirmBankingSettingDetail(
        String     bizKey,
        String     outBankCd,
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
        String     lastEdtr
) { }
