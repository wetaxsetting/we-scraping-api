package com.kosign.wecms.domain.setting.firmbankingsettings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}}.
 * Mirrors the legacy JEX validation at
 * {@code comm_0220_003_u001_act.jsp:32-38} — three editable fields,
 * all required (rules V3 / V4 / V5 in the Phase 1 spec).
 *
 * <p>Wire format: lowercase snake_case via the global Jackson
 * SNAKE_CASE strategy. Java fields stay camelCase.
 *
 * <p>Editable fields only — JEX never permits mutating
 * {@code OUT_BANK_CD} or {@code WDRL_ACT_NO} (those identify the row;
 * V14: {@code FILE_TYPE} is server-derived on Create and never
 * touched on Update).
 *
 * <p>Frontend UX caveat (parity-clarified 2026-05-09): the Edit dialog
 * surfaces only the status toggle as editable. {@code bankPrtnId} and
 * {@code vanOrgCd} are read-only display in the dialog but the
 * backend's PUT contract still requires them — the frontend submits
 * the values it received from the Detail endpoint verbatim. Future
 * UX changes (e.g. allowing partner-id edit) ship on top of this
 * contract without touching it.
 */
public record FirmBankingSettingUpdateRequest(
        @NotBlank
        @Size(max = 20)
        String bankPrtnId,

        @NotBlank
        @Size(max = 20)
        String vanOrgCd,

        /** {@code OrgStat} discriminator: {@code 0} WAITING /
         *  {@code 1} AVAILABLE / {@code 2} SUSPENDED /
         *  {@code 3} TERMINATED. Phase 1 V6 + V22 — any value in the
         *  set transitions to any other; no FSM. */
        @NotBlank
        @Pattern(regexp = "^[0-3]$", message = "org_stat: must be one of 0/1/2/3")
        String orgStat
) { }
