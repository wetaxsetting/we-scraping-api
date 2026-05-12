package com.kosign.wecms.domain.setting.firmbankingsettings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/settings/firm-banking-settings}.
 * Mirrors the legacy JEX validation at
 * {@code comm_0220_002_c001_act.jsp:38-43} — five required fields, all
 * sourced from the user (rules V3 / V4 / V5 / V7 / V20 / V21 in the
 * Phase 1 spec).
 *
 * <p>Wire format: lowercase snake_case via the global Jackson
 * SNAKE_CASE strategy. Java fields stay camelCase.
 *
 * <p><b>OQ-8a Option (c) — bounded-bank set, server-derived
 * {@code file_type}.</b> The client sends only the five fields below;
 * {@code file_type} is server-derived from {@code out_bank_cd} per the
 * §5.4 mapping table (Shinhan→{@code SHB}, Vietcombank→{@code VCB},
 * Woori→{@code WRB}). Anything outside the supported 3-bank set is
 * rejected with {@code WBE0001 Invalid OUT_BANK_CD} at the service
 * layer (rule V8). Adding {@code file_type} to this DTO is forbidden —
 * see workspace {@code CLAUDE.md} §"Refactor identifier naming" and
 * the feature spec §13 OQ-8a.
 *
 * <p>Server-set fields not on the wire: {@code BIZ_KEY} (from
 * {@link com.kosign.wecms.common.tenant.TenantContext}),
 * {@code FILE_TYPE} (V14), {@code BAT_TRAN_BRANCH_DIV} (V15 default
 * {@code 'Y'}), {@code BAT_TRAN_BRANCH_LIMIT} (V16 default
 * {@code 2000}), {@code REG_DTTM} / {@code START_DTTM} /
 * {@code MOD_DTTM} / {@code END_DTTM} (V18), {@code LAST_EDTR} (V19).
 *
 * <p>Note on {@code orgStat}: JEX defaults to {@code '1'} AVAILABLE on
 * Create when absent (rule V17 — the DAO override wins over the DB
 * column DEFAULT {@code '0'}). The current frontend dialog does not
 * expose the field, so the wire ships the explicit default until UX
 * adds a status selector. Validation accepts {@code 0..3} so a future
 * UX change can pass any legal status without a contract bump.
 */
public record FirmBankingSettingCreateRequest(
        /** NAPAS bank code. Must be one of the supported codes
         *  ({@code 970424} Shinhan / {@code 970436} Vietcombank /
         *  {@code 970457} Woori) per OQ-8a Option (c). The
         *  {@code @Pattern} here only enforces the field shape; the
         *  3-bank allow-list lives in the service so the rejection
         *  carries the {@code WBE0001} code with the right argument. */
        @NotBlank
        @Size(max = 15)
        @Pattern(regexp = "^[0-9]{1,15}$",
                message = "out_bank_cd: invalid format")
        String outBankCd,

        /** Tenant company account number (plain text per OQ-4). */
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[A-Za-z0-9\\-]{1,100}$",
                message = "wdrl_act_no: invalid format")
        String wdrlActNo,

        @NotBlank
        @Size(max = 20)
        String bankPrtnId,

        @NotBlank
        @Size(max = 20)
        String vanOrgCd,

        /** {@code OrgStat} discriminator: {@code 0} WAITING /
         *  {@code 1} AVAILABLE / {@code 2} SUSPENDED /
         *  {@code 3} TERMINATED. JEX V17 defaults to {@code '1'} on
         *  Create when the field is absent — the frontend supplies
         *  the default verbatim until the dialog adds a status
         *  selector. Required on the wire so an explicit default
         *  remains visible at the contract boundary. */
        @NotBlank
        @Pattern(regexp = "^[0-3]$", message = "org_stat: must be one of 0/1/2/3")
        String orgStat
) { }
